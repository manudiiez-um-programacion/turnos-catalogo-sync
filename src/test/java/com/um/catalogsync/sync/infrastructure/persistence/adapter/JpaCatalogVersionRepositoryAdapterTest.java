package com.um.catalogsync.sync.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.application.exception.ConcurrentVersionAdvanceException;
import com.um.catalogsync.sync.application.exception.VersionRegressionException;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class JpaCatalogVersionRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant CREADO = Instant.parse("2026-07-01T12:00:00Z");

    @Autowired
    private CatalogVersionRepository catalogVersionRepository;

    /**
     * Se usa para el escenario que demuestra la regla central del proyecto:
     * aplicar datos y avanzar la version en una misma transaccion que
     * despues falla. Es el puerto del slice de categorias, no su repositorio
     * JPA, igual que lo usara la sincronizacion real.
     */
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    private int filasDeVersion() {
        return jdbc.queryForObject("SELECT count(*) FROM catalog_version", Integer.class);
    }

    private static ProfessionalCategory categoria() {
        return ProfessionalCategory.builder()
                .id(1L)
                .name("Clinica medica")
                .enabled(true)
                .createdAt(CREADO)
                .updatedAt(CREADO)
                .build();
    }

    // --- Lectura ---------------------------------------------------------

    @Test
    @DisplayName("Una base recien inicializada no tiene version: devuelve vacio, no la version 0")
    void baseReciemInicializadaDevuelveVacio() {
        assertThat(catalogVersionRepository.findCurrent()).isEmpty();
        assertThat(filasDeVersion()).isZero();
    }

    // --- Avance ----------------------------------------------------------

    @Test
    @DisplayName("El primer avance asienta la version y queda una sola fila")
    void elPrimerAvanceAsientaLaVersion() {
        Instant antes = Instant.now();
        CatalogVersion asentada =
                catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);
        Instant despues = Instant.now();

        assertThat(asentada.getVersion()).isEqualTo(7L);
        assertThat(asentada.getSource()).isEqualTo(CatalogVersionSource.SNAPSHOT);
        // appliedAt es nuestro reloj, asi que se verifica por rango.
        assertThat(asentada.getAppliedAt()).isBetween(
                antes.minusSeconds(1), despues.plusSeconds(1));
        assertThat(filasDeVersion()).isOne();
    }

    @Test
    @DisplayName("Lo asentado es exactamente lo que se lee despues, appliedAt incluido")
    void loAsentadoEsLoQueSeLee() {
        CatalogVersion asentada =
                catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);

        // Si el adapter no truncara a microsegundos, este assert fallaria:
        // TIMESTAMPTZ no guarda los nanosegundos que trae Instant.now().
        assertThat(catalogVersionRepository.findCurrent())
                .get()
                .usingRecursiveComparison()
                .isEqualTo(asentada);
    }

    @Test
    @DisplayName("Avanzar hacia adelante reemplaza la version vigente, sin agregar filas")
    void avanzarHaciaAdelanteReemplaza() {
        catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);
        catalogVersionRepository.advanceTo(8L, CatalogVersionSource.INCREMENTAL);

        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion, CatalogVersion::getSource)
                .containsExactly(8L, CatalogVersionSource.INCREMENTAL);
        assertThat(filasDeVersion()).isOne();
    }

    @Test
    @DisplayName("Asentar la misma version se permite: reprocesar no altera el resultado")
    void laMismaVersionSePermite() {
        catalogVersionRepository.advanceTo(7L, CatalogVersionSource.INCREMENTAL);

        catalogVersionRepository.advanceTo(7L, CatalogVersionSource.INCREMENTAL);

        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion)
                .isEqualTo(7L);
        assertThat(filasDeVersion()).isOne();
    }

    @Test
    @DisplayName("Retroceder por incremental se rechaza y la version vigente no se toca")
    void retrocederPorIncrementalSeRechaza() {
        catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);

        assertThatThrownBy(() ->
                catalogVersionRepository.advanceTo(6L, CatalogVersionSource.INCREMENTAL))
                .isInstanceOf(VersionRegressionException.class)
                .hasMessageContaining("7")
                .hasMessageContaining("6");

        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion)
                .isEqualTo(7L);
    }

    @Test
    @DisplayName("Retroceder por snapshot se permite: es la reconstruccion por discontinuidad")
    void retrocederPorSnapshotSePermite() {
        // El escenario de §18.2: la version local quedo en 7 y el snapshot
        // que reconstruye la copia trae snapshotVersion 3. La version tiene
        // que describir los datos que hay, no el maximo que supimos.
        catalogVersionRepository.advanceTo(7L, CatalogVersionSource.INCREMENTAL);

        catalogVersionRepository.advanceTo(3L, CatalogVersionSource.SNAPSHOT);

        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion, CatalogVersion::getSource)
                .containsExactly(3L, CatalogVersionSource.SNAPSHOT);
        assertThat(filasDeVersion()).isOne();
    }

    // --- La regla central: la version avanza solo si la unidad de trabajo
    // --- termino completa (§6.1, §6.2) -----------------------------------

    @Test
    @DisplayName("Si la transaccion que aplica datos falla, la version NO avanza")
    void laVersionNoAvanzaSiLaTransaccionFalla() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            // Una unidad de trabajo como la de #13: aplicar datos y, al
            // final, asentar la version que corresponde a esos datos.
            categoryRepository.upsert(categoria());
            catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);
            // Y algo falla despues. Puede ser cualquier cosa: una categoria
            // invalida, la conexion a la catedra, un bug.
            throw new IllegalStateException("fallo al terminar de aplicar el snapshot");
        })).isInstanceOf(IllegalStateException.class);

        // Las dos escrituras se fueron juntas. Esto es lo que garantiza que
        // la version local nunca describa datos que no estan.
        assertThat(catalogVersionRepository.findCurrent()).isEmpty();
        assertThat(filasDeVersion()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM professional_category", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Si la transaccion termina bien, la version avanza junto con los datos")
    void laVersionAvanzaSiLaTransaccionTermina() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        tx.executeWithoutResult(status -> {
            categoryRepository.upsert(categoria());
            catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);
        });

        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion)
                .isEqualTo(7L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM professional_category", Integer.class)).isOne();
    }

    @Test
    @DisplayName("Un avance rechazado deshace tambien los datos de su transaccion")
    void unAvanceRechazadoDeshaceLosDatos() {
        catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            categoryRepository.upsert(categoria());
            // Retroceder por incremental: el puerto lo rechaza, y como la
            // excepcion sale de la transaccion, los datos tampoco quedan.
            catalogVersionRepository.advanceTo(6L, CatalogVersionSource.INCREMENTAL);
        })).isInstanceOf(VersionRegressionException.class);

        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion)
                .isEqualTo(7L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM professional_category", Integer.class)).isZero();
    }

    // --- Concurrencia ----------------------------------------------------

    @Test
    @DisplayName("La base hace imposible una segunda version, no solo improbable")
    void unaSegundaFilaEsImposible() {
        catalogVersionRepository.advanceTo(7L, CatalogVersionSource.SNAPSHOT);

        // Esta prueba no depende de ningun hilo: verifica que la invariante
        // la sostenga el esquema. Aunque el codigo tuviera un bug y quisiera
        // anotar una segunda version, la base no lo permite.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO catalog_version (id, applied_version, applied_at, source) "
                        + "VALUES (2, 8, now(), 'SNAPSHOT')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(filasDeVersion()).isOne();
    }

    @Test
    @DisplayName("Dos avances concurrentes sobre una version existente no dejan dos filas")
    void avancesConcurrentesNoDejanDosFilas() throws Exception {
        catalogVersionRepository.advanceTo(1L, CatalogVersionSource.SNAPSHOT);

        List<Object> resultados = enParalelo(
                () -> catalogVersionRepository.advanceTo(10L, CatalogVersionSource.INCREMENTAL),
                () -> catalogVersionRepository.advanceTo(20L, CatalogVersionSource.INCREMENTAL));

        // Lo que el issue pide: una sola fila vigente, pase lo que pase.
        assertThat(filasDeVersion()).isOne();

        // El lock de lockCurrent() serializa los dos avances, asi que el
        // resultado no depende del orden en que arrancaron: si el de 20
        // gana, el de 10 queda rechazado por retroceder; si gana el de 10,
        // el de 20 lo reemplaza. En los dos casos termina en 20.
        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion)
                .isEqualTo(20L);
        assertThat(resultados).anySatisfy(r -> assertThat(r).isInstanceOf(CatalogVersion.class));
        assertThat(resultados).allSatisfy(r -> assertThat(r)
                .satisfiesAnyOf(
                        ok -> assertThat(ok).isInstanceOf(CatalogVersion.class),
                        error -> assertThat(error).isInstanceOf(VersionRegressionException.class)));
    }

    @Test
    @DisplayName("Primeros avances concurrentes sobre base vacia: gana uno y queda una sola fila")
    void primerosAvancesConcurrentesNoDejanDosFilas() throws Exception {
        List<Object> resultados = enParalelo(
                () -> catalogVersionRepository.advanceTo(10L, CatalogVersionSource.SNAPSHOT),
                () -> catalogVersionRepository.advanceTo(20L, CatalogVersionSource.SNAPSHOT));

        // Sobre una fila que no existe no hay nada que bloquear, asi que la
        // invariante la sostiene la clave primaria: los dos INSERT llegan y
        // pasa uno. La que pierde recibe la excepcion que lo dice.
        assertThat(filasDeVersion()).isOne();
        assertThat(resultados).anySatisfy(r -> assertThat(r).isInstanceOf(CatalogVersion.class));
        assertThat(resultados).allSatisfy(r -> assertThat(r)
                .satisfiesAnyOf(
                        ok -> assertThat(ok).isInstanceOf(CatalogVersion.class),
                        perdio -> assertThat(perdio)
                                .isInstanceOf(ConcurrentVersionAdvanceException.class),
                        // Si una alcanzo a commitear antes de que la otra
                        // leyera, la segunda ve la fila y decide con la regla
                        // normal en lugar de chocar con la clave primaria.
                        tarde -> assertThat(tarde).isInstanceOf(VersionRegressionException.class)));

        assertThat(catalogVersionRepository.findCurrent()).get()
                .extracting(CatalogVersion::getVersion)
                .isIn(10L, 20L);
    }

    /**
     * Corre las tareas a la vez, cada una en su propio hilo y su propia
     * transaccion, y devuelve lo que dio cada una: el valor si salio bien o
     * la excepcion si fallo.
     *
     * <p>La barrera hace que arranquen juntas de verdad. Sin ella, la
     * primera podria terminar antes de que la segunda empiece y la prueba
     * no probaria nada sobre concurrencia.
     */
    private static List<Object> enParalelo(Callable<?>... tareas) throws Exception {
        CyclicBarrier arranque = new CyclicBarrier(tareas.length);
        ExecutorService pool = Executors.newFixedThreadPool(tareas.length);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> tarea : tareas) {
                futures.add(pool.submit(() -> {
                    arranque.await(10, TimeUnit.SECONDS);
                    try {
                        return tarea.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> future : futures) {
                resultados.add(future.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}

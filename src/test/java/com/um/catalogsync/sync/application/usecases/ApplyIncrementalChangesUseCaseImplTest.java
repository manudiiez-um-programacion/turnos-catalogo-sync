package com.um.catalogsync.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.CatalogSnapshotFixtures;
import com.um.catalogsync.sync.domain.model.CatalogChanges;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionDelta;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.IncrementalSyncOutcome;
import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogVersionUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogChangesPort;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.PublishedCatalogPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El recorrido de versiones, con dobles de los cuatro puertos.
 *
 * <p>Lo que se prueba acá es la decision del bucle: desde donde arranca,
 * hasta donde llega, en que orden, cuando se detiene y que informa. Nada de
 * eso necesita base ni Redis, y tenerlos haria que una falla de entorno se
 * confundiera con una falla de la decision. Que el estado final sea el
 * correcto lo prueba {@code IncrementalSyncIntegrationTest} contra PostgreSQL
 * y Redis reales.
 */
class ApplyIncrementalChangesUseCaseImplTest {

    private final VersionLocalEnMemoria versionLocal = new VersionLocalEnMemoria();
    private final CambiosEnMemoria cambios = new CambiosEnMemoria();
    private final CatalogoPublicadoEnMemoria publicado = new CatalogoPublicadoEnMemoria();
    private final AplicacionesRegistradas aplicaciones = new AplicacionesRegistradas();

    private ApplyIncrementalChangesUseCaseImpl useCase() {
        return new ApplyIncrementalChangesUseCaseImpl(
                versionLocal, cambios, publicado, aplicaciones);
    }

    // --- El recorrido en orden --------------------------------------------

    @Test
    @DisplayName("Recorre de versionLocal + 1 hasta el destino, sin saltear ninguna")
    void recorreEnOrden() {
        versionLocal.version = 5L;
        cambios.vacia(6L);
        cambios.vacia(7L);
        cambios.vacia(8L);

        IncrementalSyncResult resultado = useCase().applyUpTo(8L);

        // El orden no es una preferencia: cada version describe una
        // diferencia contra la anterior, asi que aplicarlas desordenadas
        // dejaria la copia local afirmando un estado que no tiene.
        assertThat(aplicaciones.versiones).containsExactly(6L, 7L, 8L);
        assertThat(resultado.outcome()).isEqualTo(IncrementalSyncOutcome.CAUGHT_UP);
        assertThat(resultado.fromVersion()).isEqualTo(5L);
        assertThat(resultado.toVersion()).isEqualTo(8L);
        assertThat(resultado.versionsApplied()).isEqualTo(3);
        assertThat(resultado.reachedTarget()).isTrue();
        assertThat(resultado.needsFullSync()).isFalse();
    }

    @Test
    @DisplayName("Cada version se aplica con su propio delta, no todas juntas")
    void unDeltaPorVersion() {
        versionLocal.version = 5L;
        cambios.con(6L, List.of(1L), List.of(), List.of());
        cambios.con(7L, List.of(), List.of(15L), List.of());
        publicado.categoria(1L);
        publicado.profesional(15L, 1L);

        useCase().applyUpTo(7L);

        // Una transaccion por version es lo que hace que una falla en la
        // septima no tire las anteriores. Si los deltas se acumularan, serian
        // una sola unidad de trabajo gigante.
        assertThat(aplicaciones.deltas).hasSize(2);
        assertThat(aplicaciones.deltas.get(0).getCategories()).hasSize(1);
        assertThat(aplicaciones.deltas.get(0).getProfessionals()).isEmpty();
        assertThat(aplicaciones.deltas.get(1).getProfessionals()).hasSize(1);
    }

    @Test
    @DisplayName("Una version sin cambios se aplica igual y hace avanzar la version")
    void laVersionVaciaSeAplica() {
        versionLocal.version = 5L;
        cambios.vacia(6L);

        IncrementalSyncResult resultado = useCase().applyUpTo(6L);

        assertThat(aplicaciones.versiones).containsExactly(6L);
        assertThat(aplicaciones.deltas.getFirst().isEmpty()).isTrue();
        assertThat(resultado.toVersion()).isEqualTo(6L);
    }

    // --- Cuando no hay nada que hacer -------------------------------------

    @Test
    @DisplayName("Si la version local ya cubre el destino, no se aplica nada")
    void yaEstaAlDia() {
        versionLocal.version = 7L;

        IncrementalSyncResult resultado = useCase().applyUpTo(7L);

        assertThat(resultado.outcome()).isEqualTo(IncrementalSyncOutcome.UP_TO_DATE);
        assertThat(aplicaciones.versiones).isEmpty();
        assertThat(resultado.reachedTarget()).isTrue();
    }

    @Test
    @DisplayName("Nunca retrocede: un destino anterior a la version local no hace nada")
    void noRetrocede() {
        // Puede pasar de verdad: un snapshot recien aplicado dejo la version
        // local en 9 mientras una notificacion de la 7 esperaba su turno.
        // Aplicarla haria retroceder la copia local a un estado que ya no
        // tiene.
        versionLocal.version = 9L;

        IncrementalSyncResult resultado = useCase().applyUpTo(7L);

        assertThat(resultado.outcome()).isEqualTo(IncrementalSyncOutcome.UP_TO_DATE);
        assertThat(resultado.toVersion()).isEqualTo(9L);
        assertThat(aplicaciones.versiones).isEmpty();
    }

    @Test
    @DisplayName("Sin version local, el camino incremental no aplica")
    void sinVersionLocal() {
        // Base recien inicializada: los cambios describen diferencias contra
        // un estado que no existe. Se informa en lugar de decidir, porque
        // elegir el snapshot es de #26.
        versionLocal.version = null;

        IncrementalSyncResult resultado = useCase().applyUpTo(7L);

        assertThat(resultado.outcome()).isEqualTo(IncrementalSyncOutcome.NO_LOCAL_VERSION);
        assertThat(resultado.fromVersion()).isNull();
        assertThat(resultado.needsFullSync()).isTrue();
        assertThat(aplicaciones.versiones).isEmpty();
    }

    // --- Donde se detiene -------------------------------------------------

    @Test
    @DisplayName("Si falta la clave de cambios de una version, se detiene ahi")
    void seDetieneEnLaVersionFaltante() {
        versionLocal.version = 5L;
        cambios.vacia(6L);
        // La 7 no esta: se la saco de la ventana retenida.
        cambios.vacia(8L);

        IncrementalSyncResult resultado = useCase().applyUpTo(8L);

        // No se saltea a la 8. Saltear dejaria un hueco que nada posterior
        // corrige, y la copia local quedaria afirmando la version 8 sin tener
        // los cambios de la 7 (§18.2).
        assertThat(aplicaciones.versiones).containsExactly(6L);
        assertThat(resultado.outcome()).isEqualTo(IncrementalSyncOutcome.MISSING_VERSION);
        assertThat(resultado.toVersion()).isEqualTo(6L);
        assertThat(resultado.versionsApplied()).isEqualTo(1);
        assertThat(resultado.needsFullSync()).isTrue();
    }

    @Test
    @DisplayName("Si una version falla, la excepcion sale y las anteriores quedan")
    void siUnaVersionFallaSeDetieneAhi() {
        versionLocal.version = 5L;
        cambios.vacia(6L);
        cambios.vacia(7L);
        cambios.vacia(8L);
        aplicaciones.fallaEnLaVersion = 7L;

        assertThatThrownBy(() -> useCase().applyUpTo(8L))
                .isInstanceOf(IllegalStateException.class);

        // La 6 quedo aplicada y la 8 no se intento. La version local queda en
        // la ultima completa, que es el estado correcto desde el cual
        // reintentar.
        assertThat(aplicaciones.versiones).containsExactly(6L);
    }

    // --- Los ids que ya no estan en los hashes ----------------------------

    @Test
    @DisplayName("Un id que no esta en el hash se aplica como baja")
    void elIdAusenteEsUnaBaja() {
        // El hash guarda el estado actual, no el historico: al ponerse al dia
        // varias versiones de corrido, una entidad que la version 6 modifico
        // puede haber sido eliminada despues. El estado vigente de ese id es
        // "no existe", y la copia local tiene que reflejarlo.
        versionLocal.version = 5L;
        cambios.con(6L, List.of(1L, 2L), List.of(15L), List.of(21L));
        publicado.categoria(1L);
        // La categoria 2, el profesional 15 y el horario 21 no estan.

        useCase().applyUpTo(6L);

        CatalogVersionDelta delta = aplicaciones.deltas.getFirst();
        assertThat(delta.getCategories()).hasSize(1);
        assertThat(delta.getRemovedCategoryIds()).containsExactly(2L);
        assertThat(delta.getRemovedProfessionalIds()).containsExactly(15L);
        assertThat(delta.getRemovedScheduleIds()).containsExactly(21L);
        assertThat(delta.removals()).isEqualTo(3);
    }

    @Test
    @DisplayName("Una entidad deshabilitada llega como entidad, no como baja")
    void laBajaLogicaNoEsUnaBaja() {
        // §14.4: las deshabilitadas permanecen en los hashes. Confundirlas
        // con una baja borraria de la copia local justo lo que la busqueda
        // por enabled tiene que poder encontrar.
        versionLocal.version = 5L;
        cambios.con(6L, List.of(), List.of(15L), List.of());
        publicado.profesionales.put(15L,
                CatalogSnapshotFixtures.profesional(15L, 1L, "Ana", "Gomez", false));

        useCase().applyUpTo(6L);

        CatalogVersionDelta delta = aplicaciones.deltas.getFirst();
        assertThat(delta.getProfessionals()).singleElement()
                .satisfies(p -> assertThat(p.isEnabled()).isFalse());
        assertThat(delta.getRemovedProfessionalIds()).isEmpty();
    }

    // --- Dobles -----------------------------------------------------------

    private static final class VersionLocalEnMemoria implements CatalogVersionRepository {

        private Long version;

        @Override
        public Optional<CatalogVersion> findCurrent() {
            return Optional.ofNullable(version).map(v -> CatalogVersion.builder()
                    .version(v)
                    .source(CatalogVersionSource.INCREMENTAL)
                    .appliedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .build());
        }

        @Override
        public CatalogVersion advanceTo(long version, CatalogVersionSource source) {
            throw new AssertionError("El recorrido no avanza la version: eso es de"
                    + " ApplyCatalogVersionUseCase, dentro de su transaccion");
        }
    }

    private static final class CambiosEnMemoria implements CatalogChangesPort {

        private final Map<Long, CatalogChanges> porVersion = new HashMap<>();

        private void vacia(long version) {
            con(version, List.of(), List.of(), List.of());
        }

        private void con(long version, List<Long> cats, List<Long> profs, List<Long> hors) {
            porVersion.put(version, CatalogChanges.builder()
                    .version(version)
                    .categoryIds(cats)
                    .professionalIds(profs)
                    .scheduleIds(hors)
                    .build());
        }

        @Override
        public Optional<CatalogChanges> changesFor(long version) {
            return Optional.ofNullable(porVersion.get(version));
        }
    }

    private static final class CatalogoPublicadoEnMemoria implements PublishedCatalogPort {

        private final Map<Long, ProfessionalCategory> categorias = new HashMap<>();
        private final Map<Long, Professional> profesionales = new HashMap<>();
        private final Map<Long, WeeklySchedule> horarios = new HashMap<>();

        private void categoria(long id) {
            categorias.put(id, CatalogSnapshotFixtures.categoria(id, "Categoria " + id, true));
        }

        private void profesional(long id, long categoriaId) {
            profesionales.put(id,
                    CatalogSnapshotFixtures.profesional(id, categoriaId, "Ana", "Gomez", true));
        }

        @Override
        public Optional<Long> currentVersion() {
            return Optional.empty();
        }

        @Override
        public Optional<Long> oldestAvailableVersion() {
            return Optional.empty();
        }

        @Override
        public Optional<PublishedCatalogMetadata> metadata() {
            return Optional.empty();
        }

        @Override
        public Optional<ProfessionalCategory> findCategory(long id) {
            return Optional.ofNullable(categorias.get(id));
        }

        @Override
        public Optional<Professional> findProfessional(long id) {
            return Optional.ofNullable(profesionales.get(id));
        }

        @Override
        public Optional<WeeklySchedule> findSchedule(long id) {
            return Optional.ofNullable(horarios.get(id));
        }
    }

    private static final class AplicacionesRegistradas implements ApplyCatalogVersionUseCase {

        private final List<Long> versiones = new ArrayList<>();
        private final List<CatalogVersionDelta> deltas = new ArrayList<>();
        private Long fallaEnLaVersion;

        @Override
        public CatalogVersion apply(CatalogVersionDelta delta) {
            if (Long.valueOf(delta.getVersion()).equals(fallaEnLaVersion)) {
                throw new IllegalStateException(
                        "la base no responde aplicando la version " + delta.getVersion());
            }
            versiones.add(delta.getVersion());
            deltas.add(delta);
            return CatalogVersion.builder()
                    .version(delta.getVersion())
                    .source(CatalogVersionSource.INCREMENTAL)
                    .appliedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .build();
        }
    }
}

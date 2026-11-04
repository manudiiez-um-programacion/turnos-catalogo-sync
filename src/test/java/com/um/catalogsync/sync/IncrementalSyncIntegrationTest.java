package com.um.catalogsync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.IncrementalSyncOutcome;
import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisForTests;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * La sincronizacion incremental de punta a punta: Redis real, PostgreSQL
 * real, adaptadores de produccion.
 *
 * <p><b>Que agrega sobre las pruebas con dobles.</b> Aquellas verifican las
 * decisiones —el orden del recorrido, el orden dentro de una version, donde
 * se detiene—. Esta verifica el <b>resultado</b>: que despues de aplicar dos
 * versiones consecutivas la copia local tenga exactamente lo que la catedra
 * publico y la version local sea la ultima aplicada. Es el unico lugar donde
 * participan las FK de verdad, la lectura real de los hashes y la
 * transaccion real.
 *
 * <p>Lo unico que se reemplaza es la configuracion de integracion, que
 * apunta al Redis del contenedor en lugar del de la catedra. Es exactamente
 * de donde la toma el servicio en produccion (§14.1), asi que el adaptador
 * no se entera de la diferencia.
 *
 * <p>El escenario es el de §16: la copia local esta en la version 7, la
 * catedra publico hasta la 9, y se recorre la 8 y la 9 leyendo
 * {@code changes:{version}} y el estado vigente de cada id.
 */
class IncrementalSyncIntegrationTest extends AbstractPostgresIntegrationTest {

    @MockitoBean
    private GetIntegrationConfigUseCase integracion;

    @Autowired
    private ApplyIncrementalChangesUseCase incremental;

    @Autowired
    private ApplyCatalogSnapshotUseCase applySnapshot;

    @Autowired
    private CatalogVersionRepository versiones;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private WeeklyScheduleRepository horarios;

    @BeforeEach
    void prepararElOrigenYLaCopiaLocal() {
        CatedraRedisForTests.limpiar();
        when(integracion.get()).thenReturn(CatedraRedisForTests.apuntandoA(
                CatedraRedisForTests.puerto(), CatedraRedisForTests.CLAVE));

        // La copia local arranca en la version 7, con el catalogo del
        // ejemplo de §7: una categoria, un profesional y un horario.
        applySnapshot.apply(CatalogSnapshotFixtures.version(7L));
    }

    @Test
    @DisplayName("Dos versiones consecutivas dejan el estado final y la version correctos")
    void dosVersionesConsecutivas() {
        // Version 8: cambia el apellido del profesional 15 y aparece la
        // categoria 2.
        hashCategoria(2L, "Pediatria", true);
        hashProfesional(15L, 1L, "Ana", "Gonzalez", true);
        claveDeCambios(8L, List.of(2L), List.of(15L), List.of());

        // Version 9: aparece un horario nuevo del profesional 15 y la
        // categoria 2 queda deshabilitada.
        hashCategoria(2L, "Pediatria", false);
        hashHorario(22L, 15L, "TUESDAY", true);
        claveDeCambios(9L, List.of(2L), List.of(), List.of(22L));

        IncrementalSyncResult resultado = incremental.applyUpTo(9L);

        assertThat(resultado.outcome()).isEqualTo(IncrementalSyncOutcome.CAUGHT_UP);
        assertThat(resultado.versionsApplied()).isEqualTo(2);

        // El estado final: lo que la catedra tiene publicado, no una suma de
        // deltas. El apellido es el de la version 8 y la categoria 2 quedo
        // deshabilitada por la 9.
        assertThat(profesionales.findById(15L)).get()
                .satisfies(p -> assertThat(p.getLastName()).isEqualTo("Gonzalez"));
        assertThat(categorias.findById(2L)).get()
                .satisfies(c -> assertThat(c.isEnabled()).isFalse());
        assertThat(horarios.findByProfessionalId(15L)).hasSize(2);

        // Y la version local quedo en la ultima aplicada completa.
        assertThat(versiones.findCurrent()).get().satisfies(version -> {
            assertThat(version.getVersion()).isEqualTo(9L);
            assertThat(version.getSource()).isEqualTo(CatalogVersionSource.INCREMENTAL);
        });
    }

    @Test
    @DisplayName("Reprocesar una version ya aplicada no cambia el resultado final")
    void reprocesarNoCambiaNada() {
        hashProfesional(15L, 1L, "Ana", "Gonzalez", true);
        claveDeCambios(8L, List.of(), List.of(15L), List.of());

        incremental.applyUpTo(8L);
        // Segunda pasada con el mismo destino: no hay nada que aplicar, y lo
        // que se aplicaria seria el mismo estado vigente. Es la propiedad que
        // vuelve inofensiva una reentrega de Kafka (§18.1).
        IncrementalSyncResult segunda = incremental.applyUpTo(8L);

        assertThat(segunda.outcome()).isEqualTo(IncrementalSyncOutcome.UP_TO_DATE);
        assertThat(versiones.findCurrent()).get()
                .satisfies(version -> assertThat(version.getVersion()).isEqualTo(8L));
        assertThat(profesionales.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un id que ya no esta en el hash se borra de la copia local")
    void elIdAusenteSeBorra() {
        // El horario 21 esta en la copia local desde el snapshot y la version
        // 8 lo lista como cambiado, pero el hash no lo tiene: su estado
        // vigente es "no existe".
        claveDeCambios(8L, List.of(), List.of(), List.of(21L));

        incremental.applyUpTo(8L);

        assertThat(horarios.findById(21L)).isEmpty();
        assertThat(versiones.findCurrent()).get()
                .satisfies(version -> assertThat(version.getVersion()).isEqualTo(8L));
    }

    @Test
    @DisplayName("Si falta una version del rango, la copia local queda en la anterior")
    void seDetieneEnLaVersionFaltante() {
        hashProfesional(15L, 1L, "Ana", "Gonzalez", true);
        claveDeCambios(8L, List.of(), List.of(15L), List.of());
        // La 9 no esta en Redis.

        IncrementalSyncResult resultado = incremental.applyUpTo(10L);

        assertThat(resultado.outcome()).isEqualTo(IncrementalSyncOutcome.MISSING_VERSION);
        assertThat(resultado.needsFullSync()).isTrue();

        // La 8 quedo aplicada entera y la version local es 8, no 10: la copia
        // local nunca afirma una version que no tiene.
        assertThat(profesionales.findById(15L)).get()
                .satisfies(p -> assertThat(p.getLastName()).isEqualTo("Gonzalez"));
        assertThat(versiones.findCurrent()).get()
                .satisfies(version -> assertThat(version.getVersion()).isEqualTo(8L));
    }

    @Test
    @DisplayName("Una version que crea una categoria y un profesional suyo no viola la FK")
    void laCategoriaYSuProfesionalEnLaMismaVersion() {
        // Es el caso que #7 resolvio con el orden padre → hijo. Contra la
        // base real, cualquier otro orden falla por la FK: es el unico lugar
        // donde eso se puede demostrar.
        hashCategoria(3L, "Traumatologia", true);
        hashProfesional(40L, 3L, "Luis", "Diaz", true);
        claveDeCambios(8L, List.of(3L), List.of(40L), List.of());

        incremental.applyUpTo(8L);

        assertThat(profesionales.findById(40L)).get()
                .satisfies(p -> assertThat(p.getCategoryId()).isEqualTo(3L));
    }

    @Test
    @DisplayName("Una version vacia hace avanzar la version local sin tocar los datos")
    void laVersionVaciaAvanza() {
        claveDeCambios(8L, List.of(), List.of(), List.of());

        incremental.applyUpTo(8L);

        assertThat(versiones.findCurrent()).get()
                .satisfies(version -> assertThat(version.getVersion()).isEqualTo(8L));
        assertThat(categorias.findAll()).hasSize(1);
        assertThat(profesionales.findAll()).hasSize(1);
        assertThat(horarios.findAll()).hasSize(1);
    }

    // --- Lo que la catedra publica en Redis -------------------------------

    private void claveDeCambios(long version, List<Long> cats, List<Long> profs, List<Long> hors) {
        CatedraRedisForTests.cli("SET", PublishedCatalogKeys.changes(version), """
                {"version": %d, "changes": {"professionalCategories": %s,
                 "professionals": %s, "weeklySchedules": %s}}
                """.formatted(version, cats, profs, hors));
    }

    private void hashCategoria(long id, String nombre, boolean habilitada) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.PROFESSIONAL_CATEGORIES,
                String.valueOf(id), """
                        {"id": %d, "name": "%s", "description": "Descripcion de %s",
                         "enabled": %b, "createdAt": "2026-07-01T12:00:00Z",
                         "updatedAt": "2026-09-10T13:40:00Z"}
                        """.formatted(id, nombre, nombre, habilitada));
    }

    private void hashProfesional(
            long id, long categoriaId, String nombre, String apellido, boolean habilitado) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.PROFESSIONALS,
                String.valueOf(id), """
                        {"id": %d, "categoryId": %d, "firstName": "%s", "lastName": "%s",
                         "enabled": %b, "createdAt": "2026-07-01T12:00:00Z",
                         "updatedAt": "2026-09-10T13:40:00Z"}
                        """.formatted(id, categoriaId, nombre, apellido, habilitado));
    }

    private void hashHorario(long id, long profesionalId, String dia, boolean habilitado) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.WEEKLY_SCHEDULES,
                String.valueOf(id), """
                        {"id": %d, "professionalId": %d, "dayOfWeek": "%s",
                         "startTime": "14:00:00", "endTime": "18:00:00",
                         "slotDurationMinutes": 30, "enabled": %b,
                         "createdAt": "2026-07-01T12:00:00Z",
                         "updatedAt": "2026-09-10T13:40:00Z"}
                        """.formatted(id, profesionalId, dia, habilitado));
    }
}

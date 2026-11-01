package com.um.catalogsync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.application.exception.UnknownCategoryException;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.application.exception.UnknownProfessionalException;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.application.usecases.RunFullSyncUseCaseImpl;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.GetLocalCatalogVersionUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogSnapshotPort;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La sincronizacion completa contra un PostgreSQL real.
 *
 * <p>Es la prueba que importa del issue #13, porque los criterios que mas
 * valen no se pueden demostrar con dobles: que un fallo a mitad de camino
 * deje la copia anterior usable y la version sin tocar es una propiedad de
 * la transaccion, y una transaccion falsa no prueba nada. Las FK
 * {@code RESTRICT}, los CHECK y el rollback son de la base.
 *
 * <p><b>El caso de uso se arma a mano</b> con los beans reales y un doble
 * del unico puerto que sale de la maquina. {@code RunFullSyncUseCaseImpl} no
 * es transaccional, asi que construirlo asi no cambia nada: los
 * colaboradores siguen siendo los beans proxiados de Spring y las
 * transacciones son las de produccion. La alternativa —reemplazar el bean
 * del snapshot en el contexto— agregaria maquinaria de override para
 * ganar lo mismo.
 */
class FullSyncIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplyCatalogSnapshotUseCase applySnapshot;

    @Autowired
    private GetLocalCatalogVersionUseCase localVersion;

    @Autowired
    private SyncRunRepository syncRuns;

    @Autowired
    private CatalogVersionRepository versions;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProfessionalRepository professionals;

    @Autowired
    private WeeklyScheduleRepository schedules;

    @Autowired
    private JdbcTemplate jdbc;

    /** El orquestador real, con un snapshot fijo en lugar de la catedra. */
    private RunFullSyncUseCaseImpl sincronizacionQueTrae(CatalogSnapshot snapshot) {
        CatalogSnapshotPort doble = () -> snapshot;
        return new RunFullSyncUseCaseImpl(doble, applySnapshot, localVersion, syncRuns);
    }

    private List<Map<String, Object>> corridas() {
        return jdbc.queryForList("SELECT * FROM sync_run ORDER BY id");
    }

    private List<Map<String, Object>> errores() {
        return jdbc.queryForList("SELECT * FROM sync_error ORDER BY id");
    }

    // --- El camino feliz --------------------------------------------------

    @Test
    @DisplayName("Aplica las tres colecciones y deja la version en snapshotVersion")
    void aplicaElSnapshotCompleto() {
        SyncRun corrida = sincronizacionQueTrae(CatalogSnapshotFixtures.version(7L))
                .run(SyncTrigger.STARTUP);

        assertThat(categories.findAll()).hasSize(1);
        assertThat(professionals.findAll()).hasSize(1);
        assertThat(schedules.findAll()).hasSize(1);
        assertThat(versions.findCurrent()).get().satisfies(version -> {
            assertThat(version.getVersion()).isEqualTo(7L);
            assertThat(version.getSource()).isEqualTo(CatalogVersionSource.SNAPSHOT);
        });
        assertThat(corrida.getStatus()).isEqualTo(SyncStatus.SUCCEEDED);
        assertThat(corrida.getToVersion()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Las entidades deshabilitadas se conservan como baja logica")
    void conservaLasDeshabilitadas() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.conDeshabilitadas(7L))
                .run(SyncTrigger.STARTUP);

        // Se guardan las dos de cada coleccion, la habilitada y la que no.
        // §14.4: una entidad deshabilitada es una baja logica que la copia
        // local conserva, no una fila que se descarte.
        assertThat(categories.findAll())
                .extracting("id", "enabled")
                .containsExactlyInAnyOrder(tuple(1L, true), tuple(2L, false));
        assertThat(professionals.findAll())
                .extracting("id", "enabled")
                .containsExactlyInAnyOrder(tuple(15L, true), tuple(16L, false));
        assertThat(schedules.findAll())
                .extracting("id", "enabled")
                .containsExactlyInAnyOrder(tuple(21L, true), tuple(22L, false));
    }

    @Test
    @DisplayName("Un snapshot vacio deja la copia local vacia y la version asentada")
    void aplicaUnSnapshotVacio() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.version(7L)).run(SyncTrigger.STARTUP);

        sincronizacionQueTrae(CatalogSnapshotFixtures.vacio(8L)).run(SyncTrigger.EVENT);

        // Tres colecciones vacias es un snapshot valido: una catedra que
        // borro todo su catalogo. La copia local tiene que reflejarlo.
        assertThat(categories.findAll()).isEmpty();
        assertThat(professionals.findAll()).isEmpty();
        assertThat(schedules.findAll()).isEmpty();
        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(8L);
    }

    @Test
    @DisplayName("Reemplaza la copia anterior en lugar de acumularla")
    void reemplazaLaCopiaAnterior() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.conDeshabilitadas(7L))
                .run(SyncTrigger.STARTUP);

        sincronizacionQueTrae(CatalogSnapshotFixtures.version(8L)).run(SyncTrigger.EVENT);

        // El snapshot es una foto, no un delta: lo que no viene, no esta.
        assertThat(categories.findAll()).extracting("id").containsExactly(1L);
        assertThat(professionals.findAll()).extracting("id").containsExactly(15L);
        assertThat(schedules.findAll()).extracting("id").containsExactly(21L);
    }

    @Test
    @DisplayName("Dos corridas del mismo snapshot dejan el mismo estado final")
    void esIdempotente() {
        CatalogSnapshot snapshot = CatalogSnapshotFixtures.conDeshabilitadas(7L);

        sincronizacionQueTrae(snapshot).run(SyncTrigger.STARTUP);
        sincronizacionQueTrae(snapshot).run(SyncTrigger.EVENT);

        // Reprocesar no altera el resultado (§6.2, §18.1): avanzar a la
        // misma version esta permitido y no cuenta como retroceder.
        assertThat(categories.findAll()).hasSize(2);
        assertThat(professionals.findAll()).hasSize(2);
        assertThat(schedules.findAll()).hasSize(2);
        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(7L);
        assertThat(corridas()).hasSize(2);
        assertThat(corridas()).allSatisfy(fila ->
                assertThat(fila).containsEntry("status", "SUCCEEDED"));
    }

    @Test
    @DisplayName("La version puede retroceder por snapshot, que es el camino de la reconstruccion")
    void laVersionRetrocedePorSnapshot() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.version(7L)).run(SyncTrigger.STARTUP);

        sincronizacionQueTrae(CatalogSnapshotFixtures.version(3L))
                .run(SyncTrigger.DISCONTINUITY);

        // La version describe lo que la copia local TIENE, no lo mas alto que
        // alguna vez supimos (§18.2). Despues de reemplazarla con el
        // snapshot 3, la version es 3.
        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(3L);
    }

    // --- El fallo a mitad de camino ---------------------------------------

    @Test
    @DisplayName("Si falla el ultimo paso, la copia anterior y la version quedan intactas")
    void elFalloEnHorariosNoDejaRastro() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.conDeshabilitadas(7L))
                .run(SyncTrigger.STARTUP);

        // El horario apunta a un profesional que el snapshot no trae, asi que
        // estalla en el paso 5 de 5, cuando categorias y profesionales ya se
        // reemplazaron.
        assertThatThrownBy(() -> sincronizacionQueTrae(
                CatalogSnapshotFixtures.conHorarioHuerfano(8L)).run(SyncTrigger.EVENT))
                .isInstanceOf(UnknownProfessionalException.class);

        // Esto es el criterio central del issue: todo lo de la version 7
        // sigue ahi, incluidas las dos entidades que el snapshot 8 no traia.
        assertThat(categories.findAll()).extracting("id").containsExactlyInAnyOrder(1L, 2L);
        assertThat(professionals.findAll()).extracting("id").containsExactlyInAnyOrder(15L, 16L);
        assertThat(schedules.findAll()).extracting("id").containsExactlyInAnyOrder(21L, 22L);
        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(7L);
    }

    @Test
    @DisplayName("Si falla el paso de profesionales, tampoco queda rastro")
    void elFalloEnProfesionalesNoDejaRastro() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.version(7L)).run(SyncTrigger.STARTUP);

        // El profesional apunta a una categoria que el snapshot no trae:
        // estalla en el paso 4, cuando las categorias ya se reemplazaron.
        assertThatThrownBy(() -> sincronizacionQueTrae(
                CatalogSnapshotFixtures.conProfesionalHuerfano(8L)).run(SyncTrigger.EVENT))
                .isInstanceOf(UnknownCategoryException.class);

        assertThat(categories.findAll()).extracting("id").containsExactly(1L);
        assertThat(professionals.findAll()).extracting("id").containsExactly(15L);
        assertThat(schedules.findAll()).extracting("id").containsExactly(21L);
        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(7L);
    }

    @Test
    @DisplayName("Con la base vacia, un fallo la deja vacia y sin version")
    void elFalloConBaseVaciaNoDejaVersion() {
        assertThatThrownBy(() -> sincronizacionQueTrae(
                CatalogSnapshotFixtures.conHorarioHuerfano(7L)).run(SyncTrigger.STARTUP))
                .isInstanceOf(UnknownProfessionalException.class);

        // Sin version y no en la version 0: son dos estados distintos, y
        // "sin version" es el que dispara la sincronizacion completa (#14).
        assertThat(versions.findCurrent()).isEmpty();
        assertThat(categories.findAll()).isEmpty();
        assertThat(professionals.findAll()).isEmpty();
    }

    // --- El historial ----------------------------------------------------

    @Test
    @DisplayName("Una corrida exitosa registra inicio, fin, resultado y motivo")
    void registraLaCorridaExitosa() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.version(7L)).run(SyncTrigger.STARTUP);
        sincronizacionQueTrae(CatalogSnapshotFixtures.version(9L)).run(SyncTrigger.EVENT);

        assertThat(corridas()).hasSize(2);
        assertThat(corridas().getLast()).satisfies(fila -> {
            assertThat(fila).containsEntry("mode", "FULL");
            assertThat(fila).containsEntry("triggered_by", "EVENT");
            assertThat(fila).containsEntry("status", "SUCCEEDED");
            assertThat(fila).containsEntry("from_version", 7L);
            assertThat(fila).containsEntry("to_version", 9L);
            assertThat(fila.get("started_at")).isNotNull();
            assertThat(fila.get("finished_at")).isNotNull();
        });
        assertThat(errores()).isEmpty();
    }

    @Test
    @DisplayName("La primera corrida registra from_version nulo, no cero")
    void registraLaPrimeraCorridaSinVersionPrevia() {
        sincronizacionQueTrae(CatalogSnapshotFixtures.version(7L)).run(SyncTrigger.STARTUP);

        assertThat(corridas()).singleElement().satisfies(fila -> {
            assertThat(fila).containsEntry("from_version", null);
            assertThat(fila).containsEntry("to_version", 7L);
        });
    }

    @Test
    @DisplayName("El registro de la corrida fallida SOBREVIVE al rollback de los datos")
    void elRegistroDelFalloSobreviveAlRollback() {
        assertThatThrownBy(() -> sincronizacionQueTrae(
                CatalogSnapshotFixtures.conHorarioHuerfano(7L)).run(SyncTrigger.DISCONTINUITY))
                .isInstanceOf(UnknownProfessionalException.class);

        // Es el punto de que el historial se escriba en REQUIRES_NEW: los
        // datos volvieron atras, el registro de que se intento no. Un
        // historial que desaparece cuando hay un error es peor que no tener
        // historial, porque da la impresion de que nunca se intento nada.
        assertThat(corridas()).singleElement().satisfies(fila -> {
            assertThat(fila).containsEntry("mode", "FULL");
            assertThat(fila).containsEntry("triggered_by", "DISCONTINUITY");
            assertThat(fila).containsEntry("status", "FAILED");
            // Sin to_version: no se llego a ninguna version nueva.
            assertThat(fila).containsEntry("to_version", null);
            assertThat(fila.get("finished_at")).isNotNull();
        });
        assertThat(errores()).singleElement().satisfies(fila -> {
            assertThat(fila).containsEntry("code", "CATALOG_INCONSISTENT");
            // El detalle es el mensaje de la excepcion de dominio. No nombra
            // el profesional 999: el adaptador de horarios ya documenta que
            // en una operacion de lote no sabe cual de las filas fallo, asi
            // que pasa null. El code es lo que permite decidir; precisar el
            // detalle es del slice de horarios, no de este issue.
            assertThat(fila.get("detail")).asString()
                    .contains("No se puede guardar un horario del profesional");
            assertThat(fila.get("occurred_at")).isNotNull();
        });
        assertThat(versions.findCurrent()).isEmpty();
    }

    @Test
    @DisplayName("Una corrida fallida y despues una exitosa dejan dos filas distinguibles")
    void registraLasDosCorridas() {
        assertThatThrownBy(() -> sincronizacionQueTrae(
                CatalogSnapshotFixtures.conHorarioHuerfano(7L)).run(SyncTrigger.STARTUP))
                .isInstanceOf(UnknownProfessionalException.class);

        sincronizacionQueTrae(CatalogSnapshotFixtures.version(7L))
                .run(SyncTrigger.DISCONTINUITY);

        assertThat(corridas()).extracting("status", "triggered_by")
                .containsExactly(
                        tuple("FAILED", "STARTUP"),
                        tuple("SUCCEEDED", "DISCONTINUITY"));
        assertThat(errores()).hasSize(1);
    }
}

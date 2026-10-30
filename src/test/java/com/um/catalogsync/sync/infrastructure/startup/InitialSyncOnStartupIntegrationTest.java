package com.um.catalogsync.sync.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.CatalogSnapshotFixtures;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException;
import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.application.service.CatalogVersionService;
import com.um.catalogsync.sync.application.usecases.RunFullSyncUseCaseImpl;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
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
 * El arranque con base vacia, contra un PostgreSQL real.
 *
 * <p>Las pruebas unitarias del runner ya fijan la decision y el manejo de
 * la falla con dobles. Lo que falta demostrar y no se puede con dobles es
 * lo que depende de la base: que despues del arranque la copia local quede
 * realmente aplicada, y sobre todo que un reinicio en medio de la primera
 * sincronizacion no deje nada a medio escribir y vuelva a intentar. Eso es
 * una propiedad de la transaccion, y una transaccion falsa no prueba nada.
 *
 * <p><b>El runner se arma a mano</b> con los beans reales y un doble del
 * unico puerto que sale de la maquina, igual que {@code FullSyncIntegrationTest}
 * y por el mismo motivo: ni el runner ni {@code RunFullSyncUseCaseImpl} son
 * transaccionales, asi que construirlos asi no cambia nada —los
 * colaboradores siguen siendo los beans proxiados de Spring— y evita la
 * maquinaria de reemplazar beans en el contexto. Se le pasa un executor
 * directo: el hilo aparte ya esta probado en el test unitario y aca solo
 * volveria las aserciones carreras.
 */
class InitialSyncOnStartupIntegrationTest extends AbstractPostgresIntegrationTest {

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

    /** El runner real, con la catedra reemplazada por un puerto a medida. */
    private InitialCatalogSyncRunner arranqueCon(CatalogSnapshotPort catedra) {
        return new InitialCatalogSyncRunner(
                new CatalogVersionService(localVersion),
                new CatalogSyncService(
                        new RunFullSyncUseCaseImpl(
                                catedra, applySnapshot, localVersion, syncRuns),
                        applySnapshot),
                Runnable::run);
    }

    private InitialCatalogSyncRunner arranqueQueTrae(CatalogSnapshot snapshot) {
        return arranqueCon(() -> snapshot);
    }

    private List<Map<String, Object>> corridas() {
        return jdbc.queryForList("SELECT * FROM sync_run ORDER BY id");
    }

    @Test
    @DisplayName("Con la base vacia, el arranque deja el catalogo aplicado y la version asentada")
    void conLaBaseVaciaDejaElCatalogoAplicado() {
        arranqueQueTrae(CatalogSnapshotFixtures.version(7L)).run(null);

        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(7L);
        assertThat(categories.findAll()).hasSize(1);
        assertThat(professionals.findAll()).hasSize(1);
        assertThat(schedules.findAll()).hasSize(1);
        assertThat(corridas()).singleElement().satisfies(fila -> {
            assertThat(fila).containsEntry("triggered_by", "STARTUP");
            assertThat(fila).containsEntry("status", "SUCCEEDED");
            // fromVersion nulo: la base estaba recien inicializada. Es la
            // diferencia con "veniamos de la version 0".
            assertThat(fila).containsEntry("from_version", null);
            assertThat(fila).containsEntry("to_version", 7L);
        });
    }

    @Test
    @DisplayName("Con version local ya aplicada, el arranque no pide el snapshot")
    void conVersionLocalNoPideElSnapshot() {
        arranqueQueTrae(CatalogSnapshotFixtures.version(7L)).run(null);

        // Este puerto no devuelve un snapshot: rompe la prueba si alguien lo
        // invoca. El criterio no es "se pide una vez menos", es "no se pide".
        arranqueCon(() -> fail("El arranque no tenia que pedir el snapshot: ya habia version"))
                .run(null);

        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(7L);
        // Y tampoco se abrio una corrida: la decision se toma antes de
        // registrar nada, asi que el historial no se llena de arranques.
        assertThat(corridas()).hasSize(1);
    }

    @Test
    @DisplayName("Si la catedra no responde, el servicio arranca igual y sin catalogo")
    void siLaCatedraNoRespondeElServicioArrancaIgual() {
        InitialCatalogSyncRunner arranque = arranqueCon(() -> {
            throw new CatalogSnapshotUnavailableException(
                    "/api/synchronization/snapshot", new RuntimeException("connect timed out"));
        });

        assertThatCode(() -> arranque.run(null)).doesNotThrowAnyException();

        // Sin version y no en la version 0, que es lo que va a hacer que el
        // proximo arranque vuelva a intentarlo.
        assertThat(versions.findCurrent()).isEmpty();
        assertThat(categories.findAll()).isEmpty();
        // La falla no desaparece por no haber tumbado el proceso: queda en
        // el historial, que es de donde #28 la va a informar.
        assertThat(corridas()).singleElement().satisfies(fila -> {
            assertThat(fila).containsEntry("triggered_by", "STARTUP");
            assertThat(fila).containsEntry("status", "FAILED");
            assertThat(fila).containsEntry("to_version", null);
        });
        assertThat(jdbc.queryForList("SELECT code FROM sync_error"))
                .singleElement()
                .isEqualTo(Map.of("code", "SNAPSHOT_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Un snapshot que se contradice no deja la copia local a medio escribir")
    void unSnapshotInconsistenteNoDejaLaCopiaAMedioEscribir() {
        // El horario apunta a un profesional que el snapshot no trae, asi que
        // estalla en el ultimo paso, cuando categorias y profesionales ya se
        // reemplazaron dentro de la transaccion.
        arranqueQueTrae(CatalogSnapshotFixtures.conHorarioHuerfano(7L)).run(null);

        assertThat(versions.findCurrent()).isEmpty();
        assertThat(categories.findAll()).isEmpty();
        assertThat(professionals.findAll()).isEmpty();
        assertThat(schedules.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Un reinicio en medio de la primera sincronizacion vuelve a intentar y cierra bien")
    void unReinicioAMediasVuelveAIntentar() {
        // Asi queda la base cuando el proceso muere sincronizando: la unidad
        // de trabajo se fue entera en el rollback —ni datos ni version— y lo
        // unico que sobrevive es la corrida abierta, que nadie llego a
        // cerrar. No se simula matando la JVM: se deja exactamente ese
        // estado, que es lo que el arranque siguiente se encuentra.
        jdbc.update("""
                INSERT INTO sync_run (mode, triggered_by, status, started_at)
                VALUES ('FULL', 'STARTUP', 'RUNNING', now())
                """);

        arranqueQueTrae(CatalogSnapshotFixtures.version(9L)).run(null);

        // Vuelve a intentar sin que nadie se lo pida y sin ninguna limpieza
        // previa: findCurrent sigue vacio, que es toda la condicion.
        assertThat(versions.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(9L);
        assertThat(categories.findAll()).hasSize(1);

        // La corrida interrumpida se deja como esta, a proposito: una fila
        // en RUNNING para siempre dice que el servicio se cayo sincronizando,
        // y esa es informacion que el historial tiene que conservar. Lo que
        // tiene que estar consistente es la copia local, no el historial.
        assertThat(corridas()).hasSize(2);
        assertThat(corridas().getFirst()).containsEntry("status", "RUNNING");
        assertThat(corridas().getLast()).containsEntry("status", "SUCCEEDED");
    }
}

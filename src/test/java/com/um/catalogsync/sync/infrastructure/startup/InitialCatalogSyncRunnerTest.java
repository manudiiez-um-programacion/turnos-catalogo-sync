package com.um.catalogsync.sync.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException;
import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.application.service.CatalogVersionService;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import com.um.catalogsync.sync.domain.ports.in.SynchronizeCatalogUseCase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La reconciliacion de arranque.
 *
 * <p>Se prueba a mano y no levantando el contexto, igual que
 * {@code IntegrationConfigStartupVerifierTest} y por el mismo motivo: el
 * perfil {@code test} apaga este componente a proposito.
 *
 * <p><b>Que cambio con #27.</b> Hasta #26 este runner miraba la version
 * local y, si la habia, no hacia nada. Ahora compara siempre: un servicio
 * que estuvo apagado un rato vuelve con una version vieja y sin ninguna
 * notificacion que le avise del atraso, porque los avisos publicados
 * mientras estaba caido no se le reenvian (§8, §16). La base vacia dejo de
 * ser un caso aparte y paso a ser un resultado de la comparacion.
 *
 * <p>Lo que sigue igual son las otras dos promesas: una falla no mata el
 * proceso, y el arranque no espera el trabajo.
 *
 * <p>Los dos casos de uso que el arranque no deberia tocar —aplicar un
 * snapshot que ya se tiene, y recorrer versiones sin pasar por la decision—
 * quedan explotando en los dobles: si alguna vez se los invoca, la prueba lo
 * dice. Es mas firme que un contador en cero.
 */
class InitialCatalogSyncRunnerTest {

    private static final CatalogVersion VERSION_APLICADA = CatalogVersion.builder()
            .version(7L)
            .appliedAt(Instant.parse("2026-10-30T12:00:00Z"))
            .source(CatalogVersionSource.SNAPSHOT)
            .build();

    /** Una fachada de version que siempre responde lo mismo. */
    private static CatalogVersionService versionLocal(CatalogVersion aplicada) {
        return new CatalogVersionService(() -> Optional.ofNullable(aplicada));
    }

    /**
     * Una fachada armada solo sobre la decision, que es lo unico que el
     * arranque usa.
     */
    private static CatalogSyncService sincronizacion(SynchronizeCatalogUseCase decision) {
        RunFullSyncUseCase noDirecto = (trigger, motivo) -> {
            throw new AssertionError("El arranque no elige la completa por su cuenta:"
                    + " pasa por la decision de §6.2");
        };
        ApplyCatalogSnapshotUseCase noCorresponde = snapshot -> {
            throw new AssertionError("El arranque no aplica snapshots por su cuenta");
        };
        ApplyIncrementalChangesUseCase noIncremental = version -> {
            throw new AssertionError("El arranque no recorre versiones por su cuenta");
        };
        return new CatalogSyncService(noDirecto, noCorresponde, noIncremental, decision);
    }

    private static SyncRun corrida(SyncMode modo, SyncDecisionReason motivo, long hasta) {
        return SyncRun.builder()
                .id(1L)
                .mode(modo)
                .triggeredBy(SyncTrigger.STARTUP)
                .decisionReason(motivo)
                .status(SyncStatus.SUCCEEDED)
                .toVersion(hasta)
                .startedAt(Instant.parse("2026-10-30T12:00:00Z"))
                .finishedAt(Instant.parse("2026-10-30T12:00:05Z"))
                .build();
    }

    @Test
    @DisplayName("Con la base vacia sincroniza, y el trigger queda en STARTUP")
    void conLaBaseVaciaSincroniza() {
        List<SyncTrigger> triggers = new ArrayList<>();
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(null),
                sincronizacion(trigger -> {
                    triggers.add(trigger);
                    return Optional.of(corrida(
                            SyncMode.FULL, SyncDecisionReason.NO_LOCAL_VERSION, 12L));
                }),
                Runnable::run);

        runner.sincronizarSiHaceFalta();

        // El trigger no es decorativo: es lo que despues distingue en el
        // historial una sincronizacion de arranque de una por notificacion o
        // por el chequeo periodico.
        assertThat(triggers).containsExactly(SyncTrigger.STARTUP);
    }

    @Test
    @DisplayName("Con version local tambien compara: un atraso se recupera al arrancar")
    void conVersionLocalTambienCompara() {
        // Es el agujero que #27 cierra. Antes, un servicio que volvia de
        // estar apagado se quedaba con la version vieja hasta que llegara
        // una notificacion nueva, y las de mientras estuvo caido no se
        // reenvian.
        List<SyncTrigger> triggers = new ArrayList<>();
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(VERSION_APLICADA),
                sincronizacion(trigger -> {
                    triggers.add(trigger);
                    return Optional.of(corrida(
                            SyncMode.INCREMENTAL,
                            SyncDecisionReason.BEHIND_CURRENT_VERSION, 9L));
                }),
                Runnable::run);

        runner.sincronizarSiHaceFalta();

        assertThat(triggers).containsExactly(SyncTrigger.STARTUP);
    }

    @Test
    @DisplayName("Si ya estaba al dia, no pasa nada y no se rompe nada")
    void siYaEstabaAlDia() {
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(VERSION_APLICADA),
                sincronizacion(trigger -> Optional.empty()),
                Runnable::run);

        assertThatCode(runner::sincronizarSiHaceFalta).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Si la catedra no responde, el servicio sigue arriba en lugar de caerse")
    void conLaCatedraCaidaNoTumbaElServicio() {
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(null),
                sincronizacion(trigger -> {
                    throw new CatalogSnapshotUnavailableException(
                            "la catedra no responde", new RuntimeException("connect timed out"));
                }),
                Runnable::run);

        // Que la excepcion no salga es todo el criterio: si saliera, el hilo
        // de arranque la propagaria y el proceso terminaria con error. Con
        // restart: unless-stopped en el compose, eso es el ciclo de
        // reinicios que el issue pide evitar. Y el atraso que no se recupero
        // ahora lo reintenta el chequeo periodico.
        assertThatCode(runner::sincronizarSiHaceFalta).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El arranque no espera a la sincronizacion: la delega y vuelve")
    void elArranqueNoEsperaLaSincronizacion() {
        List<Runnable> encoladas = new ArrayList<>();
        Executor nuncaCorre = encoladas::add;
        List<SyncTrigger> triggers = new ArrayList<>();
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(null),
                sincronizacion(trigger -> {
                    triggers.add(trigger);
                    return Optional.of(corrida(
                            SyncMode.FULL, SyncDecisionReason.NO_LOCAL_VERSION, 12L));
                }),
                nuncaCorre);

        runner.run(null);

        // Con un executor que no ejecuta nada, haber vuelto de run() sin que
        // se pidiera nada demuestra que el trabajo no ocurre en el hilo de
        // arranque. Es mas firme que medir cuanto tardo, que en una maquina
        // cargada falla sin que nadie haya roto nada.
        assertThat(encoladas).hasSize(1);
        assertThat(triggers).isEmpty();

        encoladas.getFirst().run();

        assertThat(triggers).containsExactly(SyncTrigger.STARTUP);
    }
}

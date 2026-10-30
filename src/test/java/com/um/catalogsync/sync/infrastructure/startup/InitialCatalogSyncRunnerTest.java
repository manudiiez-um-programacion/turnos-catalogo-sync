package com.um.catalogsync.sync.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException;
import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.application.service.CatalogVersionService;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La decision de sincronizar al arrancar.
 *
 * <p>Se prueba a mano y no levantando el contexto, igual que
 * {@code IntegrationConfigStartupVerifierTest} y por el mismo motivo: el
 * perfil {@code test} apaga este componente a proposito. Lo que hay que
 * fijar son las cuatro promesas de la clase —pide snapshot si no hay
 * version, no lo pide si la hay, una falla no mata el proceso y el arranque
 * no espera el trabajo— y ninguna necesita un contexto de Spring para
 * observarse.
 *
 * <p>El caso de "no vuelve a pedir el snapshot" se prueba con un doble que
 * <b>falla si lo invocan</b> en lugar de con un contador. Es la unica forma
 * de que el test diga exactamente lo que el criterio pide: no es que se
 * llame una vez de menos, es que no se llama.
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
     * Una fachada de sincronizacion armada sobre un solo caso de uso.
     *
     * <p>El de aplicar snapshot queda explotando: este componente no deberia
     * tocarlo nunca —aplicar un snapshot que ya se tiene es el camino de la
     * reconstruccion por discontinuidad (#26), no el del arranque— y si
     * alguna vez lo hace, la prueba lo dice.
     */
    private static CatalogSyncService sincronizacion(RunFullSyncUseCase completa) {
        ApplyCatalogSnapshotUseCase noCorresponde = snapshot -> {
            throw new AssertionError("El arranque no aplica snapshots por su cuenta");
        };
        return new CatalogSyncService(completa, noCorresponde);
    }

    private static SyncRun corridaExitosa(long hasta) {
        return SyncRun.builder()
                .id(1L)
                .mode(SyncMode.FULL)
                .triggeredBy(SyncTrigger.STARTUP)
                .status(SyncStatus.SUCCEEDED)
                .toVersion(hasta)
                .startedAt(Instant.parse("2026-10-30T12:00:00Z"))
                .finishedAt(Instant.parse("2026-10-30T12:00:05Z"))
                .build();
    }

    @Test
    @DisplayName("Con la base vacia pide el catalogo completo, y el motivo queda en STARTUP")
    void conLaBaseVaciaSincroniza() {
        List<SyncTrigger> motivos = new ArrayList<>();
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(null),
                sincronizacion(trigger -> {
                    motivos.add(trigger);
                    return corridaExitosa(12L);
                }),
                Runnable::run);

        runner.sincronizarSiHaceFalta();

        // El trigger no es decorativo: es lo que despues distingue en el
        // historial una primera sincronizacion de una por discontinuidad.
        assertThat(motivos).containsExactly(SyncTrigger.STARTUP);
    }

    @Test
    @DisplayName("Con version local ya aplicada no pide ningun snapshot")
    void conVersionLocalNoSincroniza() {
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(VERSION_APLICADA),
                sincronizacion(trigger -> fail("No tenia que pedir el snapshot: ya habia version")),
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
        // reinicios que el issue pide evitar.
        assertThatCode(runner::sincronizarSiHaceFalta).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El arranque no espera a la sincronizacion: la delega y vuelve")
    void elArranqueNoEsperaLaSincronizacion() {
        List<Runnable> encoladas = new ArrayList<>();
        Executor nuncaCorre = encoladas::add;
        List<SyncTrigger> motivos = new ArrayList<>();
        InitialCatalogSyncRunner runner = new InitialCatalogSyncRunner(
                versionLocal(null),
                sincronizacion(trigger -> {
                    motivos.add(trigger);
                    return corridaExitosa(12L);
                }),
                nuncaCorre);

        runner.run(null);

        // Con un executor que no ejecuta nada, haber vuelto de run() sin que
        // se pidiera el snapshot demuestra que el trabajo no ocurre en el
        // hilo de arranque. Es mas firme que medir cuanto tardo, que en una
        // maquina cargada falla sin que nadie haya roto nada.
        assertThat(encoladas).hasSize(1);
        assertThat(motivos).isEmpty();

        encoladas.getFirst().run();

        assertThat(motivos).containsExactly(SyncTrigger.STARTUP);
    }
}

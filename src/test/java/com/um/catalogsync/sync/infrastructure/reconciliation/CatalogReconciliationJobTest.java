package com.um.catalogsync.sync.infrastructure.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import com.um.catalogsync.sync.domain.ports.in.SynchronizeCatalogUseCase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El chequeo periodico: con que trigger pide, y que hace si falla.
 *
 * <p>No decide nada por su cuenta —la decision es de
 * {@code SynchronizeCatalogUseCase}, que ya tiene sus doce pruebas— asi que
 * lo que se fija acá es poco y preciso: que use el trigger
 * {@code RECONCILIATION}, que una falla no se propague al planificador, y
 * que una ventana de tiempo invalida no se pueda configurar.
 */
class CatalogReconciliationJobTest {

    private final List<SyncTrigger> triggers = new ArrayList<>();

    @Test
    @DisplayName("Pide sincronizar con el trigger RECONCILIATION")
    void pideConSuPropioTrigger() {
        // El trigger es lo que despues distingue en el historial una corrida
        // del chequeo periodico de una por notificacion. Si las dos
        // quedaran iguales, no habria forma de demostrar que la
        // reconciliacion recupero un aviso perdido, que es justamente la
        // evidencia que §16 pide poder dar.
        job(trigger -> {
            triggers.add(trigger);
            return Optional.of(corrida());
        }).reconciliar();

        assertThat(triggers).containsExactly(SyncTrigger.RECONCILIATION);
    }

    @Test
    @DisplayName("Si no habia nada que hacer, no pasa nada")
    void sinNovedadesNoHaceNada() {
        // El criterio del issue: no genera carga ni logs cuando no hay nada
        // que hacer. Lo unico que este trabajo puede hacer de mas es
        // loguear, y el Optional vacio es el camino que no loguea.
        assertThatCode(() -> job(trigger -> {
            triggers.add(trigger);
            return Optional.empty();
        }).reconciliar()).doesNotThrowAnyException();

        assertThat(triggers).containsExactly(SyncTrigger.RECONCILIATION);
    }

    @Test
    @DisplayName("Si la sincronizacion falla, no se propaga al planificador")
    void laFallaNoSePropaga() {
        // Lo invoca el planificador y no hay a quien avisarle. La corrida
        // fallida ya quedo en sync_run: propagar solo agregaria una traza
        // del planificador arriba de la que ya se escribio.
        assertThatCode(() -> job(trigger -> {
            throw new IllegalStateException("la catedra no responde");
        }).reconciliar()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un intervalo no positivo no se puede configurar")
    void intervaloInvalido() {
        // En cero el chequeo correria sin pausa y la catedra veria una
        // lectura continua. Mejor que el arranque falle nombrando la
        // propiedad.
        assertThatThrownBy(() -> new CatalogReconciliationProperties(
                        true, Duration.ZERO, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sync.reconciliation.interval");
    }

    @Test
    @DisplayName("Un retardo inicial ausente no se puede configurar")
    void retardoInicialInvalido() {
        assertThatThrownBy(() -> new CatalogReconciliationProperties(
                        true, Duration.ofMinutes(5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sync.reconciliation.initial-delay");
    }

    @Test
    @DisplayName("Los valores del application.yaml se aceptan")
    void losValoresDeProduccion() {
        assertThatCode(() -> new CatalogReconciliationProperties(
                        true, Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    private CatalogReconciliationJob job(SynchronizeCatalogUseCase decision) {
        RunFullSyncUseCase noDirecto = (trigger, motivo) -> {
            throw new AssertionError("El chequeo no elige el modo: pasa por la decision");
        };
        ApplyCatalogSnapshotUseCase noAplica = (CatalogSnapshot snapshot) -> {
            throw new AssertionError("El chequeo no aplica snapshots");
        };
        ApplyIncrementalChangesUseCase noRecorre = (long version) -> {
            throw new AssertionError("El chequeo no recorre versiones");
        };
        return new CatalogReconciliationJob(
                new CatalogSyncService(noDirecto, noAplica, noRecorre, decision));
    }

    private static SyncRun corrida() {
        return SyncRun.builder()
                .id(3L)
                .mode(SyncMode.INCREMENTAL)
                .triggeredBy(SyncTrigger.RECONCILIATION)
                .decisionReason(SyncDecisionReason.BEHIND_CURRENT_VERSION)
                .status(SyncStatus.SUCCEEDED)
                .fromVersion(7L)
                .toVersion(9L)
                .startedAt(Instant.parse("2026-11-02T12:00:00Z"))
                .finishedAt(Instant.parse("2026-11-02T12:00:02Z"))
                .build();
    }
}

package com.um.catalogsync.sync.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.CatalogSnapshotFixtures;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.IncrementalSyncOutcome;
import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.in.SynchronizeCatalogUseCase;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La fachada delega y no decide nada. */
class CatalogSyncServiceTest {

    private static final SyncRun CORRIDA = SyncRun.builder()
            .id(1L)
            .mode(SyncMode.FULL)
            .triggeredBy(SyncTrigger.STARTUP)
            .status(SyncStatus.SUCCEEDED)
            .toVersion(7L)
            .startedAt(Instant.parse("2026-09-10T13:44:00Z"))
            .finishedAt(Instant.parse("2026-09-10T13:45:00Z"))
            .build();

    private static final CatalogVersion VERSION_7 = CatalogVersion.builder()
            .version(7L)
            .appliedAt(Instant.parse("2026-09-10T13:45:00Z"))
            .source(CatalogVersionSource.SNAPSHOT)
            .build();

    private static final IncrementalSyncResult AL_DIA =
            new IncrementalSyncResult(7L, 9L, 9L, 2, IncrementalSyncOutcome.CAUGHT_UP);

    @Test
    @DisplayName("Pasa el trigger al caso de uso y devuelve la corrida")
    void delegaLaSincronizacionCompleta() {
        var service = new CatalogSyncService(
                (trigger, motivo) -> {
                    assertThat(trigger).isEqualTo(SyncTrigger.DISCONTINUITY);
                    assertThat(motivo).isEqualTo(SyncDecisionReason.VERSION_OUT_OF_WINDOW);
                    return CORRIDA;
                },
                snapshot -> {
                    throw new UnsupportedOperationException();
                },
                noSeUsa(),
                noSincroniza());

        assertThat(service.runFullSync(
                        SyncTrigger.DISCONTINUITY, SyncDecisionReason.VERSION_OUT_OF_WINDOW))
                .isSameAs(CORRIDA);
    }

    @Test
    @DisplayName("Aplica un snapshot que ya se tiene sin volver a pedirlo")
    void delegaLaAplicacion() {
        CatalogSnapshot snapshot = CatalogSnapshotFixtures.version(7L);
        var service = new CatalogSyncService(
                (trigger, motivo) -> {
                    throw new UnsupportedOperationException();
                },
                recibido -> {
                    assertThat(recibido).isSameAs(snapshot);
                    return VERSION_7;
                },
                noSeUsa(),
                noSincroniza());

        assertThat(service.applySnapshot(snapshot)).isSameAs(VERSION_7);
    }

    @Test
    @DisplayName("Pasa el destino al recorrido incremental y devuelve su resultado")
    void delegaElIncremental() {
        var service = new CatalogSyncService(
                (trigger, motivo) -> {
                    throw new UnsupportedOperationException();
                },
                snapshot -> {
                    throw new UnsupportedOperationException();
                },
                destino -> {
                    assertThat(destino).isEqualTo(9L);
                    return AL_DIA;
                },
                noSincroniza());

        assertThat(service.applyIncrementalUpTo(9L)).isSameAs(AL_DIA);
    }

    @Test
    @DisplayName("Pasa el trigger a la decision y devuelve la corrida que se hizo")
    void delegaLaDecision() {
        var service = new CatalogSyncService(
                (trigger, motivo) -> {
                    throw new UnsupportedOperationException();
                },
                snapshot -> {
                    throw new UnsupportedOperationException();
                },
                noSeUsa(),
                trigger -> {
                    assertThat(trigger).isEqualTo(SyncTrigger.RECONCILIATION);
                    return Optional.of(CORRIDA);
                });

        assertThat(service.synchronize(SyncTrigger.RECONCILIATION)).contains(CORRIDA);
    }

    /** Para el caso de uso que la prueba no espera que se invoque. */
    private static ApplyIncrementalChangesUseCase noSeUsa() {
        return version -> {
            throw new UnsupportedOperationException();
        };
    }

    private static SynchronizeCatalogUseCase noSincroniza() {
        return trigger -> {
            throw new UnsupportedOperationException();
        };
    }
}

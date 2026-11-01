package com.um.catalogsync.sync.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.CatalogSnapshotFixtures;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import java.time.Instant;
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

    @Test
    @DisplayName("Pasa el trigger al caso de uso y devuelve la corrida")
    void delegaLaSincronizacionCompleta() {
        var service = new CatalogSyncService(
                trigger -> {
                    assertThat(trigger).isEqualTo(SyncTrigger.DISCONTINUITY);
                    return CORRIDA;
                },
                snapshot -> {
                    throw new UnsupportedOperationException();
                });

        assertThat(service.runFullSync(SyncTrigger.DISCONTINUITY)).isSameAs(CORRIDA);
    }

    @Test
    @DisplayName("Aplica un snapshot que ya se tiene sin volver a pedirlo")
    void delegaLaAplicacion() {
        CatalogSnapshot snapshot = CatalogSnapshotFixtures.version(7L);
        var service = new CatalogSyncService(
                trigger -> {
                    throw new UnsupportedOperationException();
                },
                recibido -> {
                    assertThat(recibido).isSameAs(snapshot);
                    return VERSION_7;
                });

        assertThat(service.applySnapshot(snapshot)).isSameAs(VERSION_7);
    }
}

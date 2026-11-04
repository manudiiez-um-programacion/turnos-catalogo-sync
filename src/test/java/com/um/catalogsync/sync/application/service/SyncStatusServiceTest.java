package com.um.catalogsync.sync.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.domain.model.SyncOriginState;
import com.um.catalogsync.sync.domain.model.SyncStatusReport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncStatusServiceTest {

    private static final SyncStatusReport REPORTE = new SyncStatusReport(
            null, null, SyncOriginState.UNREACHABLE, null, List.of());

    @Test
    @DisplayName("getStatus delega en el caso de uso")
    void delegaEnElCasoDeUso() {
        SyncStatusService service = new SyncStatusService(() -> REPORTE);

        assertThat(service.getStatus()).isSameAs(REPORTE);
    }
}

package com.um.catalogsync.sync.infrastructure.web.mapper;

import com.um.catalogsync.shared.infrastructure.web.dto.CatalogVersionResponse;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatusReport;
import com.um.catalogsync.sync.infrastructure.web.dto.LastSyncResponse;
import com.um.catalogsync.sync.infrastructure.web.dto.RemoteCatalogStateResponse;
import com.um.catalogsync.sync.infrastructure.web.dto.SyncErrorResponse;
import com.um.catalogsync.sync.infrastructure.web.dto.SyncStatusResponse;
import org.springframework.stereotype.Component;

/**
 * Del reporte de dominio al DTO de respuesta.
 *
 * <p>Existe por lo mismo que los otros mappers web: para que el controlador no
 * sepa de dominio y el dominio no sepa de HTTP (ADR 0001).
 *
 * <p><b>No calcula nada.</b> El estado y el atraso los decide
 * {@link SyncStatusReport}, y acá solo se leen. Es la linea que separa este
 * mapper de una regla escondida en la capa web: si {@code health()} se
 * calculara acá, el reporte seria una cosa distinta segun quien lo mire.
 *
 * <p><b>Los enums viajan como texto</b>, igual que en el resto del contrato:
 * el cliente decide por el valor y no por una clase de Java, y agregar un
 * valor nuevo al enum no rompe la deserializacion del otro lado.
 *
 * <p>Solo tiene direccion de salida: este endpoint no recibe cuerpo.
 */
@Component
public class SyncStatusWebMapper {

    public SyncStatusResponse toResponse(SyncStatusReport reporte) {
        return new SyncStatusResponse(
                reporte.health().name(),
                reporte.localVersion() == null
                        ? null
                        : CatalogVersionResponse.from(reporte.localVersion()),
                toResponse(reporte.originState().name(), reporte.origin()),
                reporte.versionsBehind(),
                toResponse(reporte.lastRun()),
                reporte.recentErrors().stream().map(this::toResponse).toList());
    }

    private RemoteCatalogStateResponse toResponse(
            String estado, PublishedCatalogMetadata origen) {

        if (origen == null) {
            return new RemoteCatalogStateResponse(estado, null, null, null);
        }
        return new RemoteCatalogStateResponse(
                estado,
                origen.getCurrentVersion(),
                origen.getOldestAvailableVersion(),
                origen.getPublishedAt());
    }

    private LastSyncResponse toResponse(SyncRun corrida) {
        if (corrida == null) {
            return null;
        }
        return new LastSyncResponse(
                corrida.getId(),
                corrida.getMode().name(),
                corrida.getTriggeredBy().name(),
                corrida.getDecisionReason() == null ? null : corrida.getDecisionReason().name(),
                corrida.getStatus().name(),
                corrida.getFromVersion(),
                corrida.getToVersion(),
                corrida.getStartedAt(),
                corrida.getFinishedAt());
    }

    private SyncErrorResponse toResponse(SyncError error) {
        return new SyncErrorResponse(
                error.getSyncRunId(),
                error.getOccurredAt(),
                error.getCode().name(),
                error.getDetail());
    }
}

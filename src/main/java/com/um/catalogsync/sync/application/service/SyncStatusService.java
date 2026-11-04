package com.um.catalogsync.sync.application.service;

import com.um.catalogsync.sync.domain.model.SyncStatusReport;
import com.um.catalogsync.sync.domain.ports.in.GetSyncStatusUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fachada del estado y los errores de sincronizacion.
 *
 * <p>Es la tercera fachada del slice, y la razon esta anotada desde la
 * primera: {@code sync} modela varios procesos distintos —la version
 * aplicada, correr una sincronizacion, informar el estado— y juntarlos en un
 * unico {@code @Service} daria una clase que crece sin criterio. Esta cubre
 * informar; {@code CatalogSyncService} cubre sincronizar y
 * {@code CatalogVersionService}, leer la version aplicada.
 *
 * <p><b>No expone nada para sincronizar, ni siquiera un atajo.</b> Es
 * deliberado: lo que consume esta fachada es un cliente que <i>pregunta</i>,
 * y un metodo para disparar una sincronizacion acá convertiria el endpoint de
 * estado en una operacion de escritura disfrazada. Quien tenga que
 * sincronizar usa {@code CatalogSyncService}.
 */
@Service
@RequiredArgsConstructor
public class SyncStatusService {

    private final GetSyncStatusUseCase getSyncStatus;

    /**
     * @return el estado de la sincronizacion en este momento. Nunca falla por
     *     el estado que informa: una copia local vacia o la catedra caida son
     *     contenido del reporte
     */
    public SyncStatusReport getStatus() {
        return getSyncStatus.getStatus();
    }
}

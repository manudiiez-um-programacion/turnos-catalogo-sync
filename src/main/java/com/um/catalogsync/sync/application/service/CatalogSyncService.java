package com.um.catalogsync.sync.application.service;

import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fachada de la sincronizacion completa.
 *
 * <p>Es la segunda fachada del slice, y la razon ya esta anotada en
 * {@code CatalogVersionService}: {@code sync} modela varios procesos
 * distintos y juntarlos en un unico {@code @Service} daria una clase que
 * crece sin criterio. Esta cubre correr una sincronizacion completa; la otra,
 * leer la version aplicada.
 *
 * <p>La usan quienes deciden <i>cuando</i> sincronizar: el arranque con base
 * vacia (#14), la deteccion de discontinuidad (#26) y, si se expone, la
 * operacion manual. Ninguno de ellos depende de {@code CatalogSnapshotPort}
 * ni de los repositorios.
 */
@Service
@RequiredArgsConstructor
public class CatalogSyncService {

    private final RunFullSyncUseCase runFullSync;
    private final ApplyCatalogSnapshotUseCase applySnapshot;

    /**
     * Corre una sincronizacion completa de punta a punta.
     *
     * @param trigger por que se corre, para que quede en el historial
     * @return la corrida ya cerrada
     */
    public SyncRun runFullSync(SyncTrigger trigger) {
        return runFullSync.run(trigger);
    }

    /**
     * Aplica un snapshot que ya se tiene, sin volver a pedirlo.
     *
     * <p>Se expone aparte porque la reconstruccion por discontinuidad (#26)
     * puede llegar con el snapshot ya en mano. <b>No registra corrida:</b>
     * quien lo use es responsable de su propio historial, y esta fachada no
     * se lo inventa.
     */
    public CatalogVersion applySnapshot(CatalogSnapshot snapshot) {
        return applySnapshot.apply(snapshot);
    }
}

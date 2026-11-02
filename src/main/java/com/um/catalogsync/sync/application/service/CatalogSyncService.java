package com.um.catalogsync.sync.application.service;

import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fachada de la sincronizacion: completa e incremental.
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
    private final ApplyIncrementalChangesUseCase applyIncremental;

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

    /**
     * Pone la copia local al dia aplicando las versiones que faltan, de a
     * una y en orden.
     *
     * <p><b>No decide entre incremental y completa.</b> Devuelve que paso, y
     * si el camino incremental no alcanzo lo dice en el resultado
     * ({@code needsFullSync}); elegir la estrategia es de #26 y dispararla,
     * de #27. Esta fachada expone las dos operaciones para que esa decision
     * tenga las dos a mano sin conocer ningun puerto de salida.
     *
     * <p><b>No registra corrida</b>, igual que {@link #applySnapshot}: quien
     * la use es responsable de su propio historial.
     *
     * @param targetVersion hasta donde llegar, normalmente la
     *     {@code currentVersion} que publica la catedra
     */
    public IncrementalSyncResult applyIncrementalUpTo(long targetVersion) {
        return applyIncremental.applyUpTo(targetVersion);
    }
}

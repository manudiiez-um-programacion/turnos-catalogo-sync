package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;

/**
 * Corre una sincronizacion completa de punta a punta: trae el snapshot de
 * la catedra, lo aplica y deja registrada la corrida.
 *
 * <p>Es la operacion que el enunciado evalua (§6.1). Quien decide
 * <i>cuando</i> correrla no es este puerto: eso es del arranque con base
 * vacia (#14) y de la deteccion de discontinuidad (#26), que son los que
 * pasan el {@link SyncTrigger}.
 */
public interface RunFullSyncUseCase {

    /**
     * @param trigger por que se corre, para que quede en el historial
     * @return la corrida ya cerrada, con la version a la que se llego
     * @throws RuntimeException la falla original, despues de haberla
     *     registrado. Se propaga a proposito: quien pidio la
     *     sincronizacion tiene que poder enterarse de que no se hizo, y el
     *     registro es para despues, no un reemplazo del error
     */
    SyncRun run(SyncTrigger trigger);
}

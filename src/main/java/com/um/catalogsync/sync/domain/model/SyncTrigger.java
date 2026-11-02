package com.um.catalogsync.sync.domain.model;

/**
 * Por que arranco una corrida de sincronizacion.
 *
 * <p>Es el "motivo" que el enunciado pide registrar, y el dato que hace
 * util el historial: dos corridas completas seguidas no significan lo mismo
 * si una fue por arranque con base vacia y la otra por discontinuidad
 * detectada. Son los cinco valores del CHECK
 * {@code ck_sync_run_triggered_by}.
 *
 * <p>Vocabulario propio: el contrato de la catedra no sabe por que
 * decidimos sincronizar.
 */
public enum SyncTrigger {

    /** Arranque del servicio con la base vacia (#14). */
    STARTUP,

    /** Llego un {@code CatalogUpdated} por Kafka (§15.3, #25). */
    EVENT,

    /** Chequeo periodico de la version contra Redis (#27). */
    RECONCILIATION,

    /**
     * Se detecto discontinuidad: la version local quedo por debajo de
     * {@code oldest-available-version} o no se pudo verificar el estado
     * (§18.2, #26).
     */
    DISCONTINUITY,

    /** La pidio una persona por la API de administracion. */
    MANUAL
}

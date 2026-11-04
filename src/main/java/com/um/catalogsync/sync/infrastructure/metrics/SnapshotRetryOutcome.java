package com.um.catalogsync.sync.infrastructure.metrics;

/**
 * El resultado de un intento de pedir el snapshot que ya habia fallado una
 * vez (issue #31). Etiqueta {@link SyncMetrics#SNAPSHOT_RETRIES}.
 *
 * <p>Vive en infraestructura y no en el dominio, a diferencia de las demas
 * etiquetas de {@code SyncMetrics}: no es un concepto de negocio, es un
 * detalle de como {@code RestCatalogSnapshotAdapter} habla por HTTP con la
 * catedra. Dos valores, cardinalidad acotada por construccion, igual que el
 * resto de las etiquetas de esta clase.
 */
public enum SnapshotRetryOutcome {

    /** Fallo transitorio, pero quedan intentos: se espera el backoff y se reintenta. */
    RETRIED,

    /** Se agotaron los intentos: se abandona y la corrida queda {@code FAILED}. */
    EXHAUSTED
}

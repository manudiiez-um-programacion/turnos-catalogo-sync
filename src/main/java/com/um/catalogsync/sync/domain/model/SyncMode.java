package com.um.catalogsync.sync.domain.model;

/**
 * Como se sincronizo: reemplazando todo o aplicando cambios.
 *
 * <p>Vocabulario propio de este servicio, no del contrato, igual que
 * {@link CatalogVersionSource}. Son los dos valores del CHECK
 * {@code ck_sync_run_mode}.
 *
 * <p><b>No es lo mismo que {@link CatalogVersionSource}</b>, aunque los
 * valores se parezcan y casi siempre coincidan. Este describe <i>que hizo
 * una corrida</i> y vive en {@code sync_run}, que guarda una fila por
 * intento, incluidos los que fallaron. El otro describe <i>como se llego a
 * la version que la copia local tiene hoy</i> y vive en
 * {@code catalog_version}, que solo cambia cuando una corrida termino bien.
 * Una corrida FULL que falla deja un {@code sync_run} con modo FULL y
 * ninguna huella en {@code catalog_version}.
 */
public enum SyncMode {

    /** Sincronizacion completa: snapshot REST aplicado entero (§6.1). */
    FULL,

    /** Sincronizacion incremental: cambios leidos de Redis (§6.2). */
    INCREMENTAL
}

package com.um.catalogsync.sync.domain.model;

/**
 * Como se llego a la version local vigente.
 *
 * <p>Es <b>vocabulario propio de este servicio</b>, no del contrato de la
 * catedra. Esa distincion importa y explica por que se trata distinto que
 * {@code DayOfWeek} del slice de horarios: ahi los siete valores los define
 * un sistema externo y un valor desconocido significa que el contrato
 * cambio; aca los dos valores los escribimos nosotros, y un valor que no
 * sea uno de estos dos solo puede venir de que alguien escribio en la tabla
 * por fuera de la aplicacion.
 *
 * <p>No es decorativo: es el dato que habilita la unica excepcion a la regla
 * de que la version no retrocede. Ver
 * {@link CatalogVersion#allowsAdvanceTo(long, CatalogVersionSource)}.
 */
public enum CatalogVersionSource {

    /**
     * Sincronizacion completa: se reemplazo la copia local con un snapshot
     * REST y la version local paso a ser su {@code snapshotVersion} (§6.1).
     */
    SNAPSHOT,

    /**
     * Sincronizacion incremental: se aplicaron en orden los cambios leidos
     * de Redis hasta una version nueva (§6.2).
     */
    INCREMENTAL
}

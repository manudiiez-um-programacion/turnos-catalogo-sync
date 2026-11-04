package com.um.catalogsync.sync.domain.model;

/**
 * Que paso con una notificacion de catalogo.
 *
 * <p>Existe porque el consumidor necesita distinguir dos finales que para
 * Kafka son iguales —los dos terminan confirmando el offset (§18.1)— pero
 * que para el log y para las pruebas no lo son: uno proceso algo y el otro
 * reconocio un duplicado. Sin este valor, la unica forma de saber cual fue
 * seria mirar la base.
 *
 * <p>No incluye un tercer valor para "fallo": una falla es una excepcion y
 * tiene que impedir la confirmacion del offset, porque el mensaje se tiene
 * que volver a entregar. Un enum con {@code FAILED} invitaria a devolverlo
 * y confirmar igual, que es exactamente lo que §16 prohibe.
 */
public enum CatalogUpdatedOutcome {

    /** El evento era nuevo y quedo registrado. */
    PROCESSED,

    /**
     * El {@code eventId} ya estaba registrado: no se repitio ningun efecto.
     *
     * <p>Es el resultado esperado de §18.1, no un error. La entrega es al
     * menos una vez, asi que esto va a pasar en operacion normal cada vez
     * que el servicio caiga entre persistir y confirmar el offset.
     */
    DUPLICATE
}

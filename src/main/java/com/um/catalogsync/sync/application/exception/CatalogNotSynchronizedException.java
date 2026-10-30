package com.um.catalogsync.sync.application.exception;

/**
 * Todavia no hay una version de catalogo aplicada.
 *
 * <p>Pasa con la base recien creada, antes de que termine la primera
 * sincronizacion completa (#14). En ese estado la copia local no es una copia
 * de nada: responder una agenda vacia seria mentir, y responder 404 le diria a
 * turnos que el profesional no existe cuando lo que no existe es la copia.
 *
 * <p>Vive en {@code sync} y no en el slice que la lanza: la version aplicada es
 * estado del proceso de sincronizacion, y la condicion la comparten todas las
 * consultas que se resuelven sobre la copia local —la agenda vigente (#18) y la
 * busqueda de profesionales (#15)—. Tenerla en el slice dueño del dato evita que
 * un slice importe la excepcion de otro que tampoco es su dueño.
 *
 * <p>Se traduce a 503 y no a 4xx porque la peticion de turnos es correcta: lo
 * que falta es un estado de este servicio, y se va a resolver solo. Es lo que
 * §13.1 de la referencia reserva para "dependencia requerida no disponible", y
 * le dice al cliente lo unico util: reintentar mas tarde.
 */
public class CatalogNotSynchronizedException extends RuntimeException {

    public CatalogNotSynchronizedException() {
        super(
                "No hay una version de catalogo aplicada todavia: la copia local no se"
                        + " puede considerar vigente");
    }
}

package com.um.catalogsync.sync.application.exception;

/**
 * No se pudo traer el snapshot porque la catedra no esta disponible, o
 * respondio algo que el contrato no prevee.
 *
 * <p>Es la excepcion de los problemas de infraestructura: no llegar al
 * servidor, un timeout, un 5xx, un 503 por una dependencia caida (§13.1) o
 * un cuerpo que no se pudo deserializar. Lo que tienen en comun es que no
 * dependen de nosotros y que <b>vale reintentar mas tarde</b>: eso es lo que
 * la distingue de las otras cuatro, donde reintentar no cambiaria nada.
 *
 * <p>Como la de {@code integration}, el mensaje nombra ZeroTier: el servidor
 * no esta en internet y se llega por una red privada (D6), asi que no estar
 * en la red es la causa mas comun de verla y es el primer lugar donde
 * mirar.
 *
 * <p><b>Esta si encadena la causa.</b> Un error de conexion o un 5xx no
 * tienen un cuerpo que pueda contener el JWT: el pedido del snapshot no
 * lleva body, solo la cabecera {@code Authorization}, y la traza aporta el
 * detalle que hace falta —timeout, host inalcanzable— sin arrastrar el
 * secreto.
 */
public class CatalogSnapshotUnavailableException extends RuntimeException {

    public CatalogSnapshotUnavailableException(String ruta, Throwable causa) {
        super("No se pudo completar " + ruta + " contra la API de la catedra."
                + " El servidor se alcanza solo por la red privada ZeroTier:"
                + " verificar que la maquina este unida a la red y que"
                + " CATEDRA_API_BASE_URL apunte a la IP asignada, sin /api al final",
                causa);
    }

    public CatalogSnapshotUnavailableException(String mensaje) {
        super(mensaje);
    }
}

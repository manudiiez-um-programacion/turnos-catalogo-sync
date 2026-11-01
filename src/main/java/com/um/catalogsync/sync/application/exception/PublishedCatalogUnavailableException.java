package com.um.catalogsync.sync.application.exception;

/**
 * No se pudo leer el catalogo publicado porque el Redis de la catedra no
 * esta disponible.
 *
 * <p>Es la hermana de {@code CatalogSnapshotUnavailableException} para el
 * otro camino de lectura: no llegar al servidor, un timeout de conexion o de
 * comando, la conexion cortada a mitad, o la ACL rechazando la operacion.
 * Lo que tienen en comun es que no dependen de nosotros y que <b>vale
 * reintentar mas tarde</b>, o caer al snapshot completo, que es lo que §18.2
 * manda cuando el estado no se puede verificar.
 *
 * <p>El mensaje nombra ZeroTier por el mismo motivo que las de la API: el
 * Redis de la catedra tampoco esta en internet y corre en la misma IP
 * privada que la API, en otro puerto (D6). No estar en la red es la causa
 * mas comun de ver esta excepcion.
 *
 * <p><b>Encadena la causa pero nunca los datos de conexion.</b> La traza de
 * Lettuce aporta el detalle que importa —host inalcanzable, timeout, comando
 * denegado— y no contiene la clave: el usuario y la clave viajan en el
 * {@code AUTH} del saludo, no en el comando que fallo. El mensaje propio no
 * agrega host ni usuario: la direccion privada del servidor y el nombre del
 * usuario de Redis vienen de {@code integration} y no tienen por que
 * terminar en un log.
 */
public class PublishedCatalogUnavailableException extends RuntimeException {

    public PublishedCatalogUnavailableException(String operacion, Throwable causa) {
        super("No se pudo leer " + operacion + " del Redis de la catedra."
                + " El servidor se alcanza solo por la red privada ZeroTier:"
                + " verificar que la maquina este unida a la red y que la"
                + " configuracion de integracion traiga redisHost y redisPort"
                + " correctos (§14.1)",
                causa);
    }

    public PublishedCatalogUnavailableException(String mensaje) {
        super(mensaje);
    }
}

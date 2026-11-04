package com.um.catalogsync.sync.application.exception;

/**
 * Llego un mensaje por el topic de catalogo que no es un
 * {@code CatalogUpdated} valido (§15.3).
 *
 * <p>Cubre un JSON que no se puede parsear, un campo obligatorio ausente, un
 * {@code schemaVersion} que no es {@code 1} y un {@code eventType} que no es
 * el del topic.
 *
 * <p><b>Por que importa que sea su propia excepcion.</b> Es la unica falla
 * del consumidor que <b>no</b> se arregla reintentando: el mismo mensaje va
 * a fallar igual la proxima vez y las siguientes. Si se la tratara como a
 * cualquier otra —no confirmar el offset y volver a pedirlo— la particion
 * quedaria trabada en ese mensaje para siempre, y con ella todas las
 * notificaciones posteriores. El consumidor la distingue justamente para
 * saltearlo dejando constancia, en lugar de bloquearse. Una falla de base o
 * de red es lo contrario: transitoria, y ahi si hay que reintentar.
 *
 * <p><b>No incluye el contenido del mensaje.</b> Nombra el topic, la
 * particion, el offset y el campo que falla, que es con lo que se encuentra
 * el mensaje del otro lado. El contenido de un evento de catalogo no es
 * secreto, pero volcarlo en cada traza convierte un problema de contrato en
 * ruido.
 */
public class MalformedCatalogEventException extends RuntimeException {

    public MalformedCatalogEventException(String detalle) {
        super("El mensaje del topic de catalogo no cumple el contrato de §15.3: " + detalle);
    }

    public MalformedCatalogEventException(String detalle, Throwable causa) {
        super("El mensaje del topic de catalogo no cumple el contrato de §15.3: " + detalle,
                causa);
    }
}

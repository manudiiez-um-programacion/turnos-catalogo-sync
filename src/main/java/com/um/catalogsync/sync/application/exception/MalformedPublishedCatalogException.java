package com.um.catalogsync.sync.application.exception;

/**
 * Redis respondio, pero lo que trajo no cumple el contrato de §14.
 *
 * <p>Cubre tres casos: una clave de version que no es un numero decimal, el
 * hash de metadata al que le falta un campo, y el JSON de una entidad que no
 * se puede parsear o al que le falta un campo obligatorio.
 *
 * <p><b>Por que no es lo mismo que una clave ausente.</b> Una clave que no
 * existe es un estado legitimo del origen —la catedra todavia no publico
 * nada— y quien lee decide que hacer con eso. Un valor que existe y no se
 * entiende es un incumplimiento del contrato: seguir adelante con el
 * significaria aplicar a la copia local datos que no se pudieron verificar.
 * Esa distincion es la razon por la que el puerto devuelve
 * {@link java.util.Optional} en lugar de tirar esta excepcion cuando no hay
 * nada.
 *
 * <p><b>No incluye el valor completo.</b> Nombra la clave, el field y el
 * campo que falta, que es lo que localiza el problema del otro lado. Volcar
 * el JSON entero —o peor, el hash entero— en una traza seria arrastrar el
 * catalogo a los logs por un campo faltante.
 */
public class MalformedPublishedCatalogException extends RuntimeException {

    public MalformedPublishedCatalogException(String detalle) {
        super("El catalogo publicado en Redis no cumple el contrato de §14: " + detalle);
    }

    public MalformedPublishedCatalogException(String detalle, Throwable causa) {
        super("El catalogo publicado en Redis no cumple el contrato de §14: " + detalle, causa);
    }
}

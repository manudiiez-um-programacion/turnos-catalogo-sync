package com.um.catalogsync.sync.application.exception;

/**
 * La catedra respondio 401 al pedido del snapshot: el JWT tecnico no sirve.
 *
 * <p><b>Reautenticarse puede resolverlo y vale intentarlo una vez.</b> Es la
 * misma distincion que hace el slice {@code integration} entre un token
 * rechazado y unas credenciales rechazadas, y es lo que decide la diferencia
 * con {@link CatalogSnapshotForbiddenException}: un 401 dice "no te
 * reconozco", y el token se renueva; un 403 dice "te reconozco y no podes",
 * y renovarlo da el mismo resultado. Quien aplica esa politica es la
 * sincronizacion completa (#14), no el adaptador.
 *
 * <p>El {@code code} entra al mensaje porque §13 dice que los clientes
 * deciden por {@code status} y {@code code}; puede venir vacio, porque los
 * rechazos que produce Spring Security directamente no lo traen (§13), y el
 * mensaje lo dice en lugar de imprimir {@code null}.
 *
 * <p>No encadena la causa: la excepcion HTTP de Spring arrastra el cuerpo de
 * la respuesta y el pedido llevaba el JWT en una cabecera.
 */
public class CatalogSnapshotUnauthorizedException extends RuntimeException {

    public CatalogSnapshotUnauthorizedException(String ruta, String code) {
        super("La catedra respondio 401 a " + ruta
                + " (code=" + (code == null ? "sin code" : code) + "):"
                + " el JWT tecnico esta vencido o revocado");
    }
}

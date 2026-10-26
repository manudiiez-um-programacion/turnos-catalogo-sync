package com.um.catalogsync.sync.application.exception;

/**
 * La catedra respondio 403 al pedido del snapshot por un motivo que no es la
 * falta de integracion tecnica.
 *
 * <p>El caso tipico es un JWT sin {@code ROLE_STUDENT_CLIENT}: §6 dice que
 * todos los endpoints menos el registro y el login lo exigen. Tambien cubre
 * cualquier 403 futuro con un {@code code} que todavia no conocemos, que es
 * justamente por lo que existe y no se lo mete en el cajon de
 * "infraestructura": un 403 no es una caida y reintentar no lo arregla.
 *
 * <p><b>Reautenticarse no sirve</b>, y es lo que la separa de
 * {@link CatalogSnapshotUnauthorizedException}: el token es valido y el
 * servidor lo reconocio. Lo que falta es un permiso, y eso se arregla del
 * lado de la cuenta, no pidiendo otro token.
 *
 * <p>El caso documentado de 403 —{@code STUDENT_INTEGRATION_NOT_FOUND}
 * (§13.1)— tiene su propia excepcion, {@link TechnicalIntegrationMissingException},
 * porque lo accionable es distinto.
 */
public class CatalogSnapshotForbiddenException extends RuntimeException {

    public CatalogSnapshotForbiddenException(String ruta, String code) {
        super("La catedra respondio 403 a " + ruta
                + " (code=" + (code == null ? "sin code" : code) + "):"
                + " el JWT tecnico es valido pero no habilita la operacion."
                + " §6 exige ROLE_STUDENT_CLIENT en todos los endpoints salvo"
                + " registro y login; reautenticarse no cambia el resultado");
    }
}

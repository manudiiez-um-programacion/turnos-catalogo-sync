package com.um.catalogsync.integration.domain.model;

/**
 * Llego un {@code provisioningStatus} que no es uno de los cuatro del
 * contrato (§5.1).
 *
 * <p>Vive en {@code domain/model} por el mismo motivo que
 * {@code UnknownDayOfWeekException}: la lanza
 * {@link ProvisioningStatus#fromContract(String)}, que es dominio, y el
 * dominio no puede importar {@code application}.
 *
 * <p>El valor recibido se guarda y se entrecomilla en el mensaje porque es
 * un dato del contrato, no un secreto: sirve para diagnosticar y no hay
 * nada sensible en el.
 */
public class UnknownProvisioningStatusException extends RuntimeException {

    private final String valorRecibido;

    public UnknownProvisioningStatusException(String valorRecibido) {
        super("El estado de aprovisionamiento " + describir(valorRecibido)
                + " no es uno de los cuatro del contrato"
                + " (PENDING, PROVISIONED, FAILED, REVOKED)");
        this.valorRecibido = valorRecibido;
    }

    /** El valor tal como llego, {@code null} incluido, para diagnosticar. */
    public String getValorRecibido() {
        return valorRecibido;
    }

    private static String describir(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }
}

package com.um.catalogsync.professional.application.exception;

/**
 * El profesional pedido no esta en la copia local.
 *
 * <p>La lanza el facade, no los puertos: ellos devuelven {@code Optional}.
 */
public class ProfessionalNotFoundException extends RuntimeException {

    private final Long professionalId;

    public ProfessionalNotFoundException(Long professionalId) {
        super("No hay un profesional con id " + professionalId
                + " en la copia local del catalogo");
        this.professionalId = professionalId;
    }

    public Long getProfessionalId() {
        return professionalId;
    }
}

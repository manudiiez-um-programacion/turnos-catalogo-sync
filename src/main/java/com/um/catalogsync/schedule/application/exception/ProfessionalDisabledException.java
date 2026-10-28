package com.um.catalogsync.schedule.application.exception;

/**
 * El profesional esta en la copia local pero deshabilitado.
 *
 * <p><b>Por que es un error distinto de "no existe".</b> Lo pide #18 y lo
 * necesita el servicio de turnos para decidir: con un profesional inexistente
 * no hay nada que hacer; con uno deshabilitado tiene que <b>rechazar una
 * operacion nueva</b> pero puede seguir mostrando las reservas ya hechas con
 * los datos historicos que guardo localmente (§4.2). Un unico 404 para los dos
 * casos lo obligaria a adivinar.
 */
public class ProfessionalDisabledException extends RuntimeException {

    private final Long professionalId;

    public ProfessionalDisabledException(Long professionalId) {
        super(
                "El profesional " + professionalId
                        + " esta deshabilitado en el catalogo: no tiene agenda vigente");
        this.professionalId = professionalId;
    }

    public Long getProfessionalId() {
        return professionalId;
    }
}

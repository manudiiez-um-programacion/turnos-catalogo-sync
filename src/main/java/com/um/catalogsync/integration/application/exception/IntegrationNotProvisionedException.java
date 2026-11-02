package com.um.catalogsync.integration.application.exception;

import com.um.catalogsync.integration.domain.model.ProvisioningStatus;

/**
 * La cuenta tecnica no esta habilitada: {@code provisioningStatus} es algo
 * distinto de {@code PROVISIONED} (§5.1).
 *
 * <p><b>Por que esto detiene el arranque.</b> Sin {@code PROVISIONED} la
 * catedra no entrega {@code redisPassword}, y sin esa clave este servicio no
 * puede leer los cambios incrementales. Arrancar igual daria un servicio que
 * parece sano y falla en la primera sincronizacion con un error de
 * autenticacion de Redis, lejos de la causa. Es mejor no arrancar y decir
 * por que.
 *
 * <p>El mensaje nombra el estado y el {@code groupId}, que son datos del
 * contrato, y nada mas. El secreto no se menciona: en estos estados ni
 * siquiera llego.
 *
 * <p>Que hacer segun el estado: con {@code PENDING}, esperar a que la
 * catedra termine de habilitar la cuenta. Con {@code FAILED} o
 * {@code REVOKED}, dar de alta otra cuenta tecnica e informar a la catedra
 * (§5.4).
 */
public class IntegrationNotProvisionedException extends RuntimeException {

    private final ProvisioningStatus estado;

    public IntegrationNotProvisionedException(ProvisioningStatus estado, String groupId) {
        super("La cuenta tecnica de la integracion \"" + groupId + "\" esta en estado "
                + estado + " y no en PROVISIONED:"
                + " la catedra todavia no entrego las credenciales de Redis."
                + " Con PENDING hay que esperar el alta; con FAILED o REVOKED hay que"
                + " crear otra cuenta tecnica e informar a la catedra (§5.4)");
        this.estado = estado;
    }

    public ProvisioningStatus getEstado() {
        return estado;
    }
}

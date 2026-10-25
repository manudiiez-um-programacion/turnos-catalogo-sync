package com.um.catalogsync.integration.domain.ports.in;

import com.um.catalogsync.shared.domain.model.Secret;

/**
 * El JWT tecnico, para quien tenga que llamar a la catedra.
 *
 * <p>Es el puerto que el ADR anuncia: "el JWT tecnico lo provee el slice
 * {@code integration} a traves de un puerto que el cliente REST consume;
 * ningun otro slice lo conoce". El cliente del snapshot (#12) va a depender
 * de esta interfaz y no de como se obtiene el token, de donde salen las
 * credenciales ni de si estaba en cache.
 */
public interface GetTechnicalTokenUseCase {

    /**
     * @return el JWT tecnico vigente, autenticandose si todavia no hay uno
     */
    Secret get();
}

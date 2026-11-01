package com.um.catalogsync.integration.domain.ports.out;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.shared.domain.model.Secret;

/**
 * Puerto de salida de la configuracion vigente:
 * {@code GET /api/student/integration} (§5.3).
 *
 * <p><b>Para que sirve si el login ya la trae.</b> Para releerla sin gastar
 * un login. El JWT dura un año y la configuracion puede cambiar antes
 * —la catedra reaprovisiona una cuenta, un estado pasa de {@code PENDING} a
 * {@code PROVISIONED}—, asi que el refresco explicito que pide el issue usa
 * este camino: mismo token, configuracion nueva.
 *
 * <p>Si el toma el token por parametro es deliberado: el adaptador no decide
 * cual usar ni lo guarda. Quien administra la sesion es la capa
 * {@code application}, que es la que sabe si hay que reautenticarse.
 */
public interface IntegrationConfigPort {

    /**
     * Lee la configuracion vigente con el token indicado.
     *
     * @param technicalToken el JWT tecnico que va en {@code Authorization}
     * @return la configuracion que informa la catedra
     * @throws com.um.catalogsync.integration.application.exception.TechnicalTokenRejectedException
     *     si la catedra responde 401: el token no sirve y hay que volver a
     *     autenticarse
     * @throws com.um.catalogsync.integration.application.exception.CatedraIntegrationUnavailableException
     *     si no se pudo hablar con la catedra o respondio algo inesperado
     */
    IntegrationConfig fetchCurrent(Secret technicalToken);
}

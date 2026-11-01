package com.um.catalogsync.integration.application.exception;

import java.util.List;

/**
 * La catedra dijo {@code PROVISIONED} pero la configuracion llego sin
 * campos que este servicio necesita.
 *
 * <p>No deberia pasar segun el contrato, y justamente por eso tiene
 * excepcion propia en lugar de confundirse con
 * {@link IntegrationNotProvisionedException}: un estado que no es
 * {@code PROVISIONED} es una situacion prevista y se resuelve esperando o
 * dando de alta otra cuenta; esto otro es una respuesta que no cumple lo que
 * promete, y se resuelve hablando con la catedra. Mezclarlas mandaria a
 * revisar el alta cuando el problema es la respuesta.
 *
 * <p><b>El mensaje lleva nombres de campos, nunca valores.</b> La lista
 * viene de {@code IntegrationConfig.missingRequiredValues()}, que por
 * construccion solo devuelve nombres, asi que no hay forma de que un secreto
 * termine aca.
 */
public class IncompleteIntegrationConfigException extends RuntimeException {

    private final List<String> camposFaltantes;

    public IncompleteIntegrationConfigException(String groupId, List<String> camposFaltantes) {
        super("La configuracion de integracion de \"" + groupId + "\" dice PROVISIONED"
                + " pero le faltan campos que este servicio necesita: "
                + String.join(", ", camposFaltantes));
        this.camposFaltantes = List.copyOf(camposFaltantes);
    }

    public List<String> getCamposFaltantes() {
        return camposFaltantes;
    }
}

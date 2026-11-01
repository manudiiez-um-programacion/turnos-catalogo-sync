package com.um.catalogsync.integration.domain.ports.in;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;

/**
 * La configuracion de integracion vigente, de donde salen los topics, el
 * consumer group y los datos de Redis.
 *
 * <p>La van a consumir los adaptadores de Redis (#23) y de Kafka (#22), que
 * no pueden tener esos valores fijos en el codigo.
 */
public interface GetIntegrationConfigUseCase {

    /**
     * @return la configuracion vigente, leyendola de la catedra si todavia
     *     no se habia leido
     */
    IntegrationConfig get();
}

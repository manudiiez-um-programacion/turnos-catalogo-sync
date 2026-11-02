package com.um.catalogsync.integration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * El objeto {@code integration} tal como lo manda la catedra (§5.3).
 *
 * <p>Es la frontera: los nombres y los tipos son los del contrato, no los
 * del dominio. Por eso {@code provisioningStatus} entra como {@code String}
 * —lo traduce el mapper— y {@code redisPassword} como {@code String}, que es
 * el unico lugar del codigo donde el secreto viaja sin envoltorio: lo envuelve
 * el mapper, inmediatamente despues.
 *
 * <p>{@code ignoreUnknown} porque el contrato puede sumar campos
 * compatibles con v1 y este servicio tiene que seguir funcionando (§4). Va
 * anotado en el DTO en lugar de confiar en la configuracion global de
 * Jackson: el {@code RestClient} de este slice se arma con los conversores
 * por defecto y no con el {@code ObjectMapper} que configura Spring Boot,
 * asi que la tolerancia tiene que estar escrita donde no dependa de eso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntegrationDto(
        String groupId,
        String redisHost,
        int redisPort,
        String redisUsername,
        String redisPassword,
        String redisReadNamespace,
        String redisWriteNamespace,
        String kafkaBootstrapServers,
        String kafkaConsumerGroupId,
        String kafkaCatalogTopic,
        String kafkaAppointmentActionsTopic,
        String kafkaAppointmentPhoneTopic,
        String provisioningStatus) {

    /** Sin {@code redisPassword} ni la direccion privada del servidor. */
    @Override
    public String toString() {
        return "IntegrationDto(groupId=" + groupId
                + ", provisioningStatus=" + provisioningStatus
                + ", redisPassword=" + (redisPassword == null ? "ausente" : "***")
                + ")";
    }
}

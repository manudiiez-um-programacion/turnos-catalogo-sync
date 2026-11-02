package com.um.catalogsync.integration.infrastructure.client.mapper;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.infrastructure.client.dto.IntegrationDto;
import com.um.catalogsync.shared.domain.model.Secret;
import org.springframework.stereotype.Component;

/**
 * Traduce el {@code integration} del contrato al modelo de dominio.
 *
 * <p>Es el borde donde el DTO deja de existir: nada de
 * {@code infrastructure} sigue hacia adentro. Y es donde el secreto se
 * envuelve —{@link Secret#ofNullable} y no {@code of}, porque
 * {@code redisPassword} solo viene cuando el estado es {@code PROVISIONED}
 * (§5.1) y su ausencia en los demas estados no es un error de parseo: la
 * decision de si eso es aceptable es de la capa de aplicacion, que sabe en
 * que estado esta la cuenta.
 *
 * <p>Clase {@code @Component} y con guarda de {@code null} en la entrada,
 * como los mappers de persistencia del proyecto.
 */
@Component
public class IntegrationConfigMapper {

    /**
     * @param dto lo que vino en el JSON
     * @return el modelo de dominio, o {@code null} si no vino el objeto
     * @throws com.um.catalogsync.integration.domain.model.UnknownProvisioningStatusException
     *     si {@code provisioningStatus} no es uno de los cuatro del contrato
     */
    public IntegrationConfig toDomain(IntegrationDto dto) {
        if (dto == null) {
            return null;
        }
        return IntegrationConfig.builder()
                .groupId(dto.groupId())
                .redisHost(dto.redisHost())
                .redisPort(dto.redisPort())
                .redisUsername(dto.redisUsername())
                .redisPassword(Secret.ofNullable(dto.redisPassword()))
                .redisReadNamespace(dto.redisReadNamespace())
                .redisWriteNamespace(dto.redisWriteNamespace())
                .kafkaBootstrapServers(dto.kafkaBootstrapServers())
                .kafkaConsumerGroupId(dto.kafkaConsumerGroupId())
                .kafkaCatalogTopic(dto.kafkaCatalogTopic())
                .kafkaAppointmentActionsTopic(dto.kafkaAppointmentActionsTopic())
                .kafkaAppointmentPhoneTopic(dto.kafkaAppointmentPhoneTopic())
                .provisioningStatus(ProvisioningStatus.fromContract(dto.provisioningStatus()))
                .build();
    }
}

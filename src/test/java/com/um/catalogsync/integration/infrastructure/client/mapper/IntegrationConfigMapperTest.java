package com.um.catalogsync.integration.infrastructure.client.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.domain.model.UnknownProvisioningStatusException;
import com.um.catalogsync.integration.infrastructure.client.dto.IntegrationDto;
import com.um.catalogsync.shared.domain.model.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegrationConfigMapperTest {

    private final IntegrationConfigMapper mapper = new IntegrationConfigMapper();

    private static IntegrationDto dto(String provisioningStatus, String redisPassword) {
        return new IntegrationDto(
                IntegrationFixtures.GROUP_ID,
                "redis.catedra.internal",
                6379,
                "grp_" + IntegrationFixtures.GROUP_ID,
                redisPassword,
                "catedra:sync:*",
                "alumnos:" + IntegrationFixtures.GROUP_ID + ":*",
                "kafka.catedra.internal:9092",
                "alumnos-" + IntegrationFixtures.GROUP_ID,
                "catedra.catalog." + IntegrationFixtures.GROUP_ID,
                "alumnos.turnos.acciones." + IntegrationFixtures.GROUP_ID,
                "catedra.turnos.telefono." + IntegrationFixtures.GROUP_ID,
                provisioningStatus);
    }

    @Test
    @DisplayName("Mapea los trece campos del contrato")
    void mapeaTodosLosCampos() {
        IntegrationConfig config =
                mapper.toDomain(dto("PROVISIONED", IntegrationFixtures.REDIS_PASSWORD_FALSA));

        assertThat(config.getGroupId()).isEqualTo(IntegrationFixtures.GROUP_ID);
        assertThat(config.getRedisHost()).isEqualTo("redis.catedra.internal");
        assertThat(config.getRedisPort()).isEqualTo(6379);
        assertThat(config.getRedisUsername()).isEqualTo("grp_" + IntegrationFixtures.GROUP_ID);
        assertThat(config.getRedisPassword())
                .isEqualTo(Secret.of(IntegrationFixtures.REDIS_PASSWORD_FALSA));
        assertThat(config.getRedisReadNamespace()).isEqualTo("catedra:sync:*");
        assertThat(config.getRedisWriteNamespace())
                .isEqualTo("alumnos:" + IntegrationFixtures.GROUP_ID + ":*");
        assertThat(config.getKafkaBootstrapServers()).isEqualTo("kafka.catedra.internal:9092");
        assertThat(config.getKafkaConsumerGroupId())
                .isEqualTo("alumnos-" + IntegrationFixtures.GROUP_ID);
        assertThat(config.getKafkaCatalogTopic())
                .isEqualTo("catedra.catalog." + IntegrationFixtures.GROUP_ID);
        assertThat(config.getKafkaAppointmentActionsTopic())
                .isEqualTo("alumnos.turnos.acciones." + IntegrationFixtures.GROUP_ID);
        assertThat(config.getKafkaAppointmentPhoneTopic())
                .isEqualTo("catedra.turnos.telefono." + IntegrationFixtures.GROUP_ID);
        assertThat(config.getProvisioningStatus()).isEqualTo(ProvisioningStatus.PROVISIONED);
    }

    @Test
    @DisplayName("La clave ausente se mapea a null y no a un secreto vacio")
    void laClaveAusenteQuedaEnNull() {
        IntegrationConfig config = mapper.toDomain(dto("PENDING", null));

        assertThat(config.getRedisPassword()).isNull();
        assertThat(config.getProvisioningStatus()).isEqualTo(ProvisioningStatus.PENDING);
    }

    @Test
    @DisplayName("Un estado que no es del contrato detiene el mapeo")
    void unEstadoDesconocidoFalla() {
        assertThatThrownBy(() -> mapper.toDomain(dto("HABILITADA", null)))
                .isInstanceOf(UnknownProvisioningStatusException.class);
    }

    @Test
    @DisplayName("Un objeto ausente se mapea a null, como los mappers de persistencia")
    void toleraLaEntradaNula() {
        assertThat(mapper.toDomain(null)).isNull();
    }
}

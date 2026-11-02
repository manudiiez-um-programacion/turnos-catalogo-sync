package com.um.catalogsync.integration.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.integration.IntegrationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IntegrationConfigTest {

    @Test
    @DisplayName("PROVISIONED es el unico estado usable")
    void soloProvisionedEsUsable() {
        assertThat(IntegrationFixtures.provisionada().isProvisioned()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ProvisioningStatus.class, names = "PROVISIONED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Los otros tres estados no son usables")
    void losOtrosEstadosNoSonUsables(ProvisioningStatus estado) {
        assertThat(IntegrationFixtures.conEstado(estado).isProvisioned()).isFalse();
    }

    @Test
    @DisplayName("Una configuracion completa no tiene campos faltantes")
    void laConfiguracionCompletaNoTieneFaltantes() {
        assertThat(IntegrationFixtures.provisionada().missingRequiredValues()).isEmpty();
    }

    @Test
    @DisplayName("Informa los campos que este servicio necesita y no llegaron")
    void informaLosCamposQueFaltan() {
        IntegrationConfig incompleta = IntegrationConfig.builder()
                .provisioningStatus(ProvisioningStatus.PROVISIONED)
                .build();

        assertThat(incompleta.missingRequiredValues())
                .containsExactlyInAnyOrder(
                        "groupId",
                        "redisHost",
                        "redisPort",
                        "redisUsername",
                        "redisPassword",
                        "redisReadNamespace",
                        "redisWriteNamespace",
                        "kafkaBootstrapServers",
                        "kafkaConsumerGroupId",
                        "kafkaCatalogTopic");
    }

    @Test
    @DisplayName("Los topics del servicio de turnos no se exigen: este servicio no los usa")
    void noExigeLosTopicsDeTurnos() {
        IntegrationConfig sinTopicsDeTurnos = IntegrationFixtures.provisionada();

        // La fixture los trae; lo que se verifica es que no esten en la
        // lista de necesarios aunque falten.
        IntegrationConfig recortada = IntegrationConfig.builder()
                .groupId(sinTopicsDeTurnos.getGroupId())
                .redisHost(sinTopicsDeTurnos.getRedisHost())
                .redisPort(sinTopicsDeTurnos.getRedisPort())
                .redisUsername(sinTopicsDeTurnos.getRedisUsername())
                .redisPassword(sinTopicsDeTurnos.getRedisPassword())
                .redisReadNamespace(sinTopicsDeTurnos.getRedisReadNamespace())
                .redisWriteNamespace(sinTopicsDeTurnos.getRedisWriteNamespace())
                .kafkaBootstrapServers(sinTopicsDeTurnos.getKafkaBootstrapServers())
                .kafkaConsumerGroupId(sinTopicsDeTurnos.getKafkaConsumerGroupId())
                .kafkaCatalogTopic(sinTopicsDeTurnos.getKafkaCatalogTopic())
                .provisioningStatus(ProvisioningStatus.PROVISIONED)
                .build();

        assertThat(recortada.missingRequiredValues()).isEmpty();
    }

    @Test
    @DisplayName("Con la cuenta sin habilitar, el faltante no es redisPassword")
    void sinHabilitarNoSeReclamaLaClave() {
        IntegrationConfig pendiente = IntegrationFixtures.conEstado(ProvisioningStatus.PENDING);

        // El contrato no entrega la clave en PENDING (§5.1): reclamarla
        // mandaria a buscar el problema al lugar equivocado.
        assertThat(pendiente.missingRequiredValues()).doesNotContain("redisPassword").isEmpty();
    }

    @Test
    @DisplayName("Un puerto fuera de rango cuenta como faltante")
    void unPuertoInvalidoEsUnFaltante() {
        IntegrationConfig conPuertoCero = IntegrationConfig.builder()
                .groupId(IntegrationFixtures.GROUP_ID)
                .redisPort(0)
                .provisioningStatus(ProvisioningStatus.PENDING)
                .build();

        assertThat(conPuertoCero.missingRequiredValues()).contains("redisPort");
    }

    @Test
    @DisplayName("toString no menciona la clave de Redis ni el host privado")
    void toStringNoFiltraNada() {
        String texto = IntegrationFixtures.provisionada().toString();

        assertThat(texto)
                .doesNotContain(IntegrationFixtures.REDIS_PASSWORD_FALSA)
                .doesNotContain("redisPassword")
                .doesNotContain("redis.catedra.internal")
                .contains(IntegrationFixtures.GROUP_ID);
    }
}

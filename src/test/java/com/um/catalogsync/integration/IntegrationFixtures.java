package com.um.catalogsync.integration;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.domain.model.TechnicalSession;
import com.um.catalogsync.shared.domain.model.Secret;

/**
 * Datos de prueba del slice {@code integration}, copiados de los ejemplos
 * de §5.1 y §5.3 de la referencia.
 *
 * <p>Se usan los valores del documento y no inventados: asi las pruebas de
 * los adaptadores verifican el parseo contra exactamente la forma que la
 * catedra documenta, incluido el {@code id_token} en snake_case.
 *
 * <p>Los dos secretos son falsos y estan marcados como tales en su propio
 * texto. Son los valores que busca {@code SecretosNoSeFiltranTest}: si
 * alguno aparece en un log, en un JSON o en el mensaje de una excepcion, esa
 * prueba falla. Por eso tienen que ser cadenas raras y faciles de buscar, y
 * por eso no se reusan para nada mas.
 */
public final class IntegrationFixtures {

    public static final String GROUP_ID = "proyecto-juan-perez";

    /** JWT falso. Nunca debe aparecer escrito en ninguna parte. */
    public static final String TOKEN_FALSO = "jwt-falso-de-prueba-no-debe-aparecer-nunca";

    /** Clave de Redis falsa. Nunca debe aparecer escrita en ninguna parte. */
    public static final String REDIS_PASSWORD_FALSA =
            "clave-redis-falsa-no-debe-aparecer-nunca";

    public static final String USUARIO_TECNICO = "juan.perez";
    public static final String CLAVE_TECNICA = "clave-tecnica-falsa-no-debe-aparecer-nunca";

    private IntegrationFixtures() {
    }

    /** Configuracion completa y habilitada: el caso feliz. */
    public static IntegrationConfig provisionada() {
        return conEstado(ProvisioningStatus.PROVISIONED);
    }

    /**
     * La misma configuracion en el estado que se pida.
     *
     * <p>Con un estado distinto de {@code PROVISIONED} se omite
     * {@code redisPassword}, que es lo que hace el contrato (§5.1): la clave
     * solo se entrega cuando la cuenta esta habilitada.
     */
    public static IntegrationConfig conEstado(ProvisioningStatus estado) {
        return IntegrationConfig.builder()
                .groupId(GROUP_ID)
                .redisHost("redis.catedra.internal")
                .redisPort(6379)
                .redisUsername("grp_" + GROUP_ID)
                .redisPassword(estado == ProvisioningStatus.PROVISIONED
                        ? Secret.of(REDIS_PASSWORD_FALSA)
                        : null)
                .redisReadNamespace("catedra:sync:*")
                .redisWriteNamespace("alumnos:" + GROUP_ID + ":*")
                .kafkaBootstrapServers("kafka.catedra.internal:9092")
                .kafkaConsumerGroupId("alumnos-" + GROUP_ID)
                .kafkaCatalogTopic("catedra.catalog." + GROUP_ID)
                .kafkaAppointmentActionsTopic("alumnos.turnos.acciones." + GROUP_ID)
                .kafkaAppointmentPhoneTopic("catedra.turnos.telefono." + GROUP_ID)
                .provisioningStatus(estado)
                .build();
    }

    public static TechnicalSession sesion() {
        return new TechnicalSession(Secret.of(TOKEN_FALSO), provisionada());
    }

    /** El objeto {@code integration} del contrato, en JSON (§5.3). */
    public static String integrationJson(String provisioningStatus) {
        String clave = "PROVISIONED".equals(provisioningStatus)
                ? "  \"redisPassword\": \"" + REDIS_PASSWORD_FALSA + "\",\n"
                : "";
        return """
                {
                  "groupId": "%s",
                  "redisHost": "redis.catedra.internal",
                  "redisPort": 6379,
                  "redisUsername": "grp_%s",
                %s  "redisReadNamespace": "catedra:sync:*",
                  "redisWriteNamespace": "alumnos:%s:*",
                  "kafkaBootstrapServers": "kafka.catedra.internal:9092",
                  "kafkaConsumerGroupId": "alumnos-%s",
                  "kafkaCatalogTopic": "catedra.catalog.%s",
                  "kafkaAppointmentActionsTopic": "alumnos.turnos.acciones.%s",
                  "kafkaAppointmentPhoneTopic": "catedra.turnos.telefono.%s",
                  "provisioningStatus": "%s"
                }
                """.formatted(GROUP_ID, GROUP_ID, clave, GROUP_ID, GROUP_ID, GROUP_ID,
                GROUP_ID, GROUP_ID, provisioningStatus);
    }

    /** La respuesta del login tecnico: {@code id_token} + {@code integration} (§5.2). */
    public static String loginJson(String provisioningStatus) {
        return """
                {
                  "id_token": "%s",
                  "integration": %s
                }
                """.formatted(TOKEN_FALSO, integrationJson(provisioningStatus));
    }
}

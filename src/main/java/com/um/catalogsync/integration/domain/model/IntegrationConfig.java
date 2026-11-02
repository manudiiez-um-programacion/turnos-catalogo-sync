package com.um.catalogsync.integration.domain.model;

import com.um.catalogsync.shared.domain.model.Secret;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * La configuracion de integracion vigente de la cuenta tecnica: los campos
 * del objeto {@code integration} de §5.3.
 *
 * <p><b>Es un dato recibido, no una decision nuestra.</b> Los topics, el
 * consumer group, los namespaces y las credenciales de Redis los asigna la
 * catedra por {@code groupId}. La regla no negociable del proyecto es que
 * nada de esto se fije en el codigo: se lee de este objeto en tiempo de
 * ejecucion. Si manana la catedra renombra un topic, este servicio se
 * entera sin recompilar.
 *
 * <p><b>Inmutable</b>, a diferencia de los modelos de los slices de
 * catalogo, que tienen {@code @Setter}. La diferencia es deliberada: esos
 * modelan filas que la sincronizacion actualiza; este modela una respuesta
 * que la catedra dio en un momento. No se edita por partes —se reemplaza
 * entera por la siguiente lectura—, asi que un setter solo permitiria
 * dejarla a medio actualizar.
 *
 * <p>POJO puro: sin JPA, sin Jackson, sin validacion de framework. El
 * secreto va envuelto en {@link Secret} y el {@link #toString()} no lo
 * muestra.
 */
@Getter
@Builder
@AllArgsConstructor
public class IntegrationConfig {

    /**
     * Identifica la integracion tecnica ante la catedra (§2.1). No es un
     * grupo de trabajo ni un usuario final: es lo que aisla el acceso REST,
     * los namespaces de Redis y los topics de Kafka.
     */
    private final String groupId;

    private final String redisHost;

    private final int redisPort;

    private final String redisUsername;

    /**
     * Solo viene cuando {@link #provisioningStatus} es
     * {@code PROVISIONED} (§5.1). En los demas estados es {@code null}, y
     * eso es lo esperado: ver {@link #missingRequiredValues()}.
     */
    private final Secret redisPassword;

    /** Prefijo de solo lectura de la catedra, {@code catedra:sync:*} (§14.1). */
    private final String redisReadNamespace;

    /** Prefijo propio donde este proyecto puede escribir (§14.6). */
    private final String redisWriteNamespace;

    private final String kafkaBootstrapServers;

    private final String kafkaConsumerGroupId;

    /** Topic de {@code CatalogUpdated} (§15.2), el unico que consume este servicio. */
    private final String kafkaCatalogTopic;

    /** Topic de acciones de turnos. Lo usa el servicio de turnos, no este. */
    private final String kafkaAppointmentActionsTopic;

    /** Topic de pedidos de telefono. Lo usa el servicio de turnos, no este. */
    private final String kafkaAppointmentPhoneTopic;

    private final ProvisioningStatus provisioningStatus;

    /** Si la cuenta esta habilitada y por lo tanto es usable. */
    public boolean isProvisioned() {
        return provisioningStatus == ProvisioningStatus.PROVISIONED;
    }

    /**
     * Los nombres de los campos que hacen falta y no llegaron.
     *
     * <p><b>Devuelve nombres de campos y nunca valores.</b> Esa es la razon
     * por la que el chequeo vive aca en lugar de armarse en el mensaje de
     * una excepcion: lo que sale de este metodo se puede loguear y poner en
     * un mensaje de error sin revisar si algo sensible se colo, porque por
     * construccion no hay valores adentro.
     *
     * <p><b>Que se considera necesario.</b> Lo que este servicio usa: el
     * {@code groupId}, la conexion y los namespaces de Redis, y el bootstrap,
     * el consumer group y el topic de catalogo de Kafka. Los dos topics de
     * turnos —{@code kafkaAppointmentActionsTopic} y
     * {@code kafkaAppointmentPhoneTopic}— quedan afuera a proposito: vienen
     * en el contrato porque la cuenta tecnica es compartida con el servicio
     * de turnos, pero este servicio no los toca. Exigirlos seria negarse a
     * arrancar por algo que no vamos a usar.
     *
     * <p>{@code redisPassword} se exige solo cuando el estado es
     * {@code PROVISIONED}, que es cuando el contrato promete entregarla. En
     * los demas estados su ausencia no es el problema: el problema es el
     * estado, y es lo que hay que informar.
     *
     * @return los campos faltantes, o una lista vacia si no falta ninguno
     */
    public List<String> missingRequiredValues() {
        List<String> faltantes = new ArrayList<>();
        agregarSiEstaEnBlanco(faltantes, "groupId", groupId);
        agregarSiEstaEnBlanco(faltantes, "redisHost", redisHost);
        if (redisPort <= 0 || redisPort > 65535) {
            faltantes.add("redisPort");
        }
        agregarSiEstaEnBlanco(faltantes, "redisUsername", redisUsername);
        if (isProvisioned() && redisPassword == null) {
            faltantes.add("redisPassword");
        }
        agregarSiEstaEnBlanco(faltantes, "redisReadNamespace", redisReadNamespace);
        agregarSiEstaEnBlanco(faltantes, "redisWriteNamespace", redisWriteNamespace);
        agregarSiEstaEnBlanco(faltantes, "kafkaBootstrapServers", kafkaBootstrapServers);
        agregarSiEstaEnBlanco(faltantes, "kafkaConsumerGroupId", kafkaConsumerGroupId);
        agregarSiEstaEnBlanco(faltantes, "kafkaCatalogTopic", kafkaCatalogTopic);
        return faltantes;
    }

    private static void agregarSiEstaEnBlanco(List<String> faltantes, String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            faltantes.add(campo);
        }
    }

    /**
     * Representacion legible para diagnosticar, sin el secreto.
     *
     * <p>Se escribe a mano y no con {@code @ToString} de Lombok por una
     * razon concreta: Lombok incluiria todos los campos, y aunque
     * {@link Secret} ya se imprime enmascarado, depender de eso deja la
     * proteccion en manos de que nadie cambie el tipo del campo a
     * {@code String} mas adelante. Aca el secreto no se nombra siquiera.
     *
     * <p>Tampoco se imprimen el host ni el bootstrap de Kafka: no son
     * secretos en sentido estricto, pero son la direccion privada del
     * servidor de la catedra (D6) y no tienen por que quedar en un log.
     */
    @Override
    public String toString() {
        return "IntegrationConfig(groupId=" + groupId
                + ", provisioningStatus=" + provisioningStatus
                + ", kafkaCatalogTopic=" + kafkaCatalogTopic
                + ", kafkaConsumerGroupId=" + kafkaConsumerGroupId
                + ", redisReadNamespace=" + redisReadNamespace
                + ", redisWriteNamespace=" + redisWriteNamespace
                + ")";
    }
}

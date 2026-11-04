package com.um.catalogsync.sync.infrastructure.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Como se consume el topic de catalogo de la catedra.
 *
 * <p><b>Por que aca no estan el bootstrap, el topic ni el consumer group.</b>
 * Por el mismo motivo que en {@code CatedraRedisProperties}: no son
 * configuracion de este servicio. Llegan en tiempo de ejecucion en el objeto
 * {@code integration} que entrega la catedra (§15.2) y pueden cambiar sin que
 * se reinicie nada. Tenerlos tambien en un {@code application.yaml} daria dos
 * fuentes para el mismo dato y la pregunta de cual gana. Lo que decide este
 * servicio es cuanto espera y cuantas veces reintenta.
 *
 * @param enabled si se consume el topic. Se apaga en pruebas, donde cada
 *     contexto levantado intentaria conectarse a un broker que no existe
 * @param pollTimeout cuanto espera cada {@code poll} antes de volver sin
 *     mensajes. No es un timeout de red: marca cada cuanto el contenedor
 *     retoma el control para atender un pedido de parada
 * @param startupRetryInterval cada cuanto se reintenta arrancar el
 *     consumidor mientras la configuracion de integracion no este
 *     disponible. El arranque del servicio no puede depender de la red
 *     privada (D6), asi que el consumidor arranca tarde y se reintenta
 * @param retryInterval cuanto se espera entre reintentos de un mismo mensaje
 *     ante una falla transitoria
 * @param maxRetries cuantas veces se reintenta un mismo mensaje antes de
 *     detener el consumidor. Limitado y observable, como pide §18.4: al
 *     agotarse queda un ERROR y el consumidor se detiene, y como el offset
 *     nunca se confirmo, al volver a arrancar el mensaje se reentrega
 */
@ConfigurationProperties(prefix = "catedra.kafka")
public record CatedraKafkaProperties(
        boolean enabled,
        Duration pollTimeout,
        Duration startupRetryInterval,
        Duration retryInterval,
        long maxRetries) {

    /**
     * Rechaza los valores que dejarian al consumidor inservible.
     *
     * <p>Mismo criterio que las propiedades de Redis: se valida al construir
     * porque ninguno de estos valores tiene un escenario valido en cero o en
     * negativo, y es mejor que el arranque falle nombrando la propiedad que
     * descubrirlo con un consumidor que no reintenta nunca o que reintenta
     * sin pausa.
     *
     * <p>{@code maxRetries} en cero <b>si</b> es valido: significa un solo
     * intento por mensaje, sin reintentos en el lugar. Es una politica
     * razonable —el mensaje se reentrega igual cuando el consumidor vuelve a
     * arrancar— y por eso se admite.
     */
    public CatedraKafkaProperties {
        exigirPositivo(pollTimeout, "catedra.kafka.poll-timeout");
        exigirPositivo(startupRetryInterval, "catedra.kafka.startup-retry-interval");
        exigirPositivo(retryInterval, "catedra.kafka.retry-interval");
        if (maxRetries < 0) {
            throw new IllegalArgumentException(
                    "catedra.kafka.max-retries no puede ser negativa, y llego " + maxRetries);
        }
    }

    private static void exigirPositivo(Duration valor, String propiedad) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException(
                    propiedad + " tiene que ser una duracion positiva, y llego \"" + valor + "\"");
        }
    }
}

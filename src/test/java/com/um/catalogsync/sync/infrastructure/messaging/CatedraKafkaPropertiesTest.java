package com.um.catalogsync.sync.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Los valores que el consumidor no puede aceptar.
 *
 * <p>Se validan al construir y no donde se usan por el mismo motivo que en
 * {@code CatedraRedisProperties}: una duracion en cero o negativa no tiene
 * ningun escenario valido, y es mejor que el arranque falle nombrando la
 * propiedad que descubrirlo con un consumidor que reintenta sin pausa.
 */
class CatedraKafkaPropertiesTest {

    @Test
    @DisplayName("Los valores del application.yaml se aceptan")
    void losValoresDeProduccion() {
        assertThatCode(() -> new CatedraKafkaProperties(
                        true, Duration.ofSeconds(3), Duration.ofSeconds(30),
                        Duration.ofSeconds(5), 3))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    @DisplayName("Un poll-timeout no positivo se rechaza nombrando la propiedad")
    void pollTimeoutInvalido(String duracion) {
        assertThatThrownBy(() -> new CatedraKafkaProperties(
                        true, Duration.parse(duracion), Duration.ofSeconds(30),
                        Duration.ofSeconds(5), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.kafka.poll-timeout");
    }

    @Test
    @DisplayName("Un startup-retry-interval ausente se rechaza")
    void sinIntervaloDeArranque() {
        assertThatThrownBy(() -> new CatedraKafkaProperties(
                        true, Duration.ofSeconds(3), null, Duration.ofSeconds(5), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.kafka.startup-retry-interval");
    }

    @Test
    @DisplayName("Un retry-interval en cero se rechaza")
    void sinEsperaEntreReintentos() {
        assertThatThrownBy(() -> new CatedraKafkaProperties(
                        true, Duration.ofSeconds(3), Duration.ofSeconds(30), Duration.ZERO, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.kafka.retry-interval");
    }

    @Test
    @DisplayName("max-retries en cero es valido: un solo intento por mensaje")
    void sinReintentosEnElLugar() {
        // No es un valor degenerado: el mensaje se reentrega igual cuando el
        // consumidor vuelve a arrancar, asi que "sin reintentos en el lugar"
        // es una politica razonable y no un error de configuracion.
        assertThatCode(() -> new CatedraKafkaProperties(
                        true, Duration.ofSeconds(3), Duration.ofSeconds(30),
                        Duration.ofSeconds(5), 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("max-retries negativa se rechaza")
    void reintentosNegativos() {
        assertThatThrownBy(() -> new CatedraKafkaProperties(
                        true, Duration.ofSeconds(3), Duration.ofSeconds(30),
                        Duration.ofSeconds(5), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.kafka.max-retries");
    }
}

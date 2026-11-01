package com.um.catalogsync.sync.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los dos timeouts no pueden ser "esperar para siempre".
 *
 * <p>Es una prueba chica de un error facil de cometer: un
 * {@code command-timeout: 0} en un yaml pasa desapercibido en revision y
 * convierte cada lectura en un hilo que puede quedarse colgado. El valor en
 * cero no es un caso borde raro, es lo que escribe quien quiso "sin limite"
 * sin pensar en que eso es justamente el problema que el timeout resuelve.
 */
class CatedraRedisPropertiesTest {

    @Test
    @DisplayName("Dos duraciones positivas son validas")
    void duracionesPositivas() {
        CatedraRedisProperties propiedades =
                new CatedraRedisProperties(Duration.ofSeconds(5), Duration.ofSeconds(10));

        assertThat(propiedades.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(propiedades.commandTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Un milisegundo es valido: el limite es ser positivo, no ser razonable")
    void unMilisegundoEsValido() {
        assertThatCode(() -> new CatedraRedisProperties(
                        Duration.ofMillis(1), Duration.ofMillis(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un connect-timeout en cero se rechaza nombrando la propiedad")
    void connectTimeoutEnCero() {
        assertThatThrownBy(() -> new CatedraRedisProperties(Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.redis.connect-timeout");
    }

    @Test
    @DisplayName("Un command-timeout negativo se rechaza nombrando la propiedad")
    void commandTimeoutNegativo() {
        assertThatThrownBy(() -> new CatedraRedisProperties(
                        Duration.ofSeconds(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.redis.command-timeout");
    }

    @Test
    @DisplayName("Una propiedad ausente se rechaza igual que una invalida")
    void propiedadAusente() {
        // Pasa si alguien borra la linea del yaml: el binding deja el
        // componente en null y sin este chequeo el sintoma seria un NPE al
        // armar la conexion, lejos de la causa.
        assertThatThrownBy(() -> new CatedraRedisProperties(null, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.redis.connect-timeout");
    }
}

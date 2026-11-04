package com.um.catalogsync.sync.infrastructure.client.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Los valores que el reintento del snapshot no puede aceptar.
 *
 * <p>Mismo criterio que {@code CatedraKafkaPropertiesTest}: se valida al
 * construir, porque un {@code max-attempts} menor a uno o un {@code backoff}
 * negativo no tienen ningun escenario valido.
 */
class CatalogSnapshotRetryPropertiesTest {

    @Test
    @DisplayName("Los valores del application.yaml se aceptan")
    void losValoresDeProduccion() {
        assertThatCode(() -> new CatalogSnapshotRetryProperties(3, Duration.ofSeconds(2)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("max-attempts en uno es valido: no reintenta")
    void unSoloIntentoEsValido() {
        assertThatCode(() -> new CatalogSnapshotRetryProperties(1, Duration.ofSeconds(2)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("max-attempts menor a uno se rechaza nombrando la propiedad")
    void maxAttemptsInvalido(int valor) {
        assertThatThrownBy(() -> new CatalogSnapshotRetryProperties(valor, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.api.snapshot-retry.max-attempts");
    }

    @Test
    @DisplayName("Un backoff en cero es valido: reintenta sin esperar")
    void backoffEnCeroEsValido() {
        assertThatCode(() -> new CatalogSnapshotRetryProperties(3, Duration.ZERO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un backoff negativo se rechaza")
    void backoffNegativoSeRechaza() {
        assertThatThrownBy(() -> new CatalogSnapshotRetryProperties(3, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.api.snapshot-retry.backoff");
    }

    @Test
    @DisplayName("Un backoff nulo se rechaza")
    void backoffNuloSeRechaza() {
        assertThatThrownBy(() -> new CatalogSnapshotRetryProperties(3, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catedra.api.snapshot-retry.backoff");
    }
}

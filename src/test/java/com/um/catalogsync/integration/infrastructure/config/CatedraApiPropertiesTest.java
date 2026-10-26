package com.um.catalogsync.integration.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatedraApiPropertiesTest {

    private static CatedraApiProperties conBaseUrl(String baseUrl) {
        return new CatedraApiProperties(
                baseUrl,
                "juan.perez",
                "clave-de-prueba",
                Duration.ofSeconds(5),
                Duration.ofSeconds(20),
                true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://10.147.17.1:8080/api",
        "http://10.147.17.1:8080/api/",
        "  http://10.147.17.1:8080/api  "
    })
    @DisplayName("Rechaza una URL base que termina en /api")
    void rechazaElApiSobrante(String conApi) {
        // El sintoma real de este error no es un 404: el servidor de la
        // catedra responde 200 con HTML desde el catch-all del frontend, asi
        // que la llamada parece andar y lo que falla es el parseo. Verificado
        // contra el servidor real el 2026-10-14.
        assertThatThrownBy(() -> conBaseUrl(conApi))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no debe terminar en /api");
    }

    @Test
    @DisplayName("Acepta el origen solo y le saca la barra final")
    void normalizaLaBarraFinal() {
        assertThat(conBaseUrl("http://10.147.17.1:8080/").baseUrl())
                .isEqualTo("http://10.147.17.1:8080");
    }

    @Test
    @DisplayName("Un host que contiene 'api' en otra parte no se rechaza")
    void noConfundeUnHostConApi() {
        assertThat(conBaseUrl("http://api.catedra.internal:8080").baseUrl())
                .isEqualTo("http://api.catedra.internal:8080");
    }

    @Test
    @DisplayName("toString no imprime la contraseña ni la direccion del servidor")
    void toStringNoFiltraNada() {
        String texto = conBaseUrl("http://10.147.17.1:8080").toString();

        assertThat(texto)
                .doesNotContain("clave-de-prueba")
                .doesNotContain("10.147.17.1")
                .contains("juan.perez");
    }

    @Test
    @DisplayName("Con los tres datos completos, la integracion esta configurada")
    void conLosTresDatosEstaConfigurada() {
        assertThat(conBaseUrl("http://10.147.17.1:8080").isConfigured()).isTrue();
    }

    @Test
    @DisplayName("Sin configurar nombra las variables que faltan, no los valores")
    void sinConfigurarNombraLasVariables() {
        // Es el estado normal mientras el alta de la cuenta tecnica no este
        // hecha, y es por lo que no hay @NotBlank: con validacion de binding
        // el contenedor de Docker Compose no arrancaria.
        CatedraApiProperties vacias = new CatedraApiProperties(
                "", "  ", null, Duration.ofSeconds(5), Duration.ofSeconds(20), true);

        assertThat(vacias.isConfigured()).isFalse();
        assertThat(vacias.missingVariables())
                .isEqualTo("CATEDRA_API_BASE_URL CATEDRA_TECHNICAL_USERNAME"
                        + " CATEDRA_TECHNICAL_PASSWORD");
    }

    @Test
    @DisplayName("Si solo falta la clave, solo se nombra esa variable")
    void nombraSoloLoQueFalta() {
        CatedraApiProperties sinClave = new CatedraApiProperties(
                "http://10.147.17.1:8080", "juan.perez", "",
                Duration.ofSeconds(5), Duration.ofSeconds(20), true);

        assertThat(sinClave.isConfigured()).isFalse();
        assertThat(sinClave.missingVariables()).isEqualTo("CATEDRA_TECHNICAL_PASSWORD");
    }
}

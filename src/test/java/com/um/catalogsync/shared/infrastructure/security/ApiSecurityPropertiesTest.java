package com.um.catalogsync.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Las reglas que {@link ApiSecurityProperties} hace cumplir al arrancar. */
class ApiSecurityPropertiesTest {

    private static final String SECRETO_VALIDO = "un-secreto-de-al-menos-32-bytes-para-hs256";

    private static ApiSecurityProperties propiedades(List<String> origenesCors) {
        return new ApiSecurityProperties(
                new ApiSecurityProperties.Jwt(SECRETO_VALIDO, "turnos-reservas-service"),
                new ApiSecurityProperties.Jwt(SECRETO_VALIDO, "turnos-reservas-service"),
                origenesCors);
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        @DisplayName("rechaza el comodin, que es lo que §9 prohibe")
        void rechazaElComodin() {
            assertThatThrownBy(() -> propiedades(List.of("*")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        }

        @Test
        @DisplayName("tambien lo rechaza con espacios alrededor")
        void rechazaElComodinConEspacios() {
            assertThatThrownBy(() -> propiedades(List.of("  *  ")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("limpia espacios y descarta entradas vacias")
        void limpiaLaLista() {
            // Una variable de entorno con coma final es lo normal y produce una
            // entrada vacia: un origen vacio en la configuracion de CORS no
            // falla, simplemente nunca coincide, y eso es un error silencioso.
            assertThat(propiedades(List.of(" http://localhost:3000 ", "", "https://app.example"))
                            .origenesCors())
                    .containsExactly("http://localhost:3000", "https://app.example");
        }

        @Test
        @DisplayName("sin configuracion la lista queda vacia, no nula")
        void sinConfiguracion() {
            assertThat(propiedades(null).origenesCors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Los secretos")
    class Secretos {

        @Test
        @DisplayName("falta: el mensaje nombra la variable y como generarla")
        void faltaElSecreto() {
            ApiSecurityProperties.Jwt sinSecreto = new ApiSecurityProperties.Jwt(null, "cualquiera");

            assertThatThrownBy(() -> sinSecreto.secretoVerificado("JWT_USER_SECRET"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_USER_SECRET")
                    .hasMessageContaining("openssl rand");
        }

        @Test
        @DisplayName("en blanco cuenta como ausente: compose pasa variables vacias")
        void secretoEnBlanco() {
            ApiSecurityProperties.Jwt enBlanco = new ApiSecurityProperties.Jwt("   ", "cualquiera");

            assertThatThrownBy(() -> enBlanco.secretoVerificado("JWT_INTERNAL_SECRET"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_INTERNAL_SECRET");
        }

        @Test
        @DisplayName("corto: HS256 exige 256 bits y el mensaje lo dice")
        void secretoCorto() {
            ApiSecurityProperties.Jwt corto = new ApiSecurityProperties.Jwt("muy-corto", "cualquiera");

            assertThatThrownBy(() -> corto.secretoVerificado("JWT_USER_SECRET"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("256 bits");
        }

        @Test
        @DisplayName("valido: lo devuelve tal cual")
        void secretoValido() {
            assertThat(new ApiSecurityProperties.Jwt(SECRETO_VALIDO, "cualquiera")
                            .secretoVerificado("JWT_USER_SECRET"))
                    .isEqualTo(SECRETO_VALIDO);
        }
    }

    @Nested
    @DisplayName("No se filtran en texto")
    class NoSeFiltran {

        @Test
        @DisplayName("el toString de las propiedades no imprime ningun secreto")
        void toStringDeLasPropiedades() {
            assertThat(propiedades(List.of("http://localhost:3000")).toString())
                    .doesNotContain(SECRETO_VALIDO)
                    .contains("***");
        }

        @Test
        @DisplayName("el toString de cada token tampoco, pero si el emisor")
        void toStringDelToken() {
            // El emisor no es secreto y sirve para diagnosticar: el error tipico
            // es que los dos servicios no coincidan en el iss.
            assertThat(new ApiSecurityProperties.Jwt(SECRETO_VALIDO, "turnos-reservas-service").toString())
                    .doesNotContain(SECRETO_VALIDO)
                    .contains("turnos-reservas-service");
        }
    }
}

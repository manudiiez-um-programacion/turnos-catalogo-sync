package com.um.catalogsync.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretTest {

    private static final String VALOR = "valor-sensible-de-prueba";

    @Test
    @DisplayName("toString devuelve la mascara y nunca el valor")
    void toStringNoMuestraElValor() {
        Secret secreto = Secret.of(VALOR);

        assertThat(secreto.toString()).isEqualTo(Secret.MASCARA).doesNotContain(VALOR);
    }

    @Test
    @DisplayName("Interpolar el secreto en un texto tampoco lo revela")
    void interpolarNoRevelaElValor() {
        Secret secreto = Secret.of(VALOR);

        // Es el descuido que el tipo tiene que volver inofensivo: la forma
        // mas facil de loguear algo es concatenarlo.
        assertThat("clave=" + secreto).isEqualTo("clave=" + Secret.MASCARA);
    }

    @Test
    @DisplayName("value() devuelve el valor real para quien lo necesita")
    void valueDevuelveElValor() {
        assertThat(Secret.of(VALOR).value()).isEqualTo(VALOR);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("of rechaza un secreto vacio: no es un secreto")
    void ofRechazaVacios(String invalido) {
        assertThatThrownBy(() -> Secret.of(invalido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser nulo ni vacio");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("ofNullable tolera la ausencia, que el contrato preve")
    void ofNullableToleraLaAusencia(String ausente) {
        assertThat(Secret.ofNullable(ausente)).isNull();
    }

    @Test
    @DisplayName("ofNullable envuelve el valor cuando llego")
    void ofNullableEnvuelveCuandoLlega() {
        assertThat(Secret.ofNullable(VALOR)).isEqualTo(Secret.of(VALOR));
    }

    @Test
    @DisplayName("Dos secretos con el mismo valor son iguales")
    void igualdadPorValor() {
        assertThat(Secret.of(VALOR))
                .isEqualTo(Secret.of(VALOR))
                .hasSameHashCodeAs(Secret.of(VALOR))
                .isNotEqualTo(Secret.of("otro-valor"));
    }
}

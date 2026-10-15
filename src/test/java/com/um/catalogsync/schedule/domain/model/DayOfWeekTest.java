package com.um.catalogsync.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class DayOfWeekTest {

    @Test
    @DisplayName("Tiene exactamente los siete dias del contrato, en orden de semana")
    void tieneLosSieteDiasEnOrden() {
        assertThat(DayOfWeek.values())
                .containsExactly(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY);
    }

    @ParameterizedTest
    @EnumSource(DayOfWeek.class)
    @DisplayName("fromContract acepta los siete literales que manda el contrato")
    void fromContractAceptaLosSiete(DayOfWeek esperado) {
        assertThat(DayOfWeek.fromContract(esperado.name())).isSameAs(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FUNDAY", "monday", "Monday", "LUNES", "MON", "", " ", "MONDAY "})
    @DisplayName("Un dia desconocido falla de forma explicita, no en silencio")
    void fromContractRechazaLoDesconocido(String desconocido) {
        assertThatThrownBy(() -> DayOfWeek.fromContract(desconocido))
                .isInstanceOf(UnknownDayOfWeekException.class)
                .hasMessageContaining(desconocido);
    }

    @Test
    @DisplayName("Con null tampoco devuelve null: falla igual que con cualquier otro valor")
    void fromContractRechazaNull() {
        assertThatThrownBy(() -> DayOfWeek.fromContract(null))
                .isInstanceOf(UnknownDayOfWeekException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("La excepcion conserva el valor recibido para poder diagnosticarlo")
    void laExcepcionConservaElValor() {
        assertThatThrownBy(() -> DayOfWeek.fromContract("FUNDAY"))
                .isInstanceOf(UnknownDayOfWeekException.class)
                .extracting(e -> ((UnknownDayOfWeekException) e).getValorRecibido())
                .isEqualTo("FUNDAY");
    }
}

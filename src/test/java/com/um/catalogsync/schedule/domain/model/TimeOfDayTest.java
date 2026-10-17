package com.um.catalogsync.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeOfDayTest {

    @Test
    @DisplayName("Acepta HH:mm:ss, que es lo que manda el snapshot (§7)")
    void aceptaElFormatoDelSnapshot() {
        assertThat(TimeOfDay.parse("09:00:00")).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("Acepta HH:mm, que es lo que manda el contrato de holds")
    void aceptaElFormatoCorto() {
        assertThat(TimeOfDay.parse("09:30")).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    @DisplayName("Los dos formatos de la misma hora dan el mismo LocalTime")
    void losDosFormatosSonEquivalentes() {
        assertThat(TimeOfDay.parse("09:00")).isEqualTo(TimeOfDay.parse("09:00:00"));
    }

    @ParameterizedTest
    @CsvSource({
        "00:00,     0, 0,  0",
        "00:00:00,  0, 0,  0",
        "23:59,    23, 59, 0",
        "23:59:59, 23, 59, 59",
        "12:05:30, 12, 5,  30",
    })
    @DisplayName("Cubre el rango completo del dia en los dos formatos")
    void cubreElRangoDelDia(String texto, int hora, int minuto, int segundo) {
        assertThat(TimeOfDay.parse(texto)).isEqualTo(LocalTime.of(hora, minuto, segundo));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "9:00",             // sin cero a la izquierda
        "09",               // sin minutos
        "24:00",            // hora fuera de rango
        "09:60",            // minuto fuera de rango
        "09:00:60",         // segundo fuera de rango
        "09:00:00.123",     // fraccion: ISO_LOCAL_TIME la aceptaria, el contrato no la define
        "09:00:00Z",        // con zona: las horas de atencion son locales (§4)
        "09:00:00+03:00",
        "09-00",
        "nueve",
        "",
        "   ",
    })
    @DisplayName("Cualquier otra forma falla de manera atribuible")
    void rechazaLoQueElContratoNoDefine(String invalida) {
        assertThatThrownBy(() -> TimeOfDay.parse(invalida))
                .isInstanceOf(InvalidTimeOfDayException.class);
    }

    @Test
    @DisplayName("Con null falla en lugar de devolver null")
    void rechazaNull() {
        assertThatThrownBy(() -> TimeOfDay.parse(null))
                .isInstanceOf(InvalidTimeOfDayException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("La excepcion conserva el valor recibido y la causa original")
    void laExcepcionConservaElContexto() {
        assertThatThrownBy(() -> TimeOfDay.parse("25:00"))
                .isInstanceOf(InvalidTimeOfDayException.class)
                .hasMessageContaining("25:00")
                .hasCauseInstanceOf(java.time.format.DateTimeParseException.class)
                .extracting(e -> ((InvalidTimeOfDayException) e).getValorRecibido())
                .isEqualTo("25:00");
    }
}

package com.um.catalogsync.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El criterio de disponibilidad.
 *
 * <p>Lo que importa fijar es que las cuatro formas invalidas del filtro no se
 * pueden construir. Si se pudieran, el error aparecería mas abajo —una
 * consulta con un rango invertido devuelve cero filas sin quejarse— y el
 * cliente recibiria una pagina vacia en lugar de un 400 que le dice que
 * corregir.
 */
class AvailabilityCriteriaTest {

    @Nested
    @DisplayName("Las tres formas validas")
    class FormasValidas {

        @Test
        @DisplayName("solo el dia: atiende los lunes, a cualquier hora")
        void soloElDia() {
            AvailabilityCriteria criteria = AvailabilityCriteria.de("MONDAY", null, null).orElseThrow();

            assertThat(criteria.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(criteria.filtraPorDia()).isTrue();
            assertThat(criteria.filtraPorRango()).isFalse();
        }

        @Test
        @DisplayName("solo el rango: atiende por la mañana, cualquier dia")
        void soloElRango() {
            AvailabilityCriteria criteria =
                    AvailabilityCriteria.de(null, "09:00", "13:00").orElseThrow();

            assertThat(criteria.dayOfWeek()).isNull();
            assertThat(criteria.filtraPorDia()).isFalse();
            assertThat(criteria.filtraPorRango()).isTrue();
            assertThat(criteria.from()).isEqualTo(LocalTime.of(9, 0));
            assertThat(criteria.to()).isEqualTo(LocalTime.of(13, 0));
        }

        @Test
        @DisplayName("los dos: atiende los lunes por la mañana")
        void elDiaYElRango() {
            AvailabilityCriteria criteria =
                    AvailabilityCriteria.de("MONDAY", "09:00", "13:00").orElseThrow();

            assertThat(criteria.filtraPorDia()).isTrue();
            assertThat(criteria.filtraPorRango()).isTrue();
        }

        @Test
        @DisplayName("acepta HH:mm y HH:mm:ss, las dos formas del contrato (§4)")
        void aceptaLasDosFormasDeHora() {
            // Reusa TimeOfDay, que es la misma funcion con la que se leyo el
            // snapshot: §4 obliga a aceptar las dos formas.
            assertThat(AvailabilityCriteria.de(null, "09:00", "13:00:00").orElseThrow().from())
                    .isEqualTo(LocalTime.of(9, 0));
            assertThat(AvailabilityCriteria.de(null, "09:00:30", "13:00").orElseThrow().from())
                    .isEqualTo(LocalTime.of(9, 0, 30));
        }
    }

    @Nested
    @DisplayName("Sin filtro")
    class SinFiltro {

        @Test
        @DisplayName("los tres ausentes dan vacio: la disponibilidad es opcional")
        void losTresAusentes() {
            assertThat(AvailabilityCriteria.de(null, null, null)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("los tres en blanco tambien, igual que el filtro por nombre")
        void losTresEnBlanco(String enBlanco) {
            assertThat(AvailabilityCriteria.de(enBlanco, enBlanco, enBlanco)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Las formas invalidas")
    class FormasInvalidas {

        @ParameterizedTest
        @CsvSource({"13:00,09:00", "09:00,09:00", "13:00,12:59"})
        @DisplayName("un rango invertido o de duracion cero no se puede construir")
        void rangoInvertidoOVacio(String desde, String hasta) {
            // El de duracion cero se rechaza igual que el invertido: no hay
            // atencion posible en una franja que no dura nada, y aceptarlo
            // devolveria una pagina vacia en lugar de un error.
            assertThatThrownBy(() -> AvailabilityCriteria.de(null, desde, hasta))
                    .isInstanceOf(InvalidAvailabilityFilterException.class)
                    .hasMessageContaining("posterior");
        }

        @ParameterizedTest
        @CsvSource({"09:00,", ",13:00"})
        @DisplayName("media franja no alcanza: el rango se pide completo")
        void mediaFranja(String desde, String hasta) {
            assertThatThrownBy(() -> AvailabilityCriteria.de(null, desde, hasta))
                    .isInstanceOf(InvalidAvailabilityFilterException.class)
                    .hasMessageContaining("completo");
        }

        @ParameterizedTest
        @ValueSource(strings = {"LUNES", "monday", "Monday", "FUNDAY", "1"})
        @DisplayName("un dia que no es uno de los siete del contrato")
        void diaInexistente(String dia) {
            // "monday" se rechaza a proposito: DayOfWeek.fromContract compara
            // exacto, porque el contrato manda "MONDAY" y tolerar variantes
            // seria absorber en silencio algo que no deberia llegar.
            assertThatThrownBy(() -> AvailabilityCriteria.de(dia, null, null))
                    .isInstanceOf(InvalidAvailabilityFilterException.class)
                    .hasMessageContaining(dia);
        }

        @ParameterizedTest
        @ValueSource(strings = {"9am", "25:00", "09.00", "nueve", "09:00:00.123"})
        @DisplayName("una hora que no respeta ninguno de los dos formatos")
        void horaMalFormada(String hora) {
            assertThatThrownBy(() -> AvailabilityCriteria.de(null, hora, "23:00"))
                    .isInstanceOf(InvalidAvailabilityFilterException.class)
                    .hasMessageContaining("HH:mm");
        }

        @Test
        @DisplayName("el error del dia conserva la causa original, para el log")
        void conservaLaCausa() {
            // El cliente ve el mensaje traducido; quien diagnostica tiene la
            // excepcion de dominio que de verdad fallo.
            assertThatThrownBy(() -> AvailabilityCriteria.de("LUNES", null, null))
                    .hasCauseInstanceOf(UnknownDayOfWeekException.class);

            assertThatThrownBy(() -> AvailabilityCriteria.de(null, "9am", "23:00"))
                    .hasCauseInstanceOf(InvalidTimeOfDayException.class);
        }

        @Test
        @DisplayName("el constructor directo tampoco deja armar un criterio vacio")
        void elConstructorDirectoTambienValida() {
            // La fabrica devuelve Optional.empty() cuando no se pidio nada,
            // asi que un criterio existente siempre filtra por algo. El
            // constructor lo sostiene por si alguien lo llama derecho.
            assertThatThrownBy(() -> new AvailabilityCriteria(null, null, null))
                    .isInstanceOf(InvalidAvailabilityFilterException.class);
        }
    }

    @Test
    @DisplayName("la fabrica devuelve Optional y no null, asi la ausencia se decide explicito")
    void laFabricaDevuelveOptional() {
        Optional<AvailabilityCriteria> sinFiltro = AvailabilityCriteria.de(null, null, null);
        Optional<AvailabilityCriteria> conFiltro = AvailabilityCriteria.de("FRIDAY", null, null);

        assertThat(sinFiltro).isEmpty();
        assertThat(conFiltro).isPresent();
    }
}

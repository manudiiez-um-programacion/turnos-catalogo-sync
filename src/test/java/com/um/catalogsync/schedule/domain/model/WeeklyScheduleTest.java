package com.um.catalogsync.schedule.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyScheduleTest {

    private static WeeklySchedule horario(DayOfWeek dia, String desde) {
        return WeeklySchedule.builder()
                .professionalId(15L)
                .dayOfWeek(dia)
                .startTime(LocalTime.parse(desde))
                .endTime(LocalTime.parse(desde).plusHours(1))
                .slotDurationMinutes(30)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("Ordena por dia de la semana y despues por hora de inicio")
    void ordenaPorDiaYHora() {
        List<WeeklySchedule> desordenados = List.of(
                horario(DayOfWeek.WEDNESDAY, "09:00"),
                horario(DayOfWeek.MONDAY, "14:00"),
                horario(DayOfWeek.MONDAY, "09:00"),
                horario(DayOfWeek.SUNDAY, "10:00"));

        List<WeeklySchedule> ordenados =
                desordenados.stream().sorted(WeeklySchedule.BY_DAY_AND_START).toList();

        assertThat(ordenados)
                .extracting(WeeklySchedule::getDayOfWeek, WeeklySchedule::getStartTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DayOfWeek.MONDAY, LocalTime.of(9, 0)),
                        org.assertj.core.groups.Tuple.tuple(DayOfWeek.MONDAY, LocalTime.of(14, 0)),
                        org.assertj.core.groups.Tuple.tuple(
                                DayOfWeek.WEDNESDAY, LocalTime.of(9, 0)),
                        org.assertj.core.groups.Tuple.tuple(DayOfWeek.SUNDAY, LocalTime.of(10, 0)));
    }

    @Test
    @DisplayName("El orden es cronologico, no alfabetico: viernes va antes que sabado")
    void elOrdenNoEsAlfabetico() {
        // Alfabeticamente FRIDAY < MONDAY < SATURDAY; cronologicamente no.
        List<WeeklySchedule> ordenados = List.of(
                        horario(DayOfWeek.SATURDAY, "09:00"),
                        horario(DayOfWeek.FRIDAY, "09:00"),
                        horario(DayOfWeek.MONDAY, "09:00"))
                .stream()
                .sorted(WeeklySchedule.BY_DAY_AND_START)
                .toList();

        assertThat(ordenados)
                .extracting(WeeklySchedule::getDayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    }
}

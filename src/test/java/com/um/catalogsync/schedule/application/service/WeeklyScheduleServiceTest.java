package com.um.catalogsync.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyScheduleServiceTest {

    private static final WeeklySchedule LUNES = WeeklySchedule.builder()
            .id(21L)
            .professionalId(15L)
            .dayOfWeek(DayOfWeek.MONDAY)
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(12, 0))
            .slotDurationMinutes(30)
            .enabled(true)
            .build();

    @Test
    @DisplayName("getByProfessional delega en el caso de uso")
    void delegaEnElCasoDeUso() {
        WeeklyScheduleService service = new WeeklyScheduleService(id -> List.of(LUNES));

        assertThat(service.getByProfessional(15L)).containsExactly(LUNES);
    }

    @Test
    @DisplayName("Un profesional sin horarios da una agenda vacia, no una excepcion")
    void sinHorariosNoEsUnError() {
        WeeklyScheduleService service = new WeeklyScheduleService(id -> List.of());

        assertThat(service.getByProfessional(999L)).isEmpty();
    }
}

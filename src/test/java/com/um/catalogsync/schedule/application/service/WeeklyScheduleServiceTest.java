package com.um.catalogsync.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.CurrentAgenda;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import java.time.Instant;
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

    private static final CurrentAgenda AGENDA = CurrentAgenda.of(
            Professional.builder().id(15L).categoryId(7L).enabled(true).build(),
            List.of(LUNES),
            CatalogVersion.builder()
                    .version(128L)
                    .appliedAt(Instant.parse("2026-10-27T18:30:00Z"))
                    .source(CatalogVersionSource.SNAPSHOT)
                    .build());

    /** La fachada solo compone: un caso de uso de cada tipo alcanza. */
    private static WeeklyScheduleService fachadaCon(List<WeeklySchedule> horarios) {
        return new WeeklyScheduleService(id -> horarios, id -> AGENDA);
    }

    @Test
    @DisplayName("getByProfessional delega en el caso de uso")
    void delegaEnElCasoDeUso() {
        WeeklyScheduleService service = fachadaCon(List.of(LUNES));

        assertThat(service.getByProfessional(15L)).containsExactly(LUNES);
    }

    @Test
    @DisplayName("Un profesional sin horarios da una agenda vacia, no una excepcion")
    void sinHorariosNoEsUnError() {
        WeeklyScheduleService service = fachadaCon(List.of());

        assertThat(service.getByProfessional(999L)).isEmpty();
    }

    @Test
    @DisplayName("getCurrentAgenda delega en su caso de uso")
    void delegaLaAgenda() {
        WeeklyScheduleService service = fachadaCon(List.of());

        assertThat(service.getCurrentAgenda(15L)).isSameAs(AGENDA);
    }
}

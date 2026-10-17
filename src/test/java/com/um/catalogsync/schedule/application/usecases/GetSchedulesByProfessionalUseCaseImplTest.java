package com.um.catalogsync.schedule.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba el caso de uso sin base de datos: lo unico que agrega sobre el
 * puerto es la garantia de orden, y eso se verifica con un repositorio que
 * devuelve a proposito las filas desordenadas.
 */
class GetSchedulesByProfessionalUseCaseImplTest {

    private static WeeklySchedule horario(Long id, DayOfWeek dia, String desde) {
        return WeeklySchedule.builder()
                .id(id)
                .professionalId(15L)
                .dayOfWeek(dia)
                .startTime(LocalTime.parse(desde))
                .endTime(LocalTime.parse(desde).plusHours(1))
                .slotDurationMinutes(30)
                .enabled(true)
                .build();
    }

    /** Repositorio de mentira que devuelve siempre lo que se le pasa. */
    private static WeeklyScheduleRepository repositorioQueDevuelve(
            List<WeeklySchedule> horarios) {
        return new WeeklyScheduleRepository() {
            @Override
            public WeeklySchedule upsert(WeeklySchedule schedule) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<WeeklySchedule> findById(Long id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<WeeklySchedule> findByProfessionalId(Long professionalId) {
                return horarios;
            }

            @Override
            public List<WeeklySchedule> findAll() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<WeeklySchedule> replaceAll(Collection<WeeklySchedule> schedules) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long deleteByIdIn(Collection<Long> ids) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    @DisplayName("Devuelve los horarios ordenados por dia y hora de inicio")
    void ordenaLaAgenda() {
        var useCase = new GetSchedulesByProfessionalUseCaseImpl(repositorioQueDevuelve(List.of(
                horario(3L, DayOfWeek.WEDNESDAY, "09:00"),
                horario(2L, DayOfWeek.MONDAY, "14:00"),
                horario(1L, DayOfWeek.MONDAY, "09:00"))));

        assertThat(useCase.getByProfessional(15L))
                .extracting(WeeklySchedule::getId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("Sin horarios devuelve lista vacia, no null ni excepcion")
    void sinHorariosDevuelveVacio() {
        var useCase = new GetSchedulesByProfessionalUseCaseImpl(
                repositorioQueDevuelve(List.of()));

        assertThat(useCase.getByProfessional(999L)).isEmpty();
    }
}

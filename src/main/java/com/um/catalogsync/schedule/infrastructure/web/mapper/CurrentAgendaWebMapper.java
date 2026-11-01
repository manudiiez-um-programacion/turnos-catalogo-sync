package com.um.catalogsync.schedule.infrastructure.web.mapper;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.CurrentAgenda;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.infrastructure.web.dto.CurrentAgendaResponse;
import com.um.catalogsync.schedule.infrastructure.web.dto.ProfessionalResponse;
import com.um.catalogsync.schedule.infrastructure.web.dto.WeeklyScheduleResponse;
import com.um.catalogsync.shared.infrastructure.web.dto.CatalogVersionResponse;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Del modelo de dominio al DTO de respuesta.
 *
 * <p>Existe para que el controlador no sepa de dominio y el dominio no sepa de
 * HTTP: es el borde que exige el ADR 0001. Lo que gana en concreto es que
 * agregar un campo al modelo local no cambia lo que ve el servicio de turnos, y
 * renombrar un campo del contrato no obliga a tocar el dominio.
 *
 * <p>Solo tiene direccion de salida. Esta API no recibe cuerpos: no hay
 * ninguna operacion de escritura, asi que no hay nada que mapear hacia adentro.
 */
@Component
public class CurrentAgendaWebMapper {

    /**
     * {@code HH:mm}, el formato del contrato de la catedra (§4).
     *
     * <p>Explicito y no {@code LocalTime.toString()}: ese omite los minutos en
     * una hora en punto —{@code 09:00} saldria como {@code 09}— y produciria un
     * formato distinto segun el valor.
     */
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public CurrentAgendaResponse toResponse(CurrentAgenda agenda) {
        return new CurrentAgendaResponse(
                toResponse(agenda.professional()),
                agenda.schedules().stream().map(this::toResponse).toList(),
                CatalogVersionResponse.from(agenda.catalogVersion()));
    }

    private ProfessionalResponse toResponse(Professional profesional) {
        return new ProfessionalResponse(
                profesional.getId(),
                profesional.getCategoryId(),
                profesional.getFirstName(),
                profesional.getLastName(),
                profesional.isEnabled());
    }

    private WeeklyScheduleResponse toResponse(WeeklySchedule horario) {
        return new WeeklyScheduleResponse(
                horario.getId(),
                horario.getDayOfWeek().name(),
                HORA.format(horario.getStartTime()),
                HORA.format(horario.getEndTime()),
                horario.getSlotDurationMinutes());
    }
}

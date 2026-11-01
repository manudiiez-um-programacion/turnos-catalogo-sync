package com.um.catalogsync.schedule.infrastructure.web.dto;

import com.um.catalogsync.shared.infrastructure.web.dto.CatalogVersionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * La agenda vigente de un profesional, tal como la lee el servicio de turnos.
 *
 * @param professional datos vigentes del profesional en la copia local
 * @param schedules sus horarios habilitados, ordenados por dia y hora de inicio
 * @param catalogVersion la version local con la que se resolvio esta respuesta
 */
@Schema(
        name = "CurrentAgenda",
        description =
                "Agenda vigente de un profesional. Son reglas de agenda, no disponibilidad:"
                        + " no incluye ocupaciones, bloqueos ni reservas, que son del servicio"
                        + " de turnos.")
public record CurrentAgendaResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProfessionalResponse professional,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Puede venir vacia: un profesional habilitado sin horarios")
                List<WeeklyScheduleResponse> schedules,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CatalogVersionResponse catalogVersion) {}

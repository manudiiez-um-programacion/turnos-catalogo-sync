package com.um.catalogsync.schedule.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un horario semanal habilitado.
 *
 * <p>Las horas van como {@code HH:mm}, que es el formato del contrato de la
 * catedra (§4) y el que la copia local recibio. Como cadena y no como numero de
 * minutos: es lo que el otro lado ya sabe leer.
 *
 * @param id id del horario en el catalogo de la catedra
 * @param dayOfWeek dia en ingles y mayusculas, como en el contrato
 * @param startTime hora de inicio de atencion, {@code HH:mm}
 * @param endTime hora de fin de atencion, {@code HH:mm}
 * @param slotDurationMinutes duracion de cada turno, en minutos
 */
@Schema(name = "AgendaWeeklySchedule", description = "Franja semanal de atencion habilitada")
public record WeeklyScheduleResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "315") Long id,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {
                            "MONDAY",
                            "TUESDAY",
                            "WEDNESDAY",
                            "THURSDAY",
                            "FRIDAY",
                            "SATURDAY",
                            "SUNDAY"
                        },
                        example = "MONDAY")
                String dayOfWeek,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{2}:\\d{2}$", example = "09:00")
                String startTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{2}:\\d{2}$", example = "13:00")
                String endTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "30") int slotDurationMinutes) {}

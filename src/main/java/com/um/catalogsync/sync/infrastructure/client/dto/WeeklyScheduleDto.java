package com.um.catalogsync.sync.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Un horario semanal del snapshot (§7).
 *
 * <p><b>{@code dayOfWeek}, {@code startTime} y {@code endTime} entran como
 * texto</b>, no como {@code java.time.DayOfWeek} ni {@code LocalTime}. Los
 * tres tienen una traduccion propia del dominio y dejar que Jackson los
 * resuelva la saltearia:
 *
 * <ul>
 *   <li>el dia lo traduce {@code DayOfWeek.fromContract}, que distingue "el
 *       contrato cambio" de un error de programacion cualquiera —lo que
 *       {@code valueOf} de la plataforma no hace;</li>
 *   <li>las horas las parsea {@code TimeOfDay.parse}, que acepta exactamente
 *       las dos formas que §4 obliga a aceptar, {@code HH:mm} y
 *       {@code HH:mm:ss}, y nada mas. El parseo ISO por defecto tambien
 *       tragaria {@code "09:00:00.123"} y variantes con offset, absorbiendo
 *       en silencio un cambio de contrato.</li>
 * </ul>
 *
 * <p>Es el mismo criterio por el que {@code provisioningStatus} entra como
 * {@code String} en el slice {@code integration}: donde el dominio tiene una
 * regla sobre el valor, el DTO lo deja pasar crudo y la regla decide.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeeklyScheduleDto(
        Long id,
        Long professionalId,
        String dayOfWeek,
        String startTime,
        String endTime,
        Integer slotDurationMinutes,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}

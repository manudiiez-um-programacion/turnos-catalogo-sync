package com.um.catalogsync.schedule.domain.model;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Horario semanal de atencion de un profesional, tal como lo define el
 * contrato (§7).
 *
 * <p>POJO puro, igual que {@code ProfessionalCategory} y
 * {@code Professional}: sin JPA, sin validacion, sin Jackson.
 *
 * <p>Es una <b>regla recurrente</b>, no un turno: dice "los lunes de 09:00
 * a 12:00, en bloques de 30 minutos". Este slice la guarda y la devuelve
 * tal cual. Expandirla a slots concretos y cruzarla con ocupaciones para
 * decir que turnos quedan libres es del servicio de turnos (§4.2), y por
 * eso no hay aca ningun metodo que genere horarios.
 *
 * <p>El profesional se referencia por {@code professionalId} y no por un
 * objeto {@code Professional}, por el mismo criterio que en el slice de
 * profesionales con la categoria: el slice queda autocontenido y es lo que
 * trae el contrato. Quien necesite los datos del profesional los pide al
 * slice de profesionales por su facade.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySchedule {

    /**
     * Orden natural de una agenda: primero por dia de la semana, despues
     * por hora de inicio.
     *
     * <p>Vive en el dominio y no en una consulta SQL por una razon
     * concreta: {@code day_of_week} se guarda como texto, asi que un
     * {@code ORDER BY day_of_week} ordenaria alfabeticamente —FRIDAY,
     * MONDAY, SATURDAY…— que no es el orden de la semana. Ordenar por el
     * {@code ordinal} del enum si da el orden cronologico, y como la
     * garantia vive aca, vale para cualquier adaptador y no solo para el de
     * JPA.
     */
    public static final Comparator<WeeklySchedule> BY_DAY_AND_START =
            Comparator.comparing(WeeklySchedule::getDayOfWeek)
                    .thenComparing(WeeklySchedule::getStartTime);

    private Long id;
    private Long professionalId;
    private DayOfWeek dayOfWeek;

    /** Hora local de atencion (§4). Sin zona: ver {@link TimeOfDay}. */
    private LocalTime startTime;

    private LocalTime endTime;

    /** Duracion del bloque en minutos. El contrato la manda siempre > 0. */
    private int slotDurationMinutes;

    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}

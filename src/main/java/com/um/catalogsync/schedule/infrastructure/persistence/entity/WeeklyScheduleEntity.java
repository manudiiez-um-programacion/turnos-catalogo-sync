package com.um.catalogsync.schedule.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representacion JPA de un horario semanal.
 *
 * <p>El profesional es una columna {@code Long} y no un {@code @ManyToOne}
 * a {@code ProfessionalEntity}, por el mismo criterio que en
 * {@code ProfessionalEntity} con la categoria: una relacion JPA ataria este
 * slice a la entidad de otro, y la integridad ya la garantiza la FK en la
 * base.
 */
@Entity
@Table(name = "weekly_schedule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyScheduleEntity {

    /** Id de la catedra. Sin {@code @GeneratedValue}. */
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    /**
     * El dia como texto, no como el enum de dominio con
     * {@code @Enumerated(STRING)}.
     *
     * <p>Es deliberado y tiene dos motivos. El primero es la regla del
     * proyecto: la entidad JPA no lleva tipos de dominio, igual que
     * {@code ProfessionalEntity} guarda {@code categoryId} como
     * {@code Long} y no un {@code ProfessionalCategory}. El segundo pesa
     * mas: con {@code @Enumerated}, la conversion la hace Hibernate con
     * {@code Enum.valueOf}, que ante un valor inesperado en la base lanza un
     * {@code IllegalArgumentException} generico desde dentro del motor. Con
     * texto, la conversion pasa por
     * {@code DayOfWeek.fromContract} en el mapper y falla con
     * {@code UnknownDayOfWeekException}, que es lo que el issue pide y lo
     * que la sincronizacion puede registrar.
     *
     * <p>{@code length = 9} acompaña al {@code VARCHAR(9)} de la migracion:
     * "WEDNESDAY" es el mas largo de los siete. La columna tiene ademas un
     * CHECK con los siete literales, asi que la base rechaza cualquier otro
     * valor aunque alguien escriba por fuera de la aplicacion.
     */
    @Column(name = "day_of_week", nullable = false, length = 9)
    private String dayOfWeek;

    /**
     * Hora local de atencion. La columna es {@code TIME} sin zona, que es
     * lo que corresponde a §4: son horas de atencion, no instantes.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

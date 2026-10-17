package com.um.catalogsync.schedule.infrastructure.persistence.mapper;

import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.infrastructure.persistence.entity.WeeklyScheduleEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre la entidad JPA y el modelo de dominio.
 *
 * <p>Ademas de copiar campos, convierte el dia de la semana en los dos
 * sentidos. Ese es el borde donde el texto se vuelve enum: al ir al dominio
 * pasa por {@link DayOfWeek#fromContract(String)}, asi que un valor que no
 * sea uno de los siete falla con {@code UnknownDayOfWeekException} en lugar
 * de colarse como {@code null}; al ir a la entidad se escribe
 * {@code name()}, que es exactamente uno de los literales del CHECK de la
 * tabla.
 *
 * <p>Las horas no necesitan conversion: tanto el dominio como la entidad
 * usan {@code LocalTime}, y la columna es {@code TIME}. {@code TimeOfDay}
 * se usa un borde mas afuera, cuando la hora llega como texto del snapshot
 * o de Redis (#12, #24); aca ya es un tipo.
 */
@Component
public class WeeklyScheduleMapper {

    public WeeklySchedule toDomain(WeeklyScheduleEntity entity) {
        if (entity == null) {
            return null;
        }
        return WeeklySchedule.builder()
                .id(entity.getId())
                .professionalId(entity.getProfessionalId())
                .dayOfWeek(DayOfWeek.fromContract(entity.getDayOfWeek()))
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .slotDurationMinutes(entity.getSlotDurationMinutes())
                .enabled(entity.isEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public WeeklyScheduleEntity toEntity(WeeklySchedule schedule) {
        if (schedule == null) {
            return null;
        }
        return WeeklyScheduleEntity.builder()
                .id(schedule.getId())
                .professionalId(schedule.getProfessionalId())
                .dayOfWeek(schedule.getDayOfWeek() == null ? null : schedule.getDayOfWeek().name())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .slotDurationMinutes(schedule.getSlotDurationMinutes())
                .enabled(schedule.isEnabled())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }

    public List<WeeklySchedule> toDomain(Collection<WeeklyScheduleEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(this::toDomain).toList();
    }

    public List<WeeklyScheduleEntity> toEntity(Collection<WeeklySchedule> schedules) {
        if (schedules == null) {
            return List.of();
        }
        return schedules.stream().map(this::toEntity).toList();
    }
}

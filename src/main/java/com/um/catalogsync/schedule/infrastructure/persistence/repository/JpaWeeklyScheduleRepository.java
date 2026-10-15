package com.um.catalogsync.schedule.infrastructure.persistence.repository;

import com.um.catalogsync.schedule.infrastructure.persistence.entity.WeeklyScheduleEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data. Detalle de infraestructura, no el puerto: el
 * puerto del dominio es {@code WeeklyScheduleRepository}.
 */
public interface JpaWeeklyScheduleRepository extends JpaRepository<WeeklyScheduleEntity, Long> {

    /**
     * Aprovecha el indice {@code ix_weekly_schedule_professional_enabled},
     * cuya primera columna es {@code professional_id}. Sin {@code Sort}: el
     * orden de la agenda lo pone el caso de uso, porque ordenar por el
     * texto del dia daria el orden alfabetico.
     */
    List<WeeklyScheduleEntity> findByProfessionalId(Long professionalId);

    long deleteByIdIn(Collection<Long> ids);
}

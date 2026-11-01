package com.um.catalogsync.schedule.infrastructure.persistence.repository;

import com.um.catalogsync.schedule.infrastructure.persistence.entity.WeeklyScheduleEntity;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Ids de profesionales con al menos un horario habilitado en alguno de los
     * dias pedidos y dentro de la franja pedida (#16).
     *
     * <p><b>El rango se cruza por solapamiento:</b> el horario sirve si
     * empieza antes de que la franja termine y termina despues de que la
     * franja empiece. Las desigualdades son estrictas, asi que un horario que
     * acaba justo cuando la franja arranca no cuenta, porque la interseccion
     * duraria cero.
     *
     * <p><b>Los tres parametros son obligatorios y por eso la consulta tiene
     * una sola forma.</b> No hay ningun {@code :param IS NULL OR ...}: "sin
     * filtro de dia" llega como los siete dias y "sin franja" como la jornada
     * completa, que {@code AvailabilityCriteria} resuelve. Esto no es solo
     * prolijidad —PostgreSQL rechaza un {@code ? IS NULL} sobre un parametro
     * cuyo tipo no puede inferir, y con las horas pasa exactamente eso: la
     * consulta con el patron anulable falla con "could not determine data type
     * of parameter"—. Tambien le deja al planificador una sola forma de
     * consulta para cachear en lugar de ocho.
     *
     * <p>El dia se compara como texto porque la columna es {@code VARCHAR}:
     * ver el javadoc de {@code WeeklyScheduleEntity.dayOfWeek}.
     *
     * <p>Sin indice que le sirva: filtra por {@code day_of_week} y las horas,
     * y el unico indice de la tabla arranca por {@code professional_id}. Es
     * deliberado no agregar otro: la tabla esta acotada por profesionales por
     * dias de atencion, que en un catalogo de catedra son decenas de filas, y
     * un indice que no se necesita es una escritura mas en cada
     * sincronizacion. Si el catalogo creciera, lo que corresponde es un indice
     * por {@code (enabled, day_of_week)} y no reescribir esto.
     */
    @Query(
            """
            SELECT DISTINCT h.professionalId
            FROM WeeklyScheduleEntity h
            WHERE h.enabled = true
              AND h.dayOfWeek IN :dias
              AND h.endTime > :desde
              AND h.startTime < :hasta
            """)
    List<Long> findProfessionalIdsWithEnabledScheduleIn(
            @Param("dias") Collection<String> dias,
            @Param("desde") LocalTime desde,
            @Param("hasta") LocalTime hasta);
}

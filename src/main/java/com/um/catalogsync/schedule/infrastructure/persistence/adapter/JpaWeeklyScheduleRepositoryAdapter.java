package com.um.catalogsync.schedule.infrastructure.persistence.adapter;

import com.um.catalogsync.schedule.application.exception.UnknownProfessionalException;
import com.um.catalogsync.schedule.domain.model.AvailabilityCriteria;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.schedule.infrastructure.persistence.entity.WeeklyScheduleEntity;
import com.um.catalogsync.schedule.infrastructure.persistence.mapper.WeeklyScheduleMapper;
import com.um.catalogsync.schedule.infrastructure.persistence.repository.JpaWeeklyScheduleRepository;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el puerto de salida con JPA.
 *
 * <p>Sigue el mismo patron que {@code JpaProfessionalRepositoryAdapter}, por
 * las mismas dos razones.
 *
 * <p><b>Hace flush explicito en las escrituras.</b> JPA puede postergar el
 * INSERT hasta el commit de la transaccion, y el commit ocurre despues de
 * que el metodo retorna, fuera del {@code try}. Sin el flush, la violacion
 * de FK estallaria al commitear y este {@code catch} no la veria nunca, asi
 * que la traduccion a {@code UnknownProfessionalException} no funcionaria.
 *
 * <p><b>Traduce la violacion de FK a una excepcion de dominio.</b> Sin eso,
 * quien sincroniza recibiria un error de integridad generico y tendria que
 * interpretar un mensaje de PostgreSQL para entender que falto.
 */
@Component
@RequiredArgsConstructor
public class JpaWeeklyScheduleRepositoryAdapter implements WeeklyScheduleRepository {

    /**
     * Nombre de la FK en {@code V1__catalogo_local.sql}. Hoy es la unica de
     * esta tabla, asi que el SQLSTATE bastaria; se compara igual por nombre
     * para no tener que recordar esta sutileza si mañana aparece otra, que
     * es el problema que el adaptador de profesionales ya tiene.
     */
    private static final String FK_PROFESIONAL = "fk_weekly_schedule_professional";

    /** {@code foreign_key_violation} en el estandar SQL. */
    private static final String SQLSTATE_FK = "23503";

    private final JpaWeeklyScheduleRepository jpaRepository;
    private final WeeklyScheduleMapper mapper;

    @Override
    @Transactional
    public WeeklySchedule upsert(WeeklySchedule schedule) {
        try {
            WeeklyScheduleEntity guardado =
                    jpaRepository.saveAndFlush(mapper.toEntity(schedule));
            return mapper.toDomain(guardado);
        } catch (DataIntegrityViolationException e) {
            throw traducir(e, schedule.getProfessionalId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklySchedule> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklySchedule> findByProfessionalId(Long professionalId) {
        if (professionalId == null) {
            return List.of();
        }
        return mapper.toDomain(jpaRepository.findByProfessionalId(professionalId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklySchedule> findAll() {
        return mapper.toDomain(jpaRepository.findAll());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lo unico que agrega sobre la consulta es traducir los dias del enum
     * de dominio al texto de la columna. La traduccion pasa por
     * {@link DayOfWeek#name()} y no por {@code toString()}: el nombre de la
     * constante es el valor del contrato y el que el CHECK de la tabla
     * acepta, mientras que {@code toString} podria sobrescribirse sin que
     * nada avisara.
     *
     * <p>No tiene ninguna rama. Los casos "sin dia" y "sin franja" ya llegan
     * resueltos como los siete dias y la jornada completa, porque el criterio
     * de dominio los expresa asi: ver {@code AvailabilityCriteria.diasPedidos}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> findProfessionalIdsWithAgendaIn(AvailabilityCriteria criteria) {
        return jpaRepository.findProfessionalIdsWithEnabledScheduleIn(
                criteria.diasPedidos().stream().map(DayOfWeek::name).toList(),
                criteria.franjaDesde(),
                criteria.franjaHasta());
    }

    @Override
    @Transactional
    public List<WeeklySchedule> replaceAll(Collection<WeeklySchedule> schedules) {
        jpaRepository.deleteAllInBatch();
        try {
            List<WeeklyScheduleEntity> guardados =
                    jpaRepository.saveAll(mapper.toEntity(schedules));
            // Un solo flush para todo el lote, no uno por entidad.
            jpaRepository.flush();
            return mapper.toDomain(guardados);
        } catch (DataIntegrityViolationException e) {
            throw traducir(e, null);
        }
    }

    @Override
    @Transactional
    public long deleteByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        return jpaRepository.deleteByIdIn(ids);
    }

    /**
     * Convierte la violacion en {@code UnknownProfessionalException} solo si
     * es la FK hacia profesional. Cualquier otra violacion —los CHECK de
     * rango horario y de duracion, por ejemplo— se propaga tal cual:
     * inventarle un significado seria peor que dejarla pasar.
     *
     * @param professionalId el profesional involucrado si se conoce;
     *     {@code null} en operaciones de lote, donde no se sabe cual de los
     *     horarios fallo
     */
    private RuntimeException traducir(DataIntegrityViolationException e, Long professionalId) {
        return esViolacionDe(e, FK_PROFESIONAL)
                ? new UnknownProfessionalException(professionalId, e)
                : e;
    }

    private static boolean esViolacionDe(Throwable error, String nombreDeLaFk) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql
                    && SQLSTATE_FK.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains(nombreDeLaFk)) {
                return true;
            }
            if (causa.getCause() == causa) {
                return false;
            }
        }
        return false;
    }
}

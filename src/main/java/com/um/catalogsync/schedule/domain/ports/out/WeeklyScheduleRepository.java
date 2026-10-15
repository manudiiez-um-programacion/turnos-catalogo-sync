package com.um.catalogsync.schedule.domain.ports.out;

import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida del slice de horarios semanales.
 *
 * <p>Misma forma que los puertos de categorias y profesionales, y por el
 * mismo motivo: la unica escritura viene de la sincronizacion, asi que
 * expone {@code upsert} y {@code replaceAll} en lugar de CRUD.
 *
 * <p>Las escrituras exigen que el profesional del horario ya exista
 * localmente. Ver {@code UnknownProfessionalException} para el porque: es
 * el mismo razonamiento que con las categorias de los profesionales.
 *
 * <p><b>Lo que este puerto no promete.</b> Ningun metodo garantiza orden.
 * El orden de la agenda es una decision de dominio
 * ({@code WeeklySchedule.BY_DAY_AND_START}) y la cumple el caso de uso, no
 * el adaptador. Tampoco traduce las violaciones de los CHECK de la tabla
 * —{@code end_time > start_time} y {@code slot_duration_minutes > 0}—: esos
 * datos el contrato los manda siempre validos, y si alguna vez no lo hace,
 * la violacion de integridad generica es suficiente para que la version
 * local no avance.
 */
public interface WeeklyScheduleRepository {

    /**
     * Inserta el horario o lo reemplaza si ya existe, decidiendo por id.
     *
     * @throws com.um.catalogsync.schedule.application.exception.UnknownProfessionalException
     *     si el profesional del horario no esta en la copia local
     */
    WeeklySchedule upsert(WeeklySchedule schedule);

    /** @return el horario, o vacio si no esta. Nunca {@code null}. */
    Optional<WeeklySchedule> findById(Long id);

    /**
     * Horarios de un profesional, sin orden garantizado.
     *
     * @return lista vacia si el profesional no tiene horarios o no existe:
     *     consultar la agenda de alguien que no esta no es un error de este
     *     puerto
     */
    List<WeeklySchedule> findByProfessionalId(Long professionalId);

    List<WeeklySchedule> findAll();

    /**
     * Reemplaza por completo el contenido local de horarios.
     *
     * <p>Es el extremo "hijo" de las FK del catalogo: los horarios son lo
     * primero que se borra y lo ultimo que se escribe al aplicar un
     * snapshot. Ese orden entre slices lo decide quien orquesta la
     * sincronizacion (#13).
     *
     * @throws com.um.catalogsync.schedule.application.exception.UnknownProfessionalException
     *     si algun horario referencia un profesional que no esta
     */
    List<WeeklySchedule> replaceAll(Collection<WeeklySchedule> schedules);

    long deleteByIdIn(Collection<Long> ids);
}

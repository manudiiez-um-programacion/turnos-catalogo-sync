package com.um.catalogsync.schedule.domain.ports.out;

import com.um.catalogsync.schedule.domain.model.AvailabilityCriteria;
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
     * Los ids de los profesionales que tienen agenda en el dia o el rango
     * pedidos (#16).
     *
     * <p><b>Devuelve ids y no horarios</b> porque quien pregunta es la
     * busqueda de profesionales, que necesita saber <i>a quienes</i> incluir
     * y no cuando atienden. Traer los horarios enteros para despues
     * descartarlos seria mover filas por nada, y la consulta puede responder
     * con un {@code DISTINCT} sobre una sola columna.
     *
     * <p><b>Solo mira horarios habilitados.</b> Es la mitad del criterio de
     * disponibilidad que le toca a este slice: un horario deshabilitado es
     * una baja logica que la copia conserva (§14.4) pero que no es agenda
     * vigente. La otra mitad —que el profesional tambien este habilitado— la
     * aplica el slice de profesionales, que es su dueño.
     *
     * <p><b>Que esto no es.</b> No es disponibilidad de turnos: responde
     * "tiene agenda", no "tiene cupo libre". Ver {@link AvailabilityCriteria}
     * para el criterio completo y por que no puede ser otro.
     *
     * @return los ids, sin repetir y sin orden garantizado. Lista vacia si
     *     nadie tiene agenda ahi, que es una respuesta valida y no un error
     */
    List<Long> findProfessionalIdsWithAgendaIn(AvailabilityCriteria criteria);

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

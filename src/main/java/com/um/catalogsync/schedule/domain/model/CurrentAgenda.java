package com.um.catalogsync.schedule.domain.model;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import java.util.List;

/**
 * La agenda vigente de un profesional: lo unico que este servicio le expone al
 * servicio de turnos (§3, §4.1).
 *
 * <p>Son tres cosas juntas porque las tres hacen falta para que turnos pueda
 * decidir: <b>quien</b> es el profesional, <b>cuando</b> atiende, y <b>sobre
 * que copia</b> del catalogo se respondio. Lo ultimo es lo que §4.2 necesita
 * para que turnos no confunda un dato vigente con uno historico: si la version
 * cambio entre dos consultas, turnos lo sabe sin preguntar.
 *
 * <p><b>Que no es.</b> No es disponibilidad. Devuelve las reglas de agenda
 * —que dias y en que franjas atiende el profesional— y nada sobre ocupaciones,
 * bloqueos ni reservas, que son del servicio de turnos (§4.2). La diferencia
 * importa en el nombre de las cosas: esto responde "tiene agenda", no "tiene
 * cupo libre".
 *
 * <p>Vive en el {@code domain} de {@code schedule} porque el ADR 0001 le
 * asigna a este slice "el armado de la agenda vigente que consume el servicio
 * de turnos". Importa el modelo de dominio de otros dos slices, que es lo que
 * la regla de dependencias permite: lo prohibido entre slices es el
 * {@code infrastructure} ajeno.
 *
 * @param professional el profesional, tal como esta en la copia local
 * @param schedules sus horarios <b>habilitados</b>, ordenados por dia y hora
 * @param catalogVersion la version local con la que se respondio
 */
public record CurrentAgenda(
        Professional professional, List<WeeklySchedule> schedules, CatalogVersion catalogVersion) {

    public CurrentAgenda {
        schedules = schedules == null ? List.of() : List.copyOf(schedules);
    }

    /**
     * Arma la agenda dejando afuera los horarios deshabilitados y ordenando el
     * resto.
     *
     * <p><b>El filtro va aca y no en una consulta SQL.</b> La copia local
     * guarda los horarios deshabilitados porque el snapshot los trae y hay que
     * reflejar el catalogo completo; lo que no corresponde es publicarlos como
     * agenda vigente. Teniendolo en un solo metodo del dominio, el filtro se
     * prueba sin base de datos y no puede quedar distinto entre dos consultas.
     *
     * <p>El orden es parte del contrato: una respuesta cuyo orden cambia entre
     * llamadas obliga al cliente a reordenar y rompe cualquier comparacion de
     * esquema.
     */
    public static CurrentAgenda of(
            Professional professional, List<WeeklySchedule> schedules, CatalogVersion catalogVersion) {

        List<WeeklySchedule> habilitados =
                schedules.stream()
                        .filter(WeeklySchedule::isEnabled)
                        .sorted(WeeklySchedule.BY_DAY_AND_START)
                        .toList();

        return new CurrentAgenda(professional, habilitados, catalogVersion);
    }
}

package com.um.catalogsync.schedule.application.service;

import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.in.GetSchedulesByProfessionalUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade del slice de horarios semanales.
 *
 * <p>Por ahora compone un solo caso de uso: es lo que el issue #8 pide y lo
 * minimo para que el slice sea usable. La API de agenda vigente que consume
 * el servicio de turnos (#18) y el filtro de disponibilidad (#16) se suman
 * aca, y ese es justamente el valor de la fachada: el controlador va a
 * hablar con esta clase y no con los casos de uso sueltos.
 *
 * <p>A diferencia de los otros dos slices, no traduce ningun
 * {@code Optional} vacio a excepcion: pedir la agenda de un profesional sin
 * horarios no es un error, es una agenda vacia. Que el profesional exista o
 * no lo responde el slice de profesionales, que es su dueño.
 */
@Service
@RequiredArgsConstructor
public class WeeklyScheduleService {

    private final GetSchedulesByProfessionalUseCase getSchedulesByProfessional;

    /**
     * @return la agenda del profesional ordenada por dia y hora de inicio;
     *     vacia si no tiene horarios
     */
    public List<WeeklySchedule> getByProfessional(Long professionalId) {
        return getSchedulesByProfessional.getByProfessional(professionalId);
    }
}

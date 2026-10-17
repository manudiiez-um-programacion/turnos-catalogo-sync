package com.um.catalogsync.schedule.domain.ports.in;

import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import java.util.List;

/** Puerto de entrada: la agenda semanal de un profesional. */
public interface GetSchedulesByProfessionalUseCase {

    /**
     * @return los horarios del profesional ordenados por dia de la semana y
     *     hora de inicio, o lista vacia si no tiene ninguno. El orden es
     *     parte del contrato de este puerto, no una casualidad del
     *     adaptador.
     */
    List<WeeklySchedule> getByProfessional(Long professionalId);
}

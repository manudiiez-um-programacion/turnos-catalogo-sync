package com.um.catalogsync.schedule.application.usecases;

import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.in.GetSchedulesByProfessionalUseCase;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Devuelve la agenda semanal de un profesional desde la copia local. Nunca
 * consulta al servicio central.
 *
 * <p>El orden lo pone este caso de uso y no la consulta SQL. No es una
 * preferencia: {@code day_of_week} es texto en la base, asi que un
 * {@code ORDER BY} sobre esa columna daria el orden alfabetico —viernes
 * antes que lunes— en lugar del de la semana. Ordenar con
 * {@link WeeklySchedule#BY_DAY_AND_START} usa el {@code ordinal} del enum,
 * que si es cronologico, y deja la garantia del lado del dominio: vale
 * aunque manana el adaptador sea otro.
 *
 * <p>Ordenar en memoria no es un problema de escala: son los horarios de un
 * solo profesional, como maximo unas pocas decenas de filas.
 */
@Component
@RequiredArgsConstructor
public class GetSchedulesByProfessionalUseCaseImpl implements GetSchedulesByProfessionalUseCase {

    private final WeeklyScheduleRepository weeklyScheduleRepository;

    @Override
    public List<WeeklySchedule> getByProfessional(Long professionalId) {
        return weeklyScheduleRepository.findByProfessionalId(professionalId).stream()
                .sorted(WeeklySchedule.BY_DAY_AND_START)
                .toList();
    }
}

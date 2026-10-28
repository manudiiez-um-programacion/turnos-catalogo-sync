package com.um.catalogsync.schedule.application.usecases;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.application.exception.CatalogNotSynchronizedException;
import com.um.catalogsync.schedule.application.exception.ProfessionalDisabledException;
import com.um.catalogsync.schedule.domain.model.CurrentAgenda;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.in.GetCurrentAgendaUseCase;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma la agenda vigente leyendo <b>solo</b> la copia local.
 *
 * <p>Nunca consulta a la catedra, ni por REST ni por Redis: es una regla no
 * negociable del proyecto y la razon de ser de este servicio. Los tres puertos
 * que usa son de persistencia.
 *
 * <p><b>Por que importa puertos de otros dos slices.</b> La agenda es, por
 * definicion, informacion de tres slices: el profesional, sus horarios y la
 * version aplicada. El ADR 0001 le asigna el armado a {@code schedule}, y la
 * regla entre slices prohibe el {@code infrastructure} ajeno, no el puerto de
 * dominio: es el mismo camino que ya usa {@code sync} para escribir
 * profesionales y categorias.
 *
 * <p><b>Solo lectura y REPEATABLE READ.</b> Son tres consultas que tienen que
 * ver la misma copia: si una sincronizacion commitea en medio, la respuesta
 * podria llevar el numero de una version con los horarios de otra, que es
 * exactamente el dato con el que turnos decide.
 *
 * <p>Una transaccion sola no alcanza para evitarlo. El aislamiento por defecto
 * de PostgreSQL es READ COMMITTED, y ahi <b>cada sentencia</b> toma su propia
 * instantanea: entre leer la version y leer los horarios, el commit del
 * snapshot (#13) se vuelve visible y la mezcla ocurre igual. Con REPEATABLE
 * READ la instantanea se toma una sola vez, en la primera consulta, y las tres
 * ven el mismo estado. Es lo que permite documentar una garantia de
 * consistencia en el contrato (#20) en lugar de una intencion.
 *
 * <p>El costo es nulo en la practica: PostgreSQL implementa REPEATABLE READ con
 * MVCC, sin bloqueos de lectura, y los errores de serializacion que ese nivel
 * puede producir son solo para transacciones que escriben. Esta no escribe.
 */
@Component
@RequiredArgsConstructor
public class GetCurrentAgendaUseCaseImpl implements GetCurrentAgendaUseCase {

    private final ProfessionalRepository profesionales;
    private final WeeklyScheduleRepository horarios;
    private final CatalogVersionRepository versiones;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CurrentAgenda getByProfessional(Long professionalId) {

        // Primero la version: si no hay copia local, cualquier otra respuesta
        // seria enganosa. Un 404 diria que el profesional no existe cuando lo
        // que no existe es la copia.
        CatalogVersion version =
                versiones.findCurrent().orElseThrow(CatalogNotSynchronizedException::new);

        Professional profesional =
                profesionales
                        .findById(professionalId)
                        .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        if (!profesional.isEnabled()) {
            throw new ProfessionalDisabledException(professionalId);
        }

        List<WeeklySchedule> suyos = horarios.findByProfessionalId(professionalId);

        return CurrentAgenda.of(profesional, suyos, version);
    }
}

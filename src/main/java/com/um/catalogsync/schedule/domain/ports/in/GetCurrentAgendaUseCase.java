package com.um.catalogsync.schedule.domain.ports.in;

import com.um.catalogsync.schedule.domain.model.CurrentAgenda;

/** Devuelve la agenda vigente de un profesional, para el servicio de turnos. */
public interface GetCurrentAgendaUseCase {

    /**
     * @param professionalId id del profesional en el catalogo de la catedra
     * @return la agenda vigente, nunca {@code null}
     * @throws com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException
     *     si no esta en la copia local
     * @throws com.um.catalogsync.schedule.application.exception.ProfessionalDisabledException si
     *     esta pero deshabilitado
     * @throws com.um.catalogsync.schedule.application.exception.CatalogNotSynchronizedException si
     *     todavia no hay una version local aplicada
     */
    CurrentAgenda getByProfessional(Long professionalId);
}

package com.um.catalogsync.professional.infrastructure.web.mapper;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchResult;
import com.um.catalogsync.professional.infrastructure.web.dto.ProfessionalSearchResponse;
import com.um.catalogsync.professional.infrastructure.web.dto.ProfessionalSummaryResponse;
import com.um.catalogsync.shared.infrastructure.web.dto.CatalogVersionResponse;
import com.um.catalogsync.shared.infrastructure.web.dto.PageResponse;
import org.springframework.stereotype.Component;

/**
 * Del modelo de dominio al DTO de respuesta de la busqueda.
 *
 * <p>Existe por lo mismo que {@code CurrentAgendaWebMapper}: que el
 * controlador no sepa de dominio y el dominio no sepa de HTTP. Lo que gana en
 * concreto es que agregar un campo al modelo local no cambia lo que ve la app
 * KMP, y que el orden de las filas se preserva tal como lo dejo la consulta
 * —el mapper no reordena nada, porque el orden ya es parte del contrato y
 * decidirlo dos veces es la forma de que las dos decisiones se separen—.
 *
 * <p>Solo tiene direccion de salida. Lo que entra son parametros de query
 * string, y traducirlos al criterio de dominio es trabajo del propio
 * {@code ProfessionalSearchRequest}: es una conversion sin colaboradores, y
 * tenerla en el record la deja al lado de los valores por defecto que
 * resuelve.
 */
@Component
public class ProfessionalSearchWebMapper {

    public ProfessionalSearchResponse toResponse(ProfessionalSearchResult resultado) {
        return new ProfessionalSearchResponse(
                resultado.page().content().stream().map(this::toResponse).toList(),
                toResponse(resultado.page()),
                CatalogVersionResponse.from(resultado.catalogVersion()));
    }

    private ProfessionalSummaryResponse toResponse(Professional profesional) {
        return new ProfessionalSummaryResponse(
                profesional.getId(),
                profesional.getCategoryId(),
                profesional.getFirstName(),
                profesional.getLastName(),
                profesional.isEnabled());
    }

    private PageResponse toResponse(ProfessionalPage pagina) {
        return new PageResponse(
                pagina.page(), pagina.size(), pagina.totalElements(), pagina.totalPages());
    }
}

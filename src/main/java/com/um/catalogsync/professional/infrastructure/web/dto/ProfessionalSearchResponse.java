package com.um.catalogsync.professional.infrastructure.web.dto;

import com.um.catalogsync.shared.infrastructure.web.dto.CatalogVersionResponse;
import com.um.catalogsync.shared.infrastructure.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El resultado de una busqueda de profesionales.
 *
 * <p><b>La version de catalogo viaja en la respuesta</b> y es un criterio de
 * aceptacion de #15: le dice al cliente sobre que copia local se resolvio esta
 * busqueda. Sirve para dos cosas concretas en la app KMP —saber si dos
 * pantallas estan mirando el mismo catalogo, y poder invalidar una cache
 * propia cuando el numero cambia— sin tener que preguntar nada mas.
 *
 * <p>Va una vez por respuesta y no por profesional: es el estado de la copia
 * entera, no un atributo de cada fila.
 *
 * @param content los profesionales de esta pagina, ordenados por apellido,
 *     nombre e id. Puede venir vacia: una combinacion de filtros que no
 *     matchea nada es una respuesta correcta
 * @param page metadatos de paginado
 * @param catalogVersion la version local con la que se resolvio la busqueda
 */
@Schema(
        name = "ProfessionalSearchResult",
        description =
                "Pagina de profesionales de la copia local, con la version de catalogo con la"
                        + " que se resolvio. Nunca consulta a la catedra.")
public record ProfessionalSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                List<ProfessionalSummaryResponse> content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PageResponse page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CatalogVersionResponse catalogVersion) {}

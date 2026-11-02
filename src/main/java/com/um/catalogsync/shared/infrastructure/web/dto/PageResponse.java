package com.um.catalogsync.shared.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Los metadatos de una pagina de resultados.
 *
 * <p><b>Por que no se devuelve el {@code Page} de Spring Data serializado.</b>
 * Su JSON incluye el objeto {@code pageable} entero, con {@code offset},
 * {@code paged}, {@code unpaged} y el {@code sort} desglosado: una decena de
 * campos que son estructura interna de una libreria y que cambiaron de forma
 * entre versiones. Eso convertiria una actualizacion de Spring Data en un
 * cambio de contrato para la app KMP. Spring Boot incluso avisa de esto por
 * log y pide no hacerlo. Cuatro campos propios y estables es lo que el cliente
 * necesita.
 *
 * <p>Vive en {@code shared} porque es paginado, no profesionales: lo va a
 * reusar cualquier listado que se agregue.
 *
 * @param number numero de pagina devuelta, base cero
 * @param size tamaño de pagina pedido
 * @param totalElements cuantos elementos matchean los filtros en total
 * @param totalPages cuantas paginas hay con ese tamaño
 */
@Schema(name = "PageMetadata", description = "Metadatos de paginado")
public record PageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int number,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "37") long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") int totalPages) {}

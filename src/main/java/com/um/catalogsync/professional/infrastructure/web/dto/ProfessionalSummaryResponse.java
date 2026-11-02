package com.um.catalogsync.professional.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un profesional en los resultados de busqueda.
 *
 * <p><b>Lleva {@code enabled} y puede venir en {@code false}.</b> Es la
 * diferencia con el profesional de la agenda vigente, donde siempre es
 * {@code true} porque un deshabilitado no tiene agenda que devolver. Aca el
 * estado es un filtro: §14.4 dice que una entidad deshabilitada es una baja
 * logica que la copia local conserva, y el cliente tiene que poder pedirla y
 * reconocerla.
 *
 * <p><b>No lleva {@code createdAt} ni {@code updatedAt}.</b> Son datos de la
 * catedra que la copia guarda, pero no sirven para elegir un profesional en un
 * listado; lo que el cliente necesita para saber que tan vigente es lo que ve
 * es la version de catalogo, que viaja una sola vez en la respuesta y no
 * repetida por fila.
 *
 * <p><b>No lleva el nombre normalizado de busqueda.</b> Es una columna de
 * apoyo del indice, no informacion: publicarla invitaria a un cliente a
 * filtrar por ella y a depender de una regla de normalizacion que es nuestra y
 * puede cambiar.
 *
 * @param id el id del profesional en el catalogo de la catedra
 * @param categoryId su categoria profesional
 * @param firstName nombre
 * @param lastName apellido
 * @param enabled si esta habilitado en el catalogo de la catedra
 */
@Schema(name = "Professional", description = "Profesional de la copia local del catalogo")
public record ProfessionalSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") Long categoryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ana") String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Gomez") String lastName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean enabled) {}

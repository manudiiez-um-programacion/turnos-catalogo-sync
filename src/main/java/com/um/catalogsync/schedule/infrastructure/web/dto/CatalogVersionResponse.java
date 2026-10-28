package com.um.catalogsync.schedule.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * La version local de catalogo con la que se resolvio la respuesta.
 *
 * <p>Es lo que le permite al servicio de turnos saber sobre que copia esta
 * trabajando (§4.2): si entre dos consultas el numero cambio, los datos pueden
 * no ser comparables, y lo sabe sin preguntar nada.
 *
 * @param version numero de version del catalogo de la catedra
 * @param appliedAt cuando esta copia termino de aplicarla, en UTC
 * @param source si llego por snapshot completo o por cambios incrementales
 */
@Schema(name = "AppliedCatalogVersion", description = "Version local de catalogo aplicada")
public record CatalogVersionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "128") long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-27T18:30:00Z")
                Instant appliedAt,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"SNAPSHOT", "INCREMENTAL"},
                        example = "SNAPSHOT")
                String source) {}

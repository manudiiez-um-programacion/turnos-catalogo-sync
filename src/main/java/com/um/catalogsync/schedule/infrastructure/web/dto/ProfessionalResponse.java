package com.um.catalogsync.schedule.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos vigentes del profesional.
 *
 * <p>No incluye {@code createdAt} ni {@code updatedAt}: son del registro local
 * —cuando esta copia escribio la fila— y no significan nada para el servicio de
 * turnos, que lo que necesita saber es sobre que version de catalogo esta
 * trabajando. Para eso esta {@code catalogVersion}.
 *
 * @param id el id del profesional en el catalogo de la catedra
 * @param categoryId su categoria profesional
 * @param firstName nombre
 * @param lastName apellido
 * @param enabled si esta habilitado; en esta respuesta siempre {@code true}
 */
@Schema(name = "AgendaProfessional", description = "Datos vigentes del profesional")
public record ProfessionalResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") Long categoryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ana") String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Gomez") String lastName,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description =
                                "Siempre true: un profesional deshabilitado no devuelve agenda,"
                                        + " devuelve 409 PROFESSIONAL_DISABLED. Viaja igual para que"
                                        + " el contrato sea explicito.")
                boolean enabled) {}

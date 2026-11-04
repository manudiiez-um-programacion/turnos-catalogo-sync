package com.um.catalogsync.sync.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Un profesional del snapshot (§7).
 *
 * <p>La categoria viene como {@code categoryId} y no anidada: es lo que
 * manda el contrato y es lo que el modelo de dominio guarda, asi que no hay
 * nada que aplanar en el mapper.
 *
 * <p>No trae el nombre normalizado para busqueda, y no deberia: es un dato
 * derivado que calcula el borde de persistencia con {@code SearchableName}.
 * Si viniera de acá seria un campo mas que puede quedar desincronizado del
 * nombre real.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfessionalDto(
        Long id,
        Long categoryId,
        String firstName,
        String lastName,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}

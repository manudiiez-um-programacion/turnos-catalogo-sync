package com.um.catalogsync.sync.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Una categoria profesional del snapshot (§7).
 *
 * <p>{@code enabled} es {@code Boolean} y no {@code boolean} por el mismo
 * motivo que {@code snapshotVersion} es {@code Long}: el primitivo
 * convertiria un campo ausente en {@code false}, que es un valor con
 * significado propio —categoria deshabilitada— y haria desaparecer la
 * categoria de la busqueda sin que nada avise. Envuelto, la ausencia llega
 * como {@code null} y el mapper la rechaza.
 *
 * <p>{@code description} es el unico campo que puede faltar legitimamente:
 * es el unico que la tabla local no declara {@code NOT NULL}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfessionalCategoryDto(
        Long id,
        String name,
        String description,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}

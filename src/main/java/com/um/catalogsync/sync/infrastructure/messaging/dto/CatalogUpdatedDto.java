package com.um.catalogsync.sync.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * El JSON de {@code CatalogUpdated} en el topic de catalogo (§15.3).
 *
 * <p>Es la frontera, igual que los DTOs de los caminos REST y Redis: los
 * nombres son los del contrato y los tipos admiten lo que el mensaje puede
 * traer, incluido no traerlo. Quien no tolera huecos es el modelo de
 * dominio, y eso lo decide el mapper.
 *
 * <p><b>Por que {@code Long} y no {@code long} en los dos numeros.</b> Con el
 * primitivo, un {@code newVersion} ausente llegaria como {@code 0}, que es
 * una version valida y la mas peligrosa: diria que la catedra publico el
 * origen de los tiempos. Y un {@code schemaVersion} ausente llegaria como
 * {@code 0}, que al compararlo contra {@code 1} daria el error correcto por
 * el motivo equivocado —"version de esquema 0" en lugar de "falta el
 * campo"—.
 *
 * <p>{@code ignoreUnknown} porque §15.1 permite agregar campos opcionales
 * dentro de v1 y obliga a los consumidores a ignorar los desconocidos. Es el
 * requisito mas facil de incumplir sin darse cuenta: un campo nuevo tumbaria
 * el consumo del topic entero.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogUpdatedDto(
        String eventId,
        String eventType,
        Long newVersion,
        Instant occurredAt,
        String source,
        Long schemaVersion) {
}

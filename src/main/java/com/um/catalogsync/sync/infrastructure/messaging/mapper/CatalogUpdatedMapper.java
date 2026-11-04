package com.um.catalogsync.sync.infrastructure.messaging.mapper;

import com.um.catalogsync.sync.application.exception.MalformedCatalogEventException;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.infrastructure.messaging.dto.CatalogUpdatedDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduce el mensaje del topic de catalogo al modelo de dominio (§15.3).
 *
 * <p>Es el borde del camino de Kafka. Del lado de afuera hay un string con
 * JSON; del lado de adentro, un {@link CatalogUpdatedEvent} del que ya se
 * sabe que tiene los campos que hacen falta para decidir.
 *
 * <p><b>Que exige y por que.</b>
 *
 * <ul>
 *   <li><b>{@code schemaVersion} igual a 1.</b> §15.1 fija el valor y
 *       promete que dentro de v1 no se eliminan campos ni cambia su
 *       significado. Un numero distinto es un esquema del que no sabemos
 *       nada: procesarlo "lo mejor posible" seria interpretar campos cuyo
 *       significado ya no esta garantizado. Que el consumidor pare y lo diga
 *       es mejor que avanzar la version local con datos de un contrato que
 *       no conoce.
 *   <li><b>{@code eventType} igual a {@code CatalogUpdated}.</b> Es el unico
 *       evento del topic (§15.2). Si manana aparece otro, saltearlo con un
 *       mensaje claro es mucho mejor que tratarlo como si fuera una
 *       notificacion de catalogo.
 *   <li><b>{@code eventId}, {@code newVersion} y {@code occurredAt}
 *       presentes.</b> Sin el primero no hay deduplicacion posible (§15.1),
 *       y sin el segundo la notificacion no dice nada.
 * </ul>
 *
 * <p><b>Lo que no exige:</b> {@code source}. §15.3 lo describe con ejemplos
 * —{@code MANUAL}, {@code SCHEDULED}— y no como lista cerrada, asi que no se
 * valida ni se convierte a enum: se guarda como vino, para diagnostico.
 *
 * <p>Todas las fallas salen como {@link MalformedCatalogEventException}, que
 * el consumidor trata distinto del resto: un mensaje asi no se arregla
 * reintentando.
 */
@Component
@RequiredArgsConstructor
public class CatalogUpdatedMapper {

    /** El unico valor de esquema que el contrato define para v1 (§15.1). */
    static final long VERSION_DE_ESQUEMA = 1L;

    /** El unico evento del topic de catalogo (§15.2). */
    static final String TIPO = "CatalogUpdated";

    private final ObjectMapper jackson;

    /**
     * El evento, a partir del valor del mensaje.
     *
     * @param ubicacion topic, particion y offset, para poder encontrarlo
     * @param json el valor del mensaje
     */
    public CatalogUpdatedEvent toEvent(String ubicacion, String json) {
        CatalogUpdatedDto dto = leer(ubicacion, json);

        exigirElEsquemaConocido(ubicacion, dto.schemaVersion());
        exigirElTipoDelTopic(ubicacion, dto.eventType());
        exigir(ubicacion, "eventId", dto.eventId() != null && !dto.eventId().isBlank());
        exigir(ubicacion, "newVersion", dto.newVersion() != null);
        exigir(ubicacion, "occurredAt", dto.occurredAt() != null);

        return CatalogUpdatedEvent.builder()
                .eventId(dto.eventId())
                .eventType(dto.eventType())
                .newVersion(dto.newVersion())
                .occurredAt(dto.occurredAt())
                .source(dto.source())
                .build();
    }

    private CatalogUpdatedDto leer(String ubicacion, String json) {
        if (json == null) {
            // Un mensaje con value nulo es un tombstone: tiene sentido en un
            // topic compactado y ninguno en uno de notificaciones.
            throw new MalformedCatalogEventException(
                    "el mensaje en " + ubicacion + " no tiene contenido");
        }
        try {
            CatalogUpdatedDto dto = jackson.readValue(json, CatalogUpdatedDto.class);
            if (dto == null) {
                throw new MalformedCatalogEventException(
                        "el mensaje en " + ubicacion + " es el literal null");
            }
            return dto;
        } catch (JacksonException noEsJson) {
            throw new MalformedCatalogEventException(
                    "el mensaje en " + ubicacion + " no es un JSON que se pueda leer", noEsJson);
        }
    }

    private void exigirElEsquemaConocido(String ubicacion, Long schemaVersion) {
        if (schemaVersion == null) {
            throw new MalformedCatalogEventException(
                    "al mensaje en " + ubicacion + " le falta el campo \"schemaVersion\"");
        }
        if (schemaVersion != VERSION_DE_ESQUEMA) {
            throw new MalformedCatalogEventException(
                    "el mensaje en " + ubicacion + " dice schemaVersion " + schemaVersion
                            + " y este consumidor solo entiende la " + VERSION_DE_ESQUEMA);
        }
    }

    private void exigirElTipoDelTopic(String ubicacion, String eventType) {
        if (!TIPO.equals(eventType)) {
            throw new MalformedCatalogEventException(
                    "el mensaje en " + ubicacion + " es de tipo \"" + eventType
                            + "\" y el topic de catalogo solo publica \"" + TIPO + "\"");
        }
    }

    private void exigir(String ubicacion, String campo, boolean presente) {
        if (!presente) {
            throw new MalformedCatalogEventException(
                    "al mensaje en " + ubicacion + " le falta el campo \"" + campo + "\"");
        }
    }
}

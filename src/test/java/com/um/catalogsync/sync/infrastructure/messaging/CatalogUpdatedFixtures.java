package com.um.catalogsync.sync.infrastructure.messaging;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import java.time.Instant;

/**
 * El mensaje de ejemplo de §15.3, copiado de la referencia.
 *
 * <p>Son los valores del documento y no inventados, igual que los fixtures
 * de §7 y §14: la version 7, el {@code occurredAt} en UTC y el
 * {@code eventId} con la forma {@code catalog-event-7-4f3b}, que la
 * referencia muestra pero no promete, y por eso el codigo no la interpreta.
 */
public final class CatalogUpdatedFixtures {

    public static final String EVENT_ID = "catalog-event-7-4f3b";
    public static final long VERSION = 7L;
    public static final String OCURRIO = "2026-09-10T13:44:59Z";
    public static final String ORIGEN = "MANUAL";
    public static final String TOPIC = "catedra.catalog.proyecto-juan-perez";

    private CatalogUpdatedFixtures() {
    }

    /** El JSON literal de §15.3. */
    public static String mensaje() {
        return """
                {
                  "eventId": "catalog-event-7-4f3b",
                  "eventType": "CatalogUpdated",
                  "newVersion": 7,
                  "occurredAt": "2026-09-10T13:44:59Z",
                  "source": "MANUAL",
                  "schemaVersion": 1
                }
                """;
    }

    /**
     * Otro aviso, con su propio {@code eventId}.
     *
     * <p>Sirve para dos cosas: una secuencia de versiones, y el caso de dos
     * eventos distintos para la misma version —que §15.1 permite, porque la
     * idempotencia es por {@code eventId} y no por version—.
     */
    public static String mensaje(String eventId, long version) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "CatalogUpdated",
                  "newVersion": %d,
                  "occurredAt": "2026-09-10T13:44:59Z",
                  "source": "SCHEDULED",
                  "schemaVersion": 1
                }
                """.formatted(eventId, version);
    }

    /** El evento del ejemplo, ya traducido. */
    public static CatalogUpdatedEvent evento() {
        return evento(EVENT_ID, VERSION);
    }

    public static CatalogUpdatedEvent evento(String eventId, long version) {
        return CatalogUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("CatalogUpdated")
                .newVersion(version)
                .occurredAt(Instant.parse(OCURRIO))
                .source(ORIGEN)
                .build();
    }
}

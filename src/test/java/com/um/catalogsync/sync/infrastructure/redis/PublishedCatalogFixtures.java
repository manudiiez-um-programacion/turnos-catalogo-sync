package com.um.catalogsync.sync.infrastructure.redis;

/**
 * Las claves y los valores de ejemplo de §14, copiados de la referencia.
 *
 * <p>Son los valores del documento y no inventados, igual que
 * {@code SnapshotFixtures} con §7: asi las pruebas verifican la lectura
 * contra exactamente la forma que la catedra publica, incluidos el
 * {@code publishedAt} en UTC, las horas con segundos y el field que repite
 * el id.
 *
 * <p>La lista de cambios es la de la version 7 y nombra al profesional 15,
 * asi que los ejemplos de §14.4 y §14.5 encajan: la version que cambio es la
 * que publico a Ana Gomez.
 *
 * <p>Las entidades son <b>las mismas tres</b> que el ejemplo del snapshot
 * —categoria 1, profesional 15, horario 21— a proposito: los dos caminos del
 * contrato publican la misma forma, y que las pruebas de los dos usen los
 * mismos datos deja a la vista que es asi.
 */
public final class PublishedCatalogFixtures {

    public static final long VERSION_ACTUAL = 7L;
    public static final long VERSION_MAS_ANTIGUA = 4L;
    public static final String PUBLICADO = "2026-09-10T13:44:59Z";

    public static final long CATEGORIA_ID = 1L;
    public static final long PROFESIONAL_ID = 15L;
    public static final long HORARIO_ID = 21L;

    private PublishedCatalogFixtures() {
    }

    /** El ejemplo literal de §14.4 para {@code professional-categories}. */
    public static String categoria() {
        return """
                {
                  "id": 1,
                  "name": "Clinica medica",
                  "description": "Atencion clinica general",
                  "enabled": true,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:40:00Z"
                }
                """;
    }

    /** El ejemplo literal de §14.4 para {@code professionals}. */
    public static String profesional() {
        return """
                {
                  "id": 15,
                  "categoryId": 1,
                  "firstName": "Ana",
                  "lastName": "Gomez",
                  "enabled": true,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:40:00Z"
                }
                """;
    }

    /** El ejemplo literal de §14.4 para {@code weekly-schedules}. */
    public static String horario() {
        return """
                {
                  "id": 21,
                  "professionalId": 15,
                  "dayOfWeek": "MONDAY",
                  "startTime": "09:00:00",
                  "endTime": "12:00:00",
                  "slotDurationMinutes": 30,
                  "enabled": true,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:40:00Z"
                }
                """;
    }

    /**
     * El ejemplo literal de §14.5 para {@code changes:7}.
     *
     * <p>Es el caso tipico y el que mas se malinterpreta: dos arrays vacios
     * y uno con un solo id, que es el del profesional 15 de §14.4. La
     * version cambio una entidad, no tres.
     */
    public static String cambios() {
        return """
                {
                  "version": 7,
                  "changes": {
                    "professionalCategories": [],
                    "professionals": [15],
                    "weeklySchedules": []
                  }
                }
                """;
    }

    /**
     * Una version publicada que no toco nada.
     *
     * <p>No es un caso inventado para la prueba: una version con los tres
     * arrays vacios es valida, se aplica y hace avanzar la version local
     * (§14.5). Lo que no existe es la clave ausente, y distinguir las dos
     * cosas es el criterio del issue #23.
     */
    public static String cambiosVacios(long version) {
        return """
                {
                  "version": %d,
                  "changes": {
                    "professionalCategories": [],
                    "professionals": [],
                    "weeklySchedules": []
                  }
                }
                """.formatted(version);
    }

    /**
     * Una version que toco las tres colecciones, con un campo que el
     * contrato todavia no tiene.
     *
     * <p>El campo de mas es un requisito explicito: §4 pide tolerar campos
     * nuevos compatibles, y si no se toleraran, un agregado de la catedra
     * cortaria la sincronizacion incremental entera.
     */
    public static String cambiosDeLasTres(long version) {
        return """
                {
                  "version": %d,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "changes": {
                    "professionalCategories": [1, 2],
                    "professionals": [15],
                    "weeklySchedules": [21, 22, 23]
                  }
                }
                """.formatted(version);
    }

    /**
     * Un profesional dado de baja, con un campo que el contrato todavia no
     * tiene.
     *
     * <p>Las dos cosas son requisitos explicitos: las entidades
     * deshabilitadas permanecen en los hashes y son bajas logicas (§14.4), y
     * los campos desconocidos se ignoran sin fallar (§4).
     */
    public static String profesionalDeshabilitado(long id) {
        return """
                {
                  "id": %d,
                  "categoryId": 1,
                  "firstName": "Luis",
                  "lastName": "Diaz",
                  "enabled": false,
                  "telefono": "campo que el contrato no tiene",
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:41:00Z"
                }
                """.formatted(id);
    }
}

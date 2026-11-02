package com.um.catalogsync.sync.infrastructure.client;

/**
 * Datos de prueba del snapshot, copiados del ejemplo de §7 de la referencia.
 *
 * <p>Los valores son los del documento y no inventados: asi las pruebas del
 * adaptador y del mapper verifican el parseo contra exactamente la forma que
 * la catedra documenta, incluidos el {@code snapshotVersion} 7, el
 * {@code generatedAt} en UTC y las horas con segundos.
 *
 * <p>Es el mismo criterio que {@code IntegrationFixtures} con los ejemplos
 * de §5.
 */
public final class SnapshotFixtures {

    public static final long VERSION = 7L;
    public static final String GENERATED_AT = "2026-09-10T13:44:59Z";

    public static final long CATEGORIA_ID = 1L;
    public static final long PROFESIONAL_ID = 15L;
    public static final long HORARIO_ID = 21L;

    private SnapshotFixtures() {
    }

    /**
     * El ejemplo literal de §7: una entidad por coleccion, las tres
     * habilitadas.
     */
    public static String snapshotDeLaReferencia() {
        return """
                {
                  "snapshotVersion": 7,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "professionalCategories": [
                    {
                      "id": 1,
                      "name": "Clinica medica",
                      "description": "Atencion clinica general",
                      "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z",
                      "updatedAt": "2026-09-10T13:40:00Z"
                    }
                  ],
                  "professionals": [
                    {
                      "id": 15,
                      "categoryId": 1,
                      "firstName": "Ana",
                      "lastName": "Gomez",
                      "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z",
                      "updatedAt": "2026-09-10T13:40:00Z"
                    }
                  ],
                  "weeklySchedules": [
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
                  ]
                }
                """;
    }

    /**
     * El ejemplo de §7 con una segunda entidad deshabilitada en cada
     * coleccion, campos que el contrato todavia no tiene y una hora sin
     * segundos.
     *
     * <p>Las tres cosas son requisitos explicitos y no adornos: el snapshot
     * devuelve entidades habilitadas y deshabilitadas (§7), los campos
     * desconocidos se ignoran sin fallar (§4) y las horas pueden venir como
     * {@code HH:mm} o {@code HH:mm:ss} (§4). La categoria deshabilitada
     * ademas viene sin {@code description}, que es el unico campo opcional.
     */
    public static String snapshotConEntidadesDeshabilitadas() {
        return """
                {
                  "snapshotVersion": 7,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "campoQueTodaviaNoExiste": "ignorame",
                  "professionalCategories": [
                    {
                      "id": 1,
                      "name": "Clinica medica",
                      "description": "Atencion clinica general",
                      "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z",
                      "updatedAt": "2026-09-10T13:40:00Z"
                    },
                    {
                      "id": 2,
                      "name": "Dermatologia",
                      "enabled": false,
                      "createdAt": "2026-07-01T12:00:00Z",
                      "updatedAt": "2026-09-10T13:41:00Z",
                      "colorDeLaFicha": "#cafe00"
                    }
                  ],
                  "professionals": [
                    {
                      "id": 15,
                      "categoryId": 1,
                      "firstName": "Ana",
                      "lastName": "Gomez",
                      "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z",
                      "updatedAt": "2026-09-10T13:40:00Z"
                    },
                    {
                      "id": 16,
                      "categoryId": 2,
                      "firstName": "Luis",
                      "lastName": "Diaz",
                      "enabled": false,
                      "createdAt": "2026-07-01T12:00:00Z",
                      "updatedAt": "2026-09-10T13:42:00Z",
                      "telefono": "no deberia estar"
                    }
                  ],
                  "weeklySchedules": [
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
                    },
                    {
                      "id": 22,
                      "professionalId": 16,
                      "dayOfWeek": "SATURDAY",
                      "startTime": "08:30",
                      "endTime": "11:30",
                      "slotDurationMinutes": 45,
                      "enabled": false,
                      "createdAt": "2026-07-01T12:00:00Z",
                      "updatedAt": "2026-09-10T13:43:00Z"
                    }
                  ]
                }
                """;
    }

    /** Las tres colecciones presentes y vacias: una catedra recien instalada. */
    public static String snapshotVacio() {
        return """
                {
                  "snapshotVersion": 0,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "professionalCategories": [],
                  "professionals": [],
                  "weeklySchedules": []
                }
                """;
    }

    /**
     * Un error {@code application/problem+json} con la forma de §13.
     *
     * <p>El {@code detail} dice algo distinto del {@code code} a proposito:
     * las pruebas lo usan para demostrar que la traduccion decide por
     * {@code status} y {@code code} y no por el texto de {@code detail}.
     *
     * @param code el {@code code} funcional, o {@code null} para omitirlo,
     *     que es lo que hacen los rechazos de Spring Security (§13)
     */
    public static String problemJson(int status, String code, String detail) {
        String lineaDeCode = code == null ? "" : "  \"code\": \"" + code + "\",\n";
        return """
                {
                  "type": "https://www.jhipster.tech/problem/problem-with-message",
                  "title": "Error",
                  "status": %d,
                  "detail": "%s",
                  "message": "error.http.%d",
                  "path": "/api/synchronization/snapshot",
                %s  "extra": "campo que no conocemos"
                }
                """.formatted(status, detail, status, lineaDeCode);
    }
}

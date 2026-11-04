package com.um.catalogsync.sync.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Un error de sincronizacion, como lo informa el reporte de estado.
 *
 * <p><b>El {@code code} es lo que se lee y el {@code detail} lo que se mira
 * despues.</b> Es la misma regla que §13 fija para los errores HTTP: el codigo
 * es estable y se puede comparar, el texto se reescribe sin aviso. Quien
 * consuma este reporte deberia decidir por {@code code}.
 *
 * <p><b>El {@code detail} nunca lleva secretos.</b> Es el mensaje de las
 * excepciones del proyecto, que estan escritas para no incluirlos: el
 * adaptador del snapshot ni siquiera lee el {@code detail} de la respuesta de
 * error de la catedra, y las excepciones de Redis no nombran el host ni el
 * usuario. Hay una prueba que falla si el JWT tecnico o la clave de Redis
 * aparecen en alguna traza.
 *
 * @param syncRunId la corrida en la que ocurrio, para cruzarlo con
 *     {@code lastSync} o con el historial
 * @param occurredAt cuando ocurrio, en UTC. Nuestro reloj, no el de la
 *     catedra
 * @param code codigo estable del motivo
 * @param detail texto para diagnosticar. Puede venir nulo
 */
@Schema(name = "SyncError", description = "Un error registrado por una corrida de sincronizacion")
public record SyncErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "412") Long syncRunId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-03T18:30:00Z")
                Instant occurredAt,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Codigo estable: comparar por este valor, nunca por detail",
                        example = "SNAPSHOT_UNAVAILABLE")
                String code,
        @Schema(
                        description = "Texto libre para diagnosticar. Nunca incluye secretos",
                        example = "No se pudo conectar con el servicio central de la catedra")
                String detail) {}

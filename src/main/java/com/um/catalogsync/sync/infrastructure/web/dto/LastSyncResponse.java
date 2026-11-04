package com.um.catalogsync.sync.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * La ultima corrida de sincronizacion: que se intento, cuando y como salio.
 *
 * <p>Es "la ultima que arranco", no "la ultima que termino". La diferencia es
 * justo el caso que importa: con una corrida abierta, {@code status} viene en
 * {@code RUNNING} y {@code finishedAt} nulo, que es lo que dice que el
 * servicio esta reconstruyendo ahora mismo.
 *
 * <p><b>{@code toVersion} nulo en una corrida fallida no es un hueco:</b> es
 * la regla central del proyecto vista desde el historial. Si una corrida que
 * fallo tuviera version de llegada, estaria afirmando que la copia local llego
 * a una version que nunca aplico.
 *
 * @param id el id de la corrida, para cruzarlo con los errores
 * @param mode {@code FULL} si reconstruyo con snapshot, {@code INCREMENTAL} si
 *     aplico cambios version por version
 * @param trigger quien la pidio
 * @param decisionReason por que se eligio ese modo. Nulo en las corridas
 *     anteriores a la migracion V3
 * @param status como termino, o {@code RUNNING} si sigue en curso
 * @param fromVersion version local al empezar, o nulo si no habia ninguna
 * @param toVersion version a la que se llego, o nulo si no termino o termino
 *     mal
 * @param startedAt cuando arranco, en UTC
 * @param finishedAt cuando termino, o nulo si sigue corriendo
 */
@Schema(
        name = "LastSync",
        description =
                "El ultimo intento de sincronizacion que arranco. Puede estar todavia en"
                        + " curso, y en ese caso status es RUNNING y finishedAt viene nulo.")
public record LastSyncResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "412") Long id,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"FULL", "INCREMENTAL"},
                        example = "INCREMENTAL")
                String mode,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {
                            "STARTUP",
                            "EVENT",
                            "RECONCILIATION",
                            "DISCONTINUITY",
                            "MANUAL"
                        },
                        example = "EVENT")
                String trigger,
        @Schema(
                        allowableValues = {
                            "NO_LOCAL_VERSION",
                            "VERSION_OUT_OF_WINDOW",
                            "MISSING_VERSION",
                            "STATE_UNVERIFIABLE",
                            "BEHIND_CURRENT_VERSION",
                            "REQUESTED"
                        },
                        example = "BEHIND_CURRENT_VERSION")
                String decisionReason,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"RUNNING", "SUCCEEDED", "FAILED"},
                        example = "SUCCEEDED")
                String status,
        @Schema(example = "127") Long fromVersion,
        @Schema(example = "128") Long toVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-03T18:29:58Z")
                Instant startedAt,
        @Schema(example = "2026-11-03T18:30:00Z") Instant finishedAt) {}

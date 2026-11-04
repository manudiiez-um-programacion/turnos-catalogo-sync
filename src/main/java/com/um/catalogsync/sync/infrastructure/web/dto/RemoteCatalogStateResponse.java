package com.um.catalogsync.sync.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Lo que se sabe del origen —el catalogo que la catedra publica— en el momento
 * de la consulta.
 *
 * <p><b>El {@code state} explica por que los numeros pueden venir nulos.</b> Un
 * reporte con {@code currentVersion} nulo y nada mas obligaria a adivinar
 * entre dos situaciones muy distintas: que la catedra no publico nada todavia
 * y que no se la pudo alcanzar. La primera la arregla la catedra, la segunda
 * la red privada, asi que el valor viaja explicito.
 *
 * <p>Es el estado del <b>origen</b>, no de la copia local: estos numeros son
 * de la catedra y no se persisten.
 *
 * @param state {@code KNOWN} si se leyo la metadata, {@code NOT_PUBLISHED} si
 *     la catedra no publico ninguna, {@code UNREACHABLE} si no se la pudo
 *     leer
 * @param currentVersion la ultima version que la catedra publico, o nulo si
 *     {@code state} no es {@code KNOWN}
 * @param oldestAvailableVersion la version mas antigua desde la que todavia se
 *     puede avanzar con cambios incrementales. Por debajo de esta, la unica
 *     salida es el snapshot completo (§18.2)
 * @param publishedAt cuando la catedra publico esa version. Su reloj, no el
 *     nuestro: sirve para diagnosticar, no para decidir
 */
@Schema(
        name = "RemoteCatalogState",
        description =
                "Estado del catalogo publicado por la catedra. Se lee en el momento de la"
                        + " consulta; si no se pudo leer, state viene en UNREACHABLE y los"
                        + " numeros nulos.")
public record RemoteCatalogStateResponse(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"KNOWN", "NOT_PUBLISHED", "UNREACHABLE"},
                        example = "KNOWN")
                String state,
        @Schema(example = "128") Long currentVersion,
        @Schema(example = "120") Long oldestAvailableVersion,
        @Schema(example = "2026-11-03T18:29:50Z") Instant publishedAt) {}

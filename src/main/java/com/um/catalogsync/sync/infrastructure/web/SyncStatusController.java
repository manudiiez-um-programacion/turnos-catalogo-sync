package com.um.catalogsync.sync.infrastructure.web;

import com.um.catalogsync.shared.infrastructure.web.OpenApiConfig;
import com.um.catalogsync.sync.application.service.SyncStatusService;
import com.um.catalogsync.sync.infrastructure.web.dto.SyncStatusResponse;
import com.um.catalogsync.sync.infrastructure.web.mapper.SyncStatusWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El estado y los errores de sincronizacion, que es la ultima responsabilidad
 * del servicio en §4.1.
 *
 * <p><b>Bajo {@code /api/internal}, y la eleccion merece justificarse.</b> El
 * servicio acepta dos identidades (ADR 0002) y ninguna es "un operador": o es
 * el JWT del usuario final de la app KMP, o es el JWT tecnico interno del
 * proyecto. Esto va detras del segundo por dos razones. La primera es que no
 * es informacion para el usuario final: los codigos de error de integracion,
 * los motivos de decision y el historial de intentos describen como esta hecho
 * el servicio, no el catalogo que la app muestra. La segunda es que el
 * servicio de turnos <b>si</b> es un consumidor legitimo: lee la agenda
 * vigente de esta copia local (#18), asi que preguntar cuan fresca es esa
 * copia —y si esta degradada— es parte de su propio diagnostico.
 *
 * <p><b>Que queda fuera a proposito.</b> No hay un endpoint para <i>disparar</i>
 * una sincronizacion. Seria la escritura mas tentadora del servicio y rompe
 * dos cosas: la API es de solo lectura, y una sincronizacion a pedido por HTTP
 * le daria a otro servicio la decision de cuando se reconstruye la copia
 * local, que es de este. Quien necesite forzarla reinicia el servicio o espera
 * el chequeo periodico (#27).
 *
 * <p><b>Responde 200 siempre</b>, incluso sin catalogo aplicado. Es la
 * diferencia con la busqueda (#15) y la agenda (#18), que contestan 503
 * mientras no haya version local: ellas no tienen nada que devolver sin
 * catalogo y este endpoint tiene, justamente entonces, su respuesta mas util.
 * Un 503 acá dejaria al servicio sin forma de explicar por que esta caido.
 *
 * <p><b>La complementa {@code /actuator}</b> y no la reemplaza:
 * {@code /actuator/health} contesta la pregunta de un orquestador —¿esta
 * servible?— sin token, y {@code /actuator/metrics} publica los contadores
 * agregados. Esto es la foto detallada, con nombres del dominio.
 *
 * <p>El controlador depende del facade del slice y del mapper web, nunca de un
 * caso de uso ni de un puerto, como pide el ADR 0001.
 */
@RestController
@RequestMapping("/api/internal/sync")
@RequiredArgsConstructor
@Tag(
        name = "Estado de sincronizacion (interno)",
        description =
                "Estado, atraso y errores de la sincronizacion con la catedra. Requiere el"
                        + " JWT tecnico interno del proyecto con la autoridad SERVICE_TURNOS.")
public class SyncStatusController {

    private final SyncStatusService estado;
    private final SyncStatusWebMapper mapper;

    /**
     * El estado de la sincronizacion en este momento.
     *
     * <p>Lee el estado del origen en vivo —una sola lectura de
     * {@code catedra:sync:metadata}— porque sin eso no habria forma de
     * distinguir una copia local atrasada, que se recupera sola, de una
     * integracion caida, que no. Si esa lectura no se puede hacer, la
     * respuesta lo dice en {@code remote.state} en lugar de fallar.
     */
    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Estado y errores de sincronizacion",
            description =
                    "Version local aplicada, estado del catalogo publicado por la catedra,"
                            + " atraso en versiones, ultimo intento de sincronizacion con su"
                            + " modo, motivo, inicio, fin y resultado, y los ultimos errores"
                            + " con su codigo estable.\n\n"
                            + "El campo health distingue cuatro situaciones que piden"
                            + " reacciones distintas: UP_TO_DATE, BEHIND (se recupera solo),"
                            + " REBUILDING (hay una sincronizacion en curso) y DEGRADED (no se"
                            + " pudo verificar el origen o el ultimo intento fallo).\n\n"
                            + "Responde 200 siempre, incluso sin catalogo aplicado o con la"
                            + " catedra inalcanzable. Consulta el estado del origen en vivo,"
                            + " nunca el catalogo: las busquedas se siguen resolviendo solo"
                            + " con datos locales (§6.2).",
            security = @SecurityRequirement(name = OpenApiConfig.SEGURIDAD_INTERNA))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "El estado de la sincronizacion"),
        @ApiResponse(
                responseCode = "401",
                description =
                        "Sin token (AUTH_TOKEN_MISSING) o token invalido (AUTH_TOKEN_INVALID)",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
                responseCode = "403",
                description = "Token valido sin la autoridad SERVICE_TURNOS (AUTH_FORBIDDEN)",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    })
    public SyncStatusResponse estadoDeSincronizacion() {
        return mapper.toResponse(estado.getStatus());
    }
}

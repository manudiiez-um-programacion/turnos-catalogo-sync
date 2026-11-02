package com.um.catalogsync.schedule.infrastructure.web;

import com.um.catalogsync.schedule.application.service.WeeklyScheduleService;
import com.um.catalogsync.schedule.infrastructure.web.dto.CurrentAgendaResponse;
import com.um.catalogsync.schedule.infrastructure.web.mapper.CurrentAgendaWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El contrato de agenda vigente con el servicio de turnos (§3, §4.1).
 *
 * <p><b>Es la unica via.</b> §3 prohibe que el servicio de turnos acceda a las
 * tablas, repositorios o estructuras internas de este servicio, use una base
 * compartida como integracion o duplique una replica del catalogo para
 * busquedas o reservas nuevas. Todo lo que turnos necesita saber del catalogo
 * vigente entra por aca.
 *
 * <p><b>Solo lectura.</b> No hay ni va a haber un endpoint de escritura en esta
 * API: la copia local se escribe desde la sincronizacion con la catedra, que es
 * la fuente autoritativa, y nunca desde otro servicio del proyecto. Lo fija una
 * prueba que falla si aparece cualquier {@code @PostMapping},
 * {@code @PutMapping}, {@code @PatchMapping} o {@code @DeleteMapping} en el
 * codigo.
 *
 * <p><b>Bajo {@code /api/internal}</b>, que es el prefijo que la cadena de
 * seguridad protege con el JWT tecnico interno y la autoridad
 * {@code SERVICE_TURNOS} (ADR 0002). Un token de usuario final no sirve: no
 * esta firmado con ese secreto. El prefijo no es decorativo, es lo que separa
 * las dos identidades.
 *
 * <p>El controlador depende del facade del slice y del mapper web, nunca de un
 * caso de uso ni de un puerto, como pide el ADR 0001.
 */
@RestController
@RequestMapping("/api/internal/professionals")
@RequiredArgsConstructor
@Tag(
        name = "Agenda vigente (interno)",
        description =
                "Contrato de solo lectura con el servicio de turnos. Requiere el JWT tecnico"
                        + " interno del proyecto con la autoridad SERVICE_TURNOS.")
public class CurrentAgendaController {

    private final WeeklyScheduleService horarios;
    private final CurrentAgendaWebMapper mapper;

    /**
     * La agenda vigente de un profesional.
     *
     * <p>Devuelve sus datos vigentes, sus horarios semanales <b>habilitados</b>
     * y la version local de catalogo con la que se resolvio la consulta.
     */
    @GetMapping(path = "/{professionalId}/agenda", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Agenda vigente de un profesional",
            description =
                    "Reglas de agenda sobre la copia local, nunca consultando a la catedra."
                            + " No es disponibilidad: no incluye ocupaciones, bloqueos ni"
                            + " reservas, que son del servicio de turnos (§4.2). La respuesta"
                            + " trae la version local aplicada para que el cliente sepa sobre"
                            + " que copia esta trabajando.",
            security = @SecurityRequirement(name = "jwt-interno"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "La agenda vigente"),
        @ApiResponse(
                responseCode = "401",
                description = "Sin token (AUTH_TOKEN_MISSING) o token invalido (AUTH_TOKEN_INVALID)",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
                responseCode = "403",
                description = "Token valido sin la autoridad SERVICE_TURNOS (AUTH_FORBIDDEN)",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
                responseCode = "404",
                description = "El profesional no esta en la copia local (PROFESSIONAL_NOT_FOUND)",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
                responseCode = "409",
                description = "El profesional existe pero esta deshabilitado (PROFESSIONAL_DISABLED)",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
                responseCode = "503",
                description =
                        "Todavia no hay una version de catalogo aplicada"
                                + " (CATALOG_NOT_SYNCHRONIZED): reintentar",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    })
    public CurrentAgendaResponse agendaVigente(
            @Parameter(
                            description = "Id del profesional en el catalogo de la catedra",
                            required = true,
                            schema = @Schema(type = "integer", format = "int64", example = "42"))
                    @PathVariable
                    Long professionalId) {

        return mapper.toResponse(horarios.getCurrentAgenda(professionalId));
    }
}

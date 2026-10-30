package com.um.catalogsync.professional.infrastructure.web;

import com.um.catalogsync.professional.application.service.ProfessionalService;
import com.um.catalogsync.professional.infrastructure.web.dto.ProfessionalSearchRequest;
import com.um.catalogsync.professional.infrastructure.web.dto.ProfessionalSearchResponse;
import com.um.catalogsync.professional.infrastructure.web.mapper.ProfessionalSearchWebMapper;
import com.um.catalogsync.shared.infrastructure.web.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La busqueda de profesionales que consume la app KMP (§4.1).
 *
 * <p><b>Nunca consulta a la catedra.</b> §6.2 lo prohibe explicitamente y es
 * la razon por la que este servicio mantiene una copia local: una busqueda que
 * saliera a la red por cada tecleo del usuario convertiria al servicio central
 * en el cuello de botella de la app. Todo se resuelve en PostgreSQL.
 *
 * <p><b>Fuera de {@code /api/internal}</b>, asi que cae en la cadena de
 * seguridad por defecto y exige el JWT del <i>usuario final</i> que emite el
 * servicio de turnos (D5 y ADR 0002). El JWT tecnico interno no sirve aca, ni
 * el de usuario sirve en la API interna: estan firmados con secretos distintos
 * y cada cadena conoce solo el suyo.
 *
 * <p><b>Solo lectura</b>, como toda la API de este servicio: la copia local se
 * escribe desde la sincronizacion con la catedra y nunca por HTTP. Lo fija una
 * prueba que falla si aparece cualquier anotacion de metodo que escriba.
 *
 * <p>El controlador depende del facade del slice y del mapper web, nunca de un
 * caso de uso ni de un puerto, como pide el ADR 0001 y el criterio de #15.
 */
@RestController
@RequestMapping("/api/professionals")
@RequiredArgsConstructor
@Tag(
        name = "Profesionales",
        description =
                "Busqueda y filtros sobre la copia local del catalogo. Requiere el JWT del"
                        + " usuario final.")
public class ProfessionalSearchController {

    private final ProfessionalService profesionales;
    private final ProfessionalSearchWebMapper mapper;

    /**
     * Busca profesionales por categoria, nombre y estado, paginado.
     *
     * <p>Los tres filtros son opcionales y combinables: sin ninguno devuelve
     * el catalogo completo paginado.
     *
     * <p><b>Orden fijo: apellido, nombre, id, ascendente.</b> No se recibe del
     * cliente, y no es una limitacion por ahorrar trabajo. Un {@code ?sort=}
     * convertiria el nombre de una columna en entrada del usuario, y el orden
     * es algo que la app KMP puede dar por sentado justamente porque no se
     * negocia en cada llamada. El id desempata a los homonimos, que es lo que
     * evita que un profesional en el borde de una pagina se repita o se pierda
     * al pedir la siguiente.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Buscar profesionales en la copia local",
            description =
                    "Filtros opcionales y combinables: categoria exacta, coincidencia parcial"
                            + " de nombre insensible a mayusculas y acentos sobre nombre y"
                            + " apellido, y estado habilitado. Se resuelve solo con datos"
                            + " locales, nunca consultando a la catedra (§6.2). El orden es"
                            + " fijo —apellido, nombre, id ascendente— para que el paginado sea"
                            + " estable. Una pagina vacia es una respuesta valida. La respuesta"
                            + " trae la version local de catalogo con la que se resolvio.",
            security = @SecurityRequirement(name = OpenApiConfig.SEGURIDAD_USUARIO))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "La pagina de resultados, posiblemente vacia"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Parametros invalidos (VALIDATION_ERROR): pagina negativa, tamaño"
                                + " fuera de rango o un tipo que no corresponde",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
                responseCode = "401",
                description =
                        "Sin token (AUTH_TOKEN_MISSING) o token invalido (AUTH_TOKEN_INVALID)",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
                responseCode = "503",
                description =
                        "Todavia no hay una version de catalogo aplicada"
                                + " (CATALOG_NOT_SYNCHRONIZED): reintentar",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    })
    public ProfessionalSearchResponse buscar(
            @ParameterObject @Valid @ModelAttribute ProfessionalSearchRequest filtros) {

        return mapper.toResponse(profesionales.search(filtros.toCriteria()));
    }
}

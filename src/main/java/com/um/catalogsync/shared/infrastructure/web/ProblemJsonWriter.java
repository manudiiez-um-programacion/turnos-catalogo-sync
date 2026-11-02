package com.um.catalogsync.shared.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Escribe un error en {@code application/problem+json} con la forma de §13.
 *
 * <p><b>Por que hace falta escribirlo a mano.</b> Para los errores que lanza
 * un controlador alcanza un {@code @RestControllerAdvice}, porque pasan por el
 * pipeline de Spring MVC. Los rechazos de seguridad no: ocurren en un filtro,
 * antes de que haya un handler, asi que ni el advice ni los convertidores de
 * mensaje intervienen. Sin esto, Spring Security responde un 401 con cuerpo
 * vacio y el cliente no tiene con que distinguir "falta el token" de "el token
 * no sirve".
 *
 * <p>El {@code ObjectMapper} es el de Jackson 3 ({@code tools.jackson}), que
 * es el que administra Spring Boot 4: {@code com.fasterxml.jackson.databind}
 * sigue en el classpath por dependencias transitivas, pero no hay un bean de
 * esa clase y pedirlo deja el contexto sin arrancar.
 *
 * <p>Los nombres de campo son los de §13 —{@code type}, {@code title},
 * {@code status}, {@code detail}, {@code message}, {@code path},
 * {@code code}— y en ese orden, para que la respuesta sea indistinguible de la
 * del servicio central y la app KMP pueda usar un solo parseador.
 */
@Component
public class ProblemJsonWriter {

    /**
     * El {@code type} de §13.
     *
     * <p>Se reusa la URI de JHipster en lugar de inventar un dominio propio:
     * el enunciado pide un contrato compatible con el usuario de JHipster
     * (§3.2) y el ejemplo de §13 usa esta misma URI, asi que un cliente que ya
     * sabe leer los errores de la catedra lee estos sin un caso especial.
     * Ademas el contrato dice que se decide por {@code status} y {@code code},
     * no por {@code type}: apuntar a un dominio que no controlamos y que
     * podria no resolver seria peor.
     */
    private static final URI TIPO = URI.create("https://www.jhipster.tech/problem/problem-with-message");

    /**
     * El cuerpo de §13, en el orden de §13.
     *
     * <p>Es publico y estatico porque lo comparten los dos caminos por los que
     * este servicio produce errores: los rechazos de seguridad, que se escriben
     * a mano desde un filtro, y las excepciones de aplicacion, que salen por el
     * {@code @RestControllerAdvice}. Si cada uno armara su propio mapa, los dos
     * formatos divergirian en la primera modificacion y el cliente tendria dos
     * parseadores.
     */
    public static Map<String, Object> cuerpo(
            HttpStatus status, ApiErrorCode code, String detail, String path) {

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", TIPO.toString());
        cuerpo.put("title", status.getReasonPhrase());
        cuerpo.put("status", status.value());
        cuerpo.put("detail", detail);
        cuerpo.put("message", "error.http." + status.value());
        cuerpo.put("path", path);
        cuerpo.put("code", code.name());
        return cuerpo;
    }

    private final ObjectMapper jackson;

    public ProblemJsonWriter(ObjectMapper jackson) {
        this.jackson = jackson;
    }

    /**
     * Responde el problema, salvo que la respuesta ya se haya empezado.
     *
     * @param request de donde sale el {@code path}
     * @param response respuesta a completar
     * @param status codigo HTTP
     * @param code el {@code code} estable del contrato
     * @param detail texto para humanos; nunca parte del contrato
     */
    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode code,
            String detail)
            throws IOException {

        if (response.isCommitted()) {
            return;
        }

        Map<String, Object> cuerpo = cuerpo(status, code, detail, request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jackson.writeValue(response.getOutputStream(), cuerpo);
    }
}

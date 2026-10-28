package com.um.catalogsync.shared.infrastructure.web;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.schedule.application.exception.CatalogNotSynchronizedException;
import com.um.catalogsync.schedule.application.exception.ProfessionalDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de aplicacion a {@code problem+json} con la forma de
 * §13 y un {@code code} estable.
 *
 * <p>Vive en {@code shared} porque el formato de error es transversal: §13 lo
 * define una vez para toda la API y un advice por slice serian varias
 * traducciones del mismo contrato que pueden divergir. Reusa el armado del
 * cuerpo de {@link ProblemJsonWriter} para que una respuesta de error sea
 * indistinguible de un rechazo de seguridad.
 *
 * <p><b>Que no hace.</b> No tiene un {@code @ExceptionHandler(Exception.class)}
 * que convierta cualquier fallo en un 500 con cuerpo propio. Un catch-all asi
 * tapa errores de programacion detras de una respuesta prolija: lo que
 * corresponde es que un fallo no previsto quede como 500 y con su stack trace
 * en el log, y que cada error que <b>si</b> es parte del contrato tenga su
 * entrada explicita aca. §13.1 admite expresamente que los 500 no garanticen
 * {@code code}.
 *
 * <p>El mapa de cada excepcion a su codigo y su HTTP es el contrato con el
 * servicio de turnos y se documenta en #20.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 404: no esta en la copia local. */
    @ExceptionHandler(ProfessionalNotFoundException.class)
    ResponseEntity<Map<String, Object>> profesionalInexistente(
            ProfessionalNotFoundException error, HttpServletRequest request) {

        return problema(HttpStatus.NOT_FOUND, ApiErrorCode.PROFESSIONAL_NOT_FOUND, error, request);
    }

    /** 409: existe pero no tiene agenda vigente. */
    @ExceptionHandler(ProfessionalDisabledException.class)
    ResponseEntity<Map<String, Object>> profesionalDeshabilitado(
            ProfessionalDisabledException error, HttpServletRequest request) {

        return problema(HttpStatus.CONFLICT, ApiErrorCode.PROFESSIONAL_DISABLED, error, request);
    }

    /** 503: la copia local todavia no es una copia de nada. */
    @ExceptionHandler(CatalogNotSynchronizedException.class)
    ResponseEntity<Map<String, Object>> sinVersionAplicada(
            CatalogNotSynchronizedException error, HttpServletRequest request) {

        return problema(
                HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CATALOG_NOT_SYNCHRONIZED, error, request);
    }

    /**
     * <p>El {@code detail} sale del mensaje de la excepcion. Se puede hacer
     * porque estos mensajes estan escritos para ser leidos por quien integra y
     * no llevan datos internos: ids del catalogo de la catedra, que el cliente
     * ya conoce porque los mando. Ninguna de estas excepciones toca secretos ni
     * estructura de la base.
     */
    private ResponseEntity<Map<String, Object>> problema(
            HttpStatus status, ApiErrorCode code, RuntimeException error, HttpServletRequest request) {

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemJsonWriter.cuerpo(status, code, error.getMessage(), request.getRequestURI()));
    }
}

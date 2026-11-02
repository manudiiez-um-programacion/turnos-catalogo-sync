package com.um.catalogsync.shared.infrastructure.web;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.schedule.domain.model.InvalidAvailabilityFilterException;
import com.um.catalogsync.sync.application.exception.CatalogNotSynchronizedException;
import com.um.catalogsync.schedule.application.exception.ProfessionalDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
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

    /**
     * 400: los parametros de la busqueda no son aceptables (#15).
     *
     * <p>Atrapa {@link BindException} y no cada anotacion por separado porque
     * es el tipo del que Spring hace derivar los dos fallos de binding de una
     * query string: la violacion de una restriccion de validacion y el tipo
     * que no convierte —{@code ?page=abc}—. Los dos significan lo mismo para
     * el cliente y se arreglan igual, asi que comparten {@code code}.
     *
     * <p><b>El detalle nombra los campos, no los valores.</b> Decir "size: el
     * tamaño de pagina maximo es 100" le dice al que integra exactamente que
     * corregir; devolverle el valor rechazado seria reflejar entrada del
     * usuario en la respuesta sin ninguna necesidad.
     */
    @ExceptionHandler(BindException.class)
    ResponseEntity<Map<String, Object>> parametrosInvalidos(
            BindException error, HttpServletRequest request) {

        String detalle =
                error.getFieldErrors().stream()
                        .map(campo -> campo.getField() + ": " + mensajeDe(campo))
                        .sorted()
                        .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(
                        ProblemJsonWriter.cuerpo(
                                HttpStatus.BAD_REQUEST,
                                ApiErrorCode.VALIDATION_ERROR,
                                detalle.isEmpty() ? "parametros invalidos" : detalle,
                                request.getRequestURI()));
    }

    /**
     * El mensaje de la restriccion, o uno propio si no hay.
     *
     * <p>Un fallo de conversion de tipo no trae mensaje de validacion: el que
     * Spring arma menciona la clase Java esperada y el valor recibido, que es
     * detalle interno. Se reemplaza por algo que el cliente pueda accionar.
     */
    private static String mensajeDe(FieldError campo) {
        return campo.isBindingFailure() || campo.getDefaultMessage() == null
                ? "el valor no tiene el tipo esperado"
                : campo.getDefaultMessage();
    }

    /**
     * 400: el filtro de disponibilidad no se entiende (#16).
     *
     * <p>Separado del {@code VALIDATION_ERROR} de arriba porque llega por otro
     * camino: no es un fallo de binding sino una invariante de dominio, y el
     * cliente tiene que poder distinguir los dos casos. El {@code detail} sale
     * del mensaje de la excepcion, que esta escrito para quien integra y dice
     * cual de los cuatro problemas posibles fue.
     */
    @ExceptionHandler(InvalidAvailabilityFilterException.class)
    ResponseEntity<Map<String, Object>> disponibilidadInvalida(
            InvalidAvailabilityFilterException error, HttpServletRequest request) {

        return problema(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_AVAILABILITY_FILTER,
                error,
                request);
    }

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

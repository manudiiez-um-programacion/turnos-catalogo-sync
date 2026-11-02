package com.um.catalogsync.shared.infrastructure.security;

import com.um.catalogsync.shared.infrastructure.web.ApiErrorCode;
import com.um.catalogsync.shared.infrastructure.web.ProblemJsonWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Responde los 401 en {@code application/problem+json}.
 *
 * <p>Distingue los dos casos que a quien integra le importan y que Spring
 * Security, por defecto, devuelve iguales y sin cuerpo: <b>no mandaste
 * token</b> y <b>mandaste uno que no sirve</b>. El primero suele ser un
 * cliente al que se le olvido la cabecera; el segundo, un secreto que no
 * coincide entre los dos servicios o un token vencido. Mandar el mismo 401
 * vacio para los dos es lo que convierte un problema de configuracion de
 * media hora en una tarde.
 *
 * <p>El {@code detail} nombra la cabecera y el esquema esperados a proposito.
 * No filtra nada: el que pregunta ya sabe que hay una API protegida, y la
 * forma de autenticarse va a estar publicada en el contrato (#20) y en
 * OpenAPI. Lo que nunca aparece es por que un token concreto fue rechazado
 * —vencido, firma distinta, otro emisor—: eso queda en el log del servidor,
 * porque sirve para diagnosticar y tambien para tantear credenciales.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemJsonWriter problemas;

    public ProblemAuthenticationEntryPoint(ProblemJsonWriter problemas) {
        this.problemas = problemas;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException excepcion)
            throws IOException {

        boolean sinCredencial = !tieneBearer(request);

        ApiErrorCode code = sinCredencial ? ApiErrorCode.AUTH_TOKEN_MISSING : ApiErrorCode.AUTH_TOKEN_INVALID;
        String detail =
                sinCredencial
                        ? "Falta la cabecera Authorization con un token Bearer"
                        : "El token recibido no es valido para este servicio";

        // Lo pide el RFC 6750 para un 401 de Bearer, y es lo que hace que un
        // cliente HTTP generico sepa que esperaba un token y no una cookie.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        problemas.write(request, response, HttpStatus.UNAUTHORIZED, code, detail);
    }

    private boolean tieneBearer(HttpServletRequest request) {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        return cabecera != null && cabecera.strip().toLowerCase(java.util.Locale.ROOT).startsWith("bearer ");
    }
}

package com.um.catalogsync.shared.infrastructure.security;

import com.um.catalogsync.shared.infrastructure.web.ApiErrorCode;
import com.um.catalogsync.shared.infrastructure.web.ProblemJsonWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Responde los 403 en {@code application/problem+json}.
 *
 * <p>Un 403 aca significa: el token se verifico bien, pero no habilita esta
 * ruta. Es la diferencia que §9 pide sostener entre las dos identidades, y es
 * informacion que quien integra necesita, porque la accion a tomar es
 * distinta: con 401 hay que revisar el secreto o la vigencia; con 403, que se
 * esta usando el token de la identidad equivocada.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemJsonWriter problemas;

    public ProblemAccessDeniedHandler(ProblemJsonWriter problemas) {
        this.problemas = problemas;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException excepcion)
            throws IOException {

        problemas.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ApiErrorCode.AUTH_FORBIDDEN,
                "El token es valido pero no habilita esta operacion");
    }
}

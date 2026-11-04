package com.um.catalogsync.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.um.catalogsync.professional.application.service.ProfessionalService;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.shared.infrastructure.security.TokensDePrueba;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * El criterio de §9 "los mensajes de error no filtran detalles internos ni
 * stack traces" (issue #32), para la unica rama que {@code ApiExceptionHandler}
 * deja deliberadamente sin traducir.
 *
 * <p><b>Por que un fallo inyectado y no uno real.</b> No hay ninguna
 * excepcion no prevista que se pueda provocar desde la frontera HTTP sin
 * tocar el codigo de produccion: todo lo previsible ya tiene su
 * {@code @ExceptionHandler}. Asi que se reemplaza el facade del slice por un
 * doble que lanza una {@link RuntimeException} con un mensaje que, si
 * llegara a filtrarse, seria inconfundible —y que a proposito menciona una
 * URL interna, para parecerse al peor caso real—.
 *
 * <p>Confirma lo que ya es cierto por configuracion (Spring Boot no incluye
 * {@code message} ni {@code stacktrace} en el 500 por defecto, y
 * {@code ApiExceptionHandler} no tiene un catch-all que pudiera cambiar eso),
 * pero que antes de este issue no tenia ninguna prueba propia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnmappedExceptionResponseTest extends AbstractPostgresIntegrationTest {

    private static final String RUTA = "/api/professionals";

    /** Si esto apareciera en una respuesta, seria la prueba de la fuga. */
    private static final String MENSAJE_SENSIBLE =
            "fallo inesperado contra jdbc:postgresql://10.0.0.5:5432/catalogo con la tabla"
                    + " professional_category";

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    @MockitoBean
    private ProfessionalService profesionales;

    @Test
    @DisplayName("una excepcion no prevista da 500 sin el mensaje interno ni el stack trace")
    void excepcionNoPrevistaNoFiltraNada() throws Exception {
        when(profesionales.search(any())).thenThrow(new RuntimeException(MENSAJE_SENSIBLE));

        HttpRequest pedido =
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA))
                        .header("Authorization", "Bearer " + TokensDePrueba.deUsuario())
                        .GET()
                        .build();
        HttpResponse<String> respuesta =
                cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(500);
        assertThat(respuesta.body())
                .doesNotContain(MENSAJE_SENSIBLE)
                .doesNotContain("RuntimeException")
                .doesNotContain("com.um.catalogsync")
                .doesNotContain("\tat ");
    }
}

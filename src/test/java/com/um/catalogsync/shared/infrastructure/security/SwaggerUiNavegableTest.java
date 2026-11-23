package com.um.catalogsync.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.shared.infrastructure.DocumentacionEncendida;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Swagger UI tiene que poder <b>verse</b>, no solo responder 200.
 *
 * <p><b>El defecto que esta prueba habria encontrado (#106).</b> Con
 * {@code Content-Security-Policy: default-src 'none'} en todas las respuestas,
 * {@code /swagger-ui.html} devolvia 200 y un HTML correcto, y la pagina se veia
 * en blanco: el navegador bloqueaba el CSS y los tres scripts que ese HTML
 * carga. Ninguna prueba lo noto porque todas miraban el codigo de estado, que
 * estaba bien, y el cuerpo, que tambien. Lo que estaba mal era una cabecera.
 *
 * <p>De ahi la forma de las pruebas de abajo: no preguntan "responde", preguntan
 * "la politica que acompaña a esta respuesta permite cargar lo que la respuesta
 * referencia". Es la diferencia entre servir la documentacion y publicarla.
 *
 * <p>Enciende las <b>dos</b> propiedades de springdoc porque el perfil
 * {@code test} las deja apagadas, y hacen falta las dos: con
 * {@code swagger-ui.enabled=false} los recursos de la pagina no se sirven, el
 * 404 se reenvia a {@code /error} —que si exige credencial— y la prueba ve un
 * 401 que no tiene nada que ver con lo que quiere medir. En el perfil
 * {@code local} las dos salen de {@code SPRINGDOC_ENABLED} y se mueven juntas.
 *
 * <p>Que con springdoc apagado estas rutas respondan 401 lo cubre
 * {@link ApiSecurityIntegrationTest}, y las dos cosas juntas fijan el
 * comportamiento de la cadena condicional por los dos lados.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {DocumentacionEncendida.API_DOCS, DocumentacionEncendida.SWAGGER_UI})
class SwaggerUiNavegableTest extends AbstractPostgresIntegrationTest {

    /** Una ruta de datos cualquiera: alcanza con que la cadena de la API la atienda. */
    private static final String RUTA_DE_DATOS = "/api/professionals";

    private static final String CSP = "content-security-policy";

    private final HttpClient cliente =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    @LocalServerPort
    private int puerto;

    @Nested
    @DisplayName("La pagina puede cargar lo que necesita")
    class LaPaginaCarga {

        @Test
        @DisplayName("el HTML llega con una politica que permite sus scripts y estilos propios")
        void elHtmlTraeLaPoliticaQueLoDejaCargar() throws Exception {
            HttpResponse<String> respuesta = pedir("/swagger-ui/index.html");

            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(respuesta.body()).contains("swagger-ui-bundle.js", "swagger-ui.css");

            String politica = politicaDe(respuesta);
            assertThat(politica)
                    .as("sin esto el navegador bloquea el bundle y la pagina queda en blanco")
                    .contains("script-src 'self'")
                    .contains("style-src 'self' 'unsafe-inline'")
                    .doesNotContain("default-src 'none'");
        }

        @Test
        @DisplayName("/swagger-ui.html redirige a la pagina real y termina en 200")
        void laRutaCortaLlegaAlaPagina() throws Exception {
            HttpResponse<String> respuesta = pedir("/swagger-ui.html");

            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(respuesta.uri().getPath()).isEqualTo("/swagger-ui/index.html");
        }

        @Test
        @DisplayName("los recursos que el HTML referencia existen y se sirven del mismo origen")
        void losRecursosSeSirven() throws Exception {
            // La politica concede 'self' y nada mas: si alguno de estos no lo
            // sirviera esta aplicacion, la politica correcta lo bloquearia.
            for (String recurso :
                    new String[] {
                        "/swagger-ui/swagger-ui.css",
                        "/swagger-ui/swagger-ui-bundle.js",
                        "/swagger-ui/swagger-ui-standalone-preset.js",
                        "/swagger-ui/swagger-initializer.js"
                    }) {
                assertThat(pedir(recurso).statusCode()).as("%s", recurso).isEqualTo(200);
            }
        }

        @Test
        @DisplayName("\"Try it out\" puede leer el documento: se sirve sin token y con connect-src")
        void elDocumentoSeLeeSinToken() throws Exception {
            HttpResponse<String> respuesta = pedir("/v3/api-docs");

            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(politicaDe(respuesta))
                    .as("sin connect-src la UI no puede pedir el documento ni ejecutar nada")
                    .contains("connect-src 'self'");
        }
    }

    @Nested
    @DisplayName("La politica relajada no se filtra a la API de datos")
    class LaApiSigueEstricta {

        @Test
        @DisplayName("una ruta de datos conserva default-src 'none' aunque la documentacion este encendida")
        void laApiConservaLaPoliticaEstricta() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_DE_DATOS);

            // Sin token: 401. Lo que importa es la cabecera, y las cabeceras no
            // dependen de que la credencial sea valida.
            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(politicaDe(respuesta))
                    .as("encender la documentacion no puede aflojar la politica de la API")
                    .isEqualTo("default-src 'none'");
        }

        @Test
        @DisplayName("la API nunca recibe permiso de ejecutar scripts ni estilos en linea")
        void laApiNoRecibePermisosDeScript() throws Exception {
            assertThat(politicaDe(pedir(RUTA_DE_DATOS)))
                    .doesNotContain("'unsafe-inline'")
                    .doesNotContain("'unsafe-eval'")
                    .doesNotContain("script-src");
        }
    }

    @Nested
    @DisplayName("Lo que la politica de la documentacion no concede")
    class LimitesDeLaPolitica {

        /**
         * Las dos concesiones que una politica de Swagger UI puede terminar
         * acumulando sin que nadie lo note, y que no hacen falta: ejecutar
         * scripts en linea y {@code eval}. La prueba las fija para que aflojarlas
         * tenga que ser una decision explicita y visible en el diff.
         */
        @Test
        @DisplayName("ni scripts en linea ni eval: 'unsafe-inline' se concede solo en estilos")
        void soloLosEstilosObtienenUnsafeInline() throws Exception {
            String politica = politicaDe(pedir("/swagger-ui/index.html"));

            assertThat(politica).doesNotContain("'unsafe-eval'");
            assertThat(politica).contains("script-src 'self';");
            assertThat(politica.indexOf("'unsafe-inline'"))
                    .as("el unico 'unsafe-inline' tiene que ser el de style-src")
                    .isEqualTo(politica.lastIndexOf("'unsafe-inline'"));
        }

        @Test
        @DisplayName("ningun origen de terceros: la politica no nombra ningun host")
        void nadaDeTerceros() throws Exception {
            assertThat(politicaDe(pedir("/swagger-ui/index.html")))
                    .doesNotContain("http://")
                    .doesNotContain("https://")
                    .doesNotContain("*");
        }
    }

    private HttpResponse<String> pedir(String ruta) throws Exception {
        return cliente.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String politicaDe(HttpResponse<String> respuesta) {
        return respuesta
                .headers()
                .firstValue(CSP)
                .orElseThrow(() -> new AssertionError("la respuesta no trae " + CSP));
    }
}

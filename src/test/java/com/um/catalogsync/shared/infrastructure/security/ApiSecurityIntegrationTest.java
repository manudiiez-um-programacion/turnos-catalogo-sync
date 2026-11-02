package com.um.catalogsync.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import io.swagger.v3.oas.annotations.Hidden;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * La seguridad de la API propia, contra un servidor HTTP real.
 *
 * <p><b>Por que un servidor de verdad y no MockMvc.</b> Lo que hay que probar
 * aca vive en filtros: el que extrae el Bearer, el que decodifica, el de CORS y
 * el que traduce el rechazo a {@code problem+json}. Una prueba que arme el
 * contexto a mano corre el riesgo de no instalar la cadena de filtros y pasar
 * en verde mientras la API queda abierta —el peor resultado posible para una
 * prueba de seguridad—. Con {@code RANDOM_PORT} y un cliente HTTP del JDK, lo
 * que se mide es exactamente lo que va a recibir el servicio de turnos.
 *
 * <p>Las dos rutas de prueba existen solo acá, y eso prueba algo por si mismo:
 * {@code /api/test/busqueda} no esta nombrada en ninguna regla de
 * {@link SecurityConfig}, asi que si quedara accesible sin token estaria
 * fallando la regla de denegado por defecto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String RUTA_DE_USUARIO = "/api/test/busqueda";
    private static final String RUTA_INTERNA = "/api/internal/test/agenda";

    private static final ObjectMapper JACKSON = new ObjectMapper();

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    /**
     * Dos endpoints que no existen en produccion, uno por cadena.
     *
     * <p>Hacen falta porque todavia no hay controladores: la busqueda es #15 y
     * la agenda es #18. Probar la seguridad contra endpoints reales ataria esta
     * prueba a esos issues y dejaria #17 sin verificar hasta que esten.
     *
     * <p><b>Los registra el component scan, no un {@code @TestConfiguration}.</b>
     * El scan de la aplicacion barre {@code com.um.catalogsync} y en la JVM de
     * pruebas eso incluye las clases de prueba, asi que declararlos tambien como
     * {@code @Bean} los registraba dos veces y el contexto no levantaba con
     * "Ambiguous mapping". Tampoco sirve {@code @RequestMapping} a nivel de
     * clase para esquivar el estereotipo: Spring Framework 7 ya no considera
     * handler a una clase sin {@code @Controller}, y las rutas responden 404.
     *
     * <p><b>{@code @Hidden} porque el component scan tambien alcanza al
     * documento OpenAPI.</b> Sin eso, estas dos rutas inventadas aparecerian en
     * el contrato que publica el servicio y en el que exporta #20, donde del
     * otro lado hay alguien implementando un cliente.
     */
    @Hidden
    @RestController
    static class BusquedaDePrueba {
        @GetMapping(RUTA_DE_USUARIO)
        public String responder() {
            return "busqueda";
        }
    }

    @Hidden
    @RestController
    static class AgendaDePrueba {
        @GetMapping(RUTA_INTERNA)
        public String responder() {
            return "agenda";
        }
    }

    @Nested
    @DisplayName("Lo que no exige token")
    class SinToken {

        @Test
        @DisplayName("health responde sin credencial, porque lo consulta el healthcheck")
        void healthEsPublico() throws Exception {
            HttpResponse<String> respuesta = pedir("/actuator/health", null);

            assertThat(respuesta.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("la documentacion OpenAPI exige token cuando springdoc esta apagado")
        void openapiNoEsPublicoSiEstaApagado() throws Exception {
            // En el perfil test springdoc.api-docs.enabled es false, asi que la
            // regla que abre esas rutas no se aplica. Fija el limite: lo que se
            // abre es la documentacion publicada, no el patron de URL.
            assertThat(pedir("/v3/api-docs", null).statusCode()).isEqualTo(401);
            assertThat(pedir("/v3/api-docs.yaml", null).statusCode()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("La API del usuario final")
    class ApiDeUsuario {

        @Test
        @DisplayName("sin token: 401 con code AUTH_TOKEN_MISSING y problem+json")
        void sinToken() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_DE_USUARIO, null);

            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(respuesta.headers().firstValue("content-type"))
                    .hasValueSatisfying(tipo -> assertThat(tipo).startsWith("application/problem+json"));
            assertThat(respuesta.headers().firstValue("www-authenticate")).hasValue("Bearer");

            JsonNode problema = leer(respuesta);
            assertThat(problema.path("code").asString()).isEqualTo("AUTH_TOKEN_MISSING");
            assertThat(problema.path("status").asInt()).isEqualTo(401);
            assertThat(problema.path("path").asString()).isEqualTo(RUTA_DE_USUARIO);
            assertThat(problema.path("message").asString()).isEqualTo("error.http.401");
        }

        @Test
        @DisplayName("con el token que emite turnos: pasa")
        void conTokenDeUsuario() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_DE_USUARIO, TokensDePrueba.deUsuario());

            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(respuesta.body()).isEqualTo("busqueda");
        }

        @Test
        @DisplayName("token vencido: 401 AUTH_TOKEN_INVALID")
        void tokenVencido() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_DE_USUARIO, TokensDePrueba.deUsuarioVencido());

            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(leer(respuesta).path("code").asString()).isEqualTo("AUTH_TOKEN_INVALID");
        }

        @Test
        @DisplayName("firmado con otra clave: 401 AUTH_TOKEN_INVALID")
        void firmaDeOtraClave() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_DE_USUARIO, TokensDePrueba.deUsuarioConOtraFirma());

            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(leer(respuesta).path("code").asString()).isEqualTo("AUTH_TOKEN_INVALID");
        }

        @Test
        @DisplayName("emitido por otro: 401, aunque la firma sea la correcta")
        void otroEmisor() throws Exception {
            // Sin JwtIssuerValidator este caso pasaria: la firma es valida y el
            // token no esta vencido. Lo que lo rechaza es exigir el iss.
            HttpResponse<String> respuesta = pedir(RUTA_DE_USUARIO, TokensDePrueba.deUsuarioConOtroEmisor());

            assertThat(respuesta.statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("un valor que no es un JWT: 401, no 500")
        void credencialQueNoEsUnJwt() throws Exception {
            // Es la forma del JWT tecnico de catedra visto desde aca: una
            // credencial ajena que este servicio no tiene con que verificar.
            HttpResponse<String> respuesta = pedir(RUTA_DE_USUARIO, "jwt-tecnico-de-catedra-opaco");

            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(leer(respuesta).path("code").asString()).isEqualTo("AUTH_TOKEN_INVALID");
        }
    }

    @Nested
    @DisplayName("La API interna que consume el servicio de turnos")
    class ApiInterna {

        @Test
        @DisplayName("con el token tecnico y la autoridad SERVICE_TURNOS: pasa")
        void conTokenDeTurnos() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_INTERNA, TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(respuesta.body()).isEqualTo("agenda");
        }

        @Test
        @DisplayName("token tecnico bien formado sin la autoridad: 403 AUTH_FORBIDDEN")
        void sinLaAutoridad() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_INTERNA, TokensDePrueba.deServicioSinAutoridad());

            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(leer(respuesta).path("code").asString()).isEqualTo("AUTH_FORBIDDEN");
        }

        @Test
        @DisplayName("sin token: 401 AUTH_TOKEN_MISSING")
        void sinToken() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_INTERNA, null);

            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(leer(respuesta).path("code").asString()).isEqualTo("AUTH_TOKEN_MISSING");
        }
    }

    @Nested
    @DisplayName("Las dos identidades no son intercambiables (§9)")
    class IdentidadesSeparadas {

        @Test
        @DisplayName("el token del usuario final no sirve en la API interna")
        void tokenDeUsuarioEnApiInterna() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_INTERNA, TokensDePrueba.deUsuario());

            // 401 y no 403: esa cadena solo conoce el secreto interno, asi que
            // el token falla en la verificacion de firma y nunca llega a la
            // comprobacion de autoridad. Es la separacion criptografica del
            // ADR 0002, no una regla de autorizacion.
            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(leer(respuesta).path("code").asString()).isEqualTo("AUTH_TOKEN_INVALID");
        }

        @Test
        @DisplayName("el token tecnico interno no sirve en la API del usuario")
        void tokenInternoEnApiDeUsuario() throws Exception {
            HttpResponse<String> respuesta = pedir(RUTA_DE_USUARIO, TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(401);
            assertThat(leer(respuesta).path("code").asString()).isEqualTo("AUTH_TOKEN_INVALID");
        }
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        @DisplayName("el preflight de un origen configurado pasa sin token")
        void preflightDeOrigenPermitido() throws Exception {
            HttpResponse<String> respuesta = preflight("http://localhost:3000");

            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(respuesta.headers().firstValue("access-control-allow-origin"))
                    .hasValue("http://localhost:3000");
        }

        @Test
        @DisplayName("el preflight de un origen que no esta en la lista se rechaza")
        void preflightDeOrigenAjeno() throws Exception {
            HttpResponse<String> respuesta = preflight("https://sitio-ajeno.example");

            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(respuesta.headers().firstValue("access-control-allow-origin")).isEmpty();
        }
    }

    private HttpResponse<String> pedir(String ruta, String token) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base() + ruta)).GET();
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> preflight(String origen) throws Exception {
        HttpRequest pedido =
                HttpRequest.newBuilder(URI.create(base() + RUTA_DE_USUARIO))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .header("Origin", origen)
                        .header("Access-Control-Request-Method", "GET")
                        .build();
        return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://localhost:" + puerto;
    }

    private static JsonNode leer(HttpResponse<String> respuesta) {
        return JACKSON.readTree(respuesta.body());
    }
}

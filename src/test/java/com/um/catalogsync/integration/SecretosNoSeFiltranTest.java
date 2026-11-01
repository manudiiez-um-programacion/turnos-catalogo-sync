package com.um.catalogsync.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.infrastructure.client.CatedraRestClientConfig;
import com.um.catalogsync.integration.infrastructure.client.adapter.RestIntegrationConfigAdapter;
import com.um.catalogsync.integration.infrastructure.client.adapter.RestTechnicalAuthAdapter;
import com.um.catalogsync.integration.infrastructure.client.dto.AuthenticateRequestDto;
import com.um.catalogsync.integration.infrastructure.client.dto.IntegrationDto;
import com.um.catalogsync.integration.infrastructure.client.dto.TechnicalLoginResponseDto;
import com.um.catalogsync.integration.infrastructure.client.mapper.IntegrationConfigMapper;
import com.um.catalogsync.integration.infrastructure.config.CatedraApiProperties;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * El criterio de aceptacion central del issue #11: el JWT tecnico y
 * {@code redisPassword} no aparecen en logs, ni en respuestas HTTP, ni en el
 * mensaje de ninguna excepcion.
 *
 * <p><b>Como se prueba algo negativo.</b> No alcanza con revisar el codigo:
 * la regla se rompe por descuido y el descuido no se nota. Asi que se ejerce
 * el slice completo —login, relectura, y los tres caminos de error— con
 * secretos falsos que son cadenas inconfundibles, se captura <i>todo</i> lo
 * que el servicio escribio en el log a nivel TRACE, y se busca si alguna de
 * esas cadenas aparece. Si manana alguien agrega un {@code log.debug} con la
 * configuracion entera, esta prueba se pone roja.
 *
 * <p>El nivel TRACE no es casual: incluye los {@code debug} de los
 * adaptadores, que son los que mas cerca estan del secreto. Si se capturara
 * solo INFO, la prueba daria verde sin mirar justamente las lineas
 * riesgosas.
 */
class SecretosNoSeFiltranTest {

    @RegisterExtension
    static final WireMockExtension CATEDRA =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    /** Todo lo que no debe aparecer escrito en ninguna parte. */
    private static final List<String> SECRETOS = List.of(
            IntegrationFixtures.TOKEN_FALSO,
            IntegrationFixtures.REDIS_PASSWORD_FALSA,
            IntegrationFixtures.CLAVE_TECNICA);

    private ListAppender<ILoggingEvent> capturados;
    private Logger raiz;
    private Level nivelOriginal;

    @BeforeEach
    void capturarTodoElLog() {
        capturados = new ListAppender<>();
        capturados.start();
        raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        nivelOriginal = raiz.getLevel();
        raiz.setLevel(Level.TRACE);
        raiz.addAppender(capturados);
    }

    @AfterEach
    void dejarElLogComoEstaba() {
        raiz.detachAppender(capturados);
        raiz.setLevel(nivelOriginal);
        capturados.stop();
    }

    /** Todo lo que se escribio en el log, en un solo texto. */
    private String todoElLog() {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : capturados.list) {
            texto.append(evento.getLoggerName())
                    .append(' ')
                    .append(evento.getFormattedMessage())
                    .append('\n');
            if (evento.getThrowableProxy() != null) {
                texto.append(evento.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return texto.toString();
    }

    private static String trazaCompleta(Throwable error) {
        StringWriter destino = new StringWriter();
        error.printStackTrace(new PrintWriter(destino));
        return destino.toString();
    }

    private static CatedraApiProperties propiedades() {
        return new CatedraApiProperties(
                CATEDRA.baseUrl(),
                IntegrationFixtures.USUARIO_TECNICO,
                IntegrationFixtures.CLAVE_TECNICA,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                false);
    }

    private static TechnicalSessionCache slice(CatedraApiProperties propiedades) {
        RestClient cliente = new CatedraRestClientConfig().catedraRestClient(propiedades);
        IntegrationConfigMapper mapper = new IntegrationConfigMapper();
        return new TechnicalSessionCache(
                new RestTechnicalAuthAdapter(cliente, propiedades, mapper),
                new RestIntegrationConfigAdapter(cliente, mapper));
    }

    @Test
    @DisplayName("El camino feliz completo no escribe ningun secreto en el log")
    void elCaminoFelizNoEscribeSecretos() {
        CATEDRA.stubFor(post(urlEqualTo("/api/authenticate"))
                .willReturn(okJson(IntegrationFixtures.loginJson("PROVISIONED"))));
        CATEDRA.stubFor(get(urlEqualTo("/api/student/integration"))
                .willReturn(okJson(IntegrationFixtures.integrationJson("PROVISIONED"))));

        TechnicalSessionCache cache = slice(propiedades());
        cache.current();
        cache.refreshConfig();
        cache.renew();

        assertThat(todoElLog())
                .as("el log tiene que haber registrado algo, o la prueba no prueba nada")
                .isNotBlank()
                .doesNotContain(SECRETOS);
    }

    @Test
    @DisplayName("Un 401 que hace eco de la contraseña no la deja en la excepcion ni en el log")
    void elEcoDeUn401NoFiltraLaContrasena() {
        // El peor caso realista: el servidor devuelve en el cuerpo del error
        // el pedido que recibio. Si el adaptador encadenara la excepcion HTTP
        // -que incluye el cuerpo en su mensaje-, la contraseña terminaria en
        // la traza. Por eso ese camino no encadena la causa.
        CATEDRA.stubFor(post(urlEqualTo("/api/authenticate"))
                .willReturn(WireMock.unauthorized().withBody("""
                        {"error":"bad credentials","received":{"username":"%s","password":"%s"}}
                        """.formatted(
                        IntegrationFixtures.USUARIO_TECNICO,
                        IntegrationFixtures.CLAVE_TECNICA))));

        Throwable error = catchThrowable(() -> slice(propiedades()).current());

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).doesNotContain(SECRETOS);
        assertThat(trazaCompleta(error)).doesNotContain(SECRETOS);
        assertThat(todoElLog()).doesNotContain(SECRETOS);
    }

    @Test
    @DisplayName("Un 401 que hace eco del bearer no deja el token en la excepcion")
    void elEcoDeUnBearerNoFiltraElToken() {
        CATEDRA.stubFor(post(urlEqualTo("/api/authenticate"))
                .willReturn(okJson(IntegrationFixtures.loginJson("PROVISIONED"))));
        CATEDRA.stubFor(get(urlEqualTo("/api/student/integration"))
                .willReturn(WireMock.unauthorized().withBody("""
                        {"error":"expired","authorization":"Bearer %s"}
                        """.formatted(IntegrationFixtures.TOKEN_FALSO))));

        TechnicalSessionCache cache = slice(propiedades());
        cache.current();
        // El refresco reautentica ante el token rechazado, asi que no falla:
        // lo que se verifica es que nada de eso quedo escrito.
        cache.refreshConfig();

        assertThat(todoElLog()).doesNotContain(SECRETOS);
    }

    @Test
    @DisplayName("Una cuenta sin habilitar falla con un mensaje claro y sin secretos")
    void elErrorDeCuentaSinHabilitarNoFiltraNada() {
        CATEDRA.stubFor(post(urlEqualTo("/api/authenticate"))
                .willReturn(okJson(IntegrationFixtures.loginJson("PENDING"))));

        Throwable error = catchThrowable(() -> slice(propiedades()).current());

        assertThat(error)
                .hasMessageContaining("PENDING")
                .hasMessageContaining(IntegrationFixtures.GROUP_ID);
        assertThat(trazaCompleta(error)).doesNotContain(SECRETOS);
        assertThat(todoElLog()).doesNotContain(SECRETOS);
    }

    @Test
    @DisplayName("Serializar la configuracion a JSON no expone la clave de Redis")
    void serializarLaConfiguracionNoExponeLaClave() throws Exception {
        // Vale como prueba de "ni en respuestas HTTP": hoy el slice no tiene
        // capa web, asi que lo que se fija es la propiedad que lo haria
        // seguro si manana algun endpoint devolviera este objeto. El punto es
        // la visibilidad del accesor: Secret.value() no lleva prefijo get,
        // asi que para Jackson la clase no tiene ninguna propiedad.
        //
        // Con la configuracion por defecto eso hace que Jackson se niegue a
        // serializar y lance (FAIL_ON_EMPTY_BEANS), que es todavia mas seguro
        // que filtrar: un endpoint que devolviera esto fallaria ruidosamente
        // en vez de publicar el secreto. Aca se desactiva esa bandera a
        // proposito, para forzar el peor caso -que Jackson intente
        // serializarlo igual- y verificar que ni asi sale el valor.
        IntegrationConfig config = IntegrationFixtures.provisionada();
        ObjectMapper tolerante =
                new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        String json = tolerante.writeValueAsString(config);

        assertThat(json)
                .doesNotContain(SECRETOS)
                .contains("\"redisPassword\":{}")
                .as("el resto de la configuracion si se serializa")
                .contains(IntegrationFixtures.GROUP_ID);
    }

    @Test
    @DisplayName("Jackson no encuentra ninguna propiedad en Secret: por eso no puede filtrarlo")
    void secretNoTienePropiedadesParaJackson() throws Exception {
        ObjectMapper tolerante =
                new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        assertThat(tolerante.writeValueAsString(
                        com.um.catalogsync.shared.domain.model.Secret.of(
                                IntegrationFixtures.TOKEN_FALSO)))
                .isEqualTo("{}");
    }

    @Test
    @DisplayName("Los DTOs del borde y las propiedades tampoco imprimen secretos")
    void losToStringDelBordeEstanTapados() {
        IntegrationDto integration = new IntegrationDto(
                IntegrationFixtures.GROUP_ID, "redis.catedra.internal", 6379, "grp_x",
                IntegrationFixtures.REDIS_PASSWORD_FALSA, "catedra:sync:*", "alumnos:x:*",
                "kafka:9092", "alumnos-x", "catedra.catalog.x", "alumnos.turnos.acciones.x",
                "catedra.turnos.telefono.x", "PROVISIONED");

        assertThat(AuthenticateRequestDto.conVigenciaLarga(
                        IntegrationFixtures.USUARIO_TECNICO, IntegrationFixtures.CLAVE_TECNICA)
                        .toString())
                .doesNotContain(SECRETOS);
        assertThat(integration.toString()).doesNotContain(SECRETOS);
        assertThat(new TechnicalLoginResponseDto(IntegrationFixtures.TOKEN_FALSO, integration)
                        .toString())
                .doesNotContain(SECRETOS);
        assertThat(propiedades().toString()).doesNotContain(SECRETOS);
        assertThat(IntegrationFixtures.sesion().toString()).doesNotContain(SECRETOS);
    }
}

package com.um.catalogsync.integration.infrastructure.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.exception.CatedraIntegrationUnavailableException;
import com.um.catalogsync.integration.application.exception.TechnicalAuthenticationFailedException;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.domain.model.TechnicalSession;
import com.um.catalogsync.integration.domain.model.UnknownProvisioningStatusException;
import com.um.catalogsync.integration.infrastructure.client.CatedraClientFixtures;
import com.um.catalogsync.integration.infrastructure.client.mapper.IntegrationConfigMapper;
import com.um.catalogsync.integration.infrastructure.config.CatedraApiProperties;
import com.um.catalogsync.shared.domain.model.Secret;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * El login tecnico contra un doble de la API de la catedra.
 *
 * <p>Las respuestas son los ejemplos de §5.2 textuales, incluido
 * {@code id_token} en snake_case, que es el unico nombre del contrato que
 * no sigue la convencion {@code camelCase} de §4. Si el
 * {@code @JsonProperty} del DTO se perdiera, el token llegaria {@code null} y
 * esta prueba lo detecta; en produccion el sintoma seria un 401 en la
 * llamada siguiente, mucho mas lejos de la causa.
 */
class RestTechnicalAuthAdapterTest {

    @RegisterExtension
    static final WireMockExtension CATEDRA =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String RUTA = "/api/authenticate";

    private RestTechnicalAuthAdapter adaptador(CatedraApiProperties propiedades) {
        return new RestTechnicalAuthAdapter(
                CatedraClientFixtures.restClient(propiedades),
                propiedades,
                new IntegrationConfigMapper());
    }

    private RestTechnicalAuthAdapter adaptador() {
        return adaptador(CatedraClientFixtures.propiedades(CATEDRA.baseUrl()));
    }

    @Test
    @DisplayName("Un login correcto devuelve el token y la configuracion del contrato")
    void loginCorrecto() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA))
                .willReturn(okJson(IntegrationFixtures.loginJson("PROVISIONED"))));

        TechnicalSession sesion = adaptador().authenticate();

        assertThat(sesion.getTechnicalToken())
                .isEqualTo(Secret.of(IntegrationFixtures.TOKEN_FALSO));
        assertThat(sesion.getIntegration().getProvisioningStatus())
                .isEqualTo(ProvisioningStatus.PROVISIONED);
        assertThat(sesion.getIntegration().getGroupId()).isEqualTo(IntegrationFixtures.GROUP_ID);
        assertThat(sesion.getIntegration().getKafkaCatalogTopic())
                .isEqualTo("catedra.catalog." + IntegrationFixtures.GROUP_ID);
        assertThat(sesion.getIntegration().getRedisPassword())
                .isEqualTo(Secret.of(IntegrationFixtures.REDIS_PASSWORD_FALSA));
    }

    @Test
    @DisplayName("El pedido va a /api/authenticate con rememberMe en true")
    void elPedidoRespetaElContrato() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA))
                .willReturn(okJson(IntegrationFixtures.loginJson("PROVISIONED"))));

        adaptador().authenticate();

        // rememberMe es lo que da la vigencia de un año (§5.2): sin eso el
        // token seria corto y habria que reautenticarse todo el tiempo.
        CATEDRA.verify(postRequestedFor(urlEqualTo(RUTA))
                .withRequestBody(equalToJson("""
                        {
                          "username": "%s",
                          "password": "%s",
                          "rememberMe": true
                        }
                        """.formatted(
                        IntegrationFixtures.USUARIO_TECNICO,
                        IntegrationFixtures.CLAVE_TECNICA))));
    }

    @Test
    @DisplayName("Un 401 es un problema de credenciales y dice donde mirar")
    void credencialesRechazadas() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA)).willReturn(WireMock.unauthorized()));

        assertThatThrownBy(() -> adaptador().authenticate())
                .isInstanceOf(TechnicalAuthenticationFailedException.class)
                .hasMessageContaining(IntegrationFixtures.USUARIO_TECNICO)
                .hasMessageContaining("CATEDRA_TECHNICAL_PASSWORD")
                .hasNoCause();
    }

    @Test
    @DisplayName("Cualquier otro error es de infraestructura y nombra el codigo")
    void otroErrorHttp() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA))
                .willReturn(WireMock.aResponse().withStatus(503)));

        assertThatThrownBy(() -> adaptador().authenticate())
                .isInstanceOf(CatedraIntegrationUnavailableException.class)
                .hasMessageContaining("503")
                .hasMessageContaining(RUTA);
    }

    @Test
    @DisplayName("Un 200 sin id_token no se acepta: la respuesta no cumple el contrato")
    void respuestaSinToken() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA)).willReturn(okJson("""
                {"integration": %s}
                """.formatted(IntegrationFixtures.integrationJson("PROVISIONED")))));

        assertThatThrownBy(() -> adaptador().authenticate())
                .isInstanceOf(CatedraIntegrationUnavailableException.class)
                .hasMessageContaining("sin id_token");
    }

    @Test
    @DisplayName("Un 200 sin el objeto integration tampoco")
    void respuestaSinIntegration() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA)).willReturn(okJson("""
                {"id_token": "%s"}
                """.formatted(IntegrationFixtures.TOKEN_FALSO))));

        assertThatThrownBy(() -> adaptador().authenticate())
                .isInstanceOf(CatedraIntegrationUnavailableException.class)
                .hasMessageContaining("sin el objeto integration");
    }

    @Test
    @DisplayName("Un provisioningStatus desconocido detiene el login")
    void estadoDesconocido() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA))
                .willReturn(okJson(IntegrationFixtures.loginJson("HABILITADA"))));

        assertThatThrownBy(() -> adaptador().authenticate())
                .isInstanceOf(UnknownProvisioningStatusException.class);
    }

    @Test
    @DisplayName("Si la catedra no responde, el timeout corta y el mensaje habla de ZeroTier")
    void timeoutDeLectura() {
        CATEDRA.stubFor(post(urlEqualTo(RUTA))
                .willReturn(okJson(IntegrationFixtures.loginJson("PROVISIONED"))
                        .withFixedDelay(2000)));
        CatedraApiProperties impaciente =
                CatedraClientFixtures.propiedades(CATEDRA.baseUrl(), Duration.ofMillis(200));

        assertThatThrownBy(() -> adaptador(impaciente).authenticate())
                .isInstanceOf(CatedraIntegrationUnavailableException.class)
                .hasMessageContaining("ZeroTier")
                .cause()
                .isNotNull();
    }

    @Test
    @DisplayName("Sin cuenta tecnica configurada no sale a la red y dice que variable falta")
    void sinConfigurarNoLlamaALaCatedra() {
        CatedraApiProperties sinCuenta = new CatedraApiProperties(
                CATEDRA.baseUrl(), "", "", Duration.ofSeconds(2), Duration.ofSeconds(2), false);

        assertThatThrownBy(() -> adaptador(sinCuenta).authenticate())
                .isInstanceOf(CatedraIntegrationUnavailableException.class)
                .hasMessageContaining("CATEDRA_TECHNICAL_USERNAME")
                .hasMessageContaining("CATEDRA_TECHNICAL_PASSWORD")
                .hasMessageContaining("/api/student/register");

        // Lo importante: no se intento la llamada. Un 401 contra la catedra
        // mandaria a revisar las credenciales en lugar de completarlas.
        CATEDRA.verify(0, postRequestedFor(urlEqualTo(RUTA)));
    }
}

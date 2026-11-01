package com.um.catalogsync.integration.infrastructure.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.exception.CatedraIntegrationUnavailableException;
import com.um.catalogsync.integration.application.exception.TechnicalTokenRejectedException;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.infrastructure.client.CatedraClientFixtures;
import com.um.catalogsync.integration.infrastructure.client.mapper.IntegrationConfigMapper;
import com.um.catalogsync.shared.domain.model.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * La relectura de la configuracion contra un doble de la API.
 *
 * <p>La diferencia de forma con el login importa y se prueba: §5.3 devuelve
 * el objeto {@code integration} <b>sin envoltorio</b>, no dentro de otro
 * objeto como §5.2.
 */
class RestIntegrationConfigAdapterTest {

    @RegisterExtension
    static final WireMockExtension CATEDRA =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String RUTA = "/api/student/integration";
    private static final Secret TOKEN = Secret.of(IntegrationFixtures.TOKEN_FALSO);

    private RestIntegrationConfigAdapter adaptador() {
        return new RestIntegrationConfigAdapter(
                CatedraClientFixtures.restClient(
                        CatedraClientFixtures.propiedades(CATEDRA.baseUrl())),
                new IntegrationConfigMapper());
    }

    @Test
    @DisplayName("Lee el objeto integration sin envoltorio")
    void leeLaConfiguracionVigente() {
        CATEDRA.stubFor(get(urlEqualTo(RUTA))
                .willReturn(okJson(IntegrationFixtures.integrationJson("PROVISIONED"))));

        IntegrationConfig config = adaptador().fetchCurrent(TOKEN);

        assertThat(config.getGroupId()).isEqualTo(IntegrationFixtures.GROUP_ID);
        assertThat(config.getProvisioningStatus()).isEqualTo(ProvisioningStatus.PROVISIONED);
        assertThat(config.getRedisPort()).isEqualTo(6379);
        assertThat(config.getKafkaConsumerGroupId())
                .isEqualTo("alumnos-" + IntegrationFixtures.GROUP_ID);
    }

    @Test
    @DisplayName("Manda el JWT tecnico como bearer, que es lo que pide §4")
    void mandaElBearer() {
        CATEDRA.stubFor(get(urlEqualTo(RUTA))
                .willReturn(okJson(IntegrationFixtures.integrationJson("PROVISIONED"))));

        adaptador().fetchCurrent(TOKEN);

        CATEDRA.verify(getRequestedFor(urlEqualTo(RUTA))
                .withHeader("Authorization", equalTo("Bearer " + IntegrationFixtures.TOKEN_FALSO)));
    }

    @Test
    @DisplayName("Un 401 aca significa token vencido, no credenciales malas")
    void tokenRechazado() {
        CATEDRA.stubFor(get(urlEqualTo(RUTA)).willReturn(WireMock.unauthorized()));

        // La distincion es la que permite reautenticarse una vez en lugar de
        // reintentar algo que nunca va a funcionar.
        assertThatThrownBy(() -> adaptador().fetchCurrent(TOKEN))
                .isInstanceOf(TechnicalTokenRejectedException.class)
                .hasMessageContaining(RUTA)
                .hasNoCause();
    }

    @Test
    @DisplayName("Un 500 es infraestructura")
    void errorDelServidor() {
        CATEDRA.stubFor(get(urlEqualTo(RUTA)).willReturn(WireMock.serverError()));

        assertThatThrownBy(() -> adaptador().fetchCurrent(TOKEN))
                .isInstanceOf(CatedraIntegrationUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Un cuerpo vacio con 200 no se acepta")
    void cuerpoVacio() {
        CATEDRA.stubFor(get(urlEqualTo(RUTA))
                .willReturn(WireMock.aResponse().withStatus(200)));

        assertThatThrownBy(() -> adaptador().fetchCurrent(TOKEN))
                .isInstanceOf(CatedraIntegrationUnavailableException.class);
    }
}

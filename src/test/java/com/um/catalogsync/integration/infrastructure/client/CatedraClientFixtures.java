package com.um.catalogsync.integration.infrastructure.client;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.infrastructure.config.CatedraApiProperties;
import java.time.Duration;
import org.springframework.web.client.RestClient;

/**
 * Armado del cliente REST para las pruebas de los adaptadores.
 *
 * <p>El {@link RestClient} se construye con la <b>misma</b> clase de
 * configuracion que usa la aplicacion, {@link CatedraRestClientConfig}, y no
 * con un {@code RestClient} armado a mano en la prueba. Si se armara aparte,
 * las pruebas pasarian con un cliente que no es el que corre en produccion:
 * los timeouts, la URL base y los conversores serian otros, y justamente
 * esas tres cosas son las que mas facil se rompen.
 *
 * <p>Los timeouts son cortos a proposito: en una prueba no hay un enlace
 * ZeroTier que justifique esperar 20 segundos, y una prueba que verifica el
 * timeout tiene que terminar rapido.
 */
public final class CatedraClientFixtures {

    private CatedraClientFixtures() {
    }

    public static CatedraApiProperties propiedades(String baseUrl) {
        return propiedades(baseUrl, Duration.ofSeconds(2));
    }

    public static CatedraApiProperties propiedades(String baseUrl, Duration readTimeout) {
        return new CatedraApiProperties(
                baseUrl,
                IntegrationFixtures.USUARIO_TECNICO,
                IntegrationFixtures.CLAVE_TECNICA,
                Duration.ofSeconds(2),
                readTimeout,
                false);
    }

    public static RestClient restClient(CatedraApiProperties propiedades) {
        return new CatedraRestClientConfig().catedraRestClient(propiedades);
    }
}

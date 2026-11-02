package com.um.catalogsync.integration.infrastructure.client;

import com.um.catalogsync.integration.infrastructure.config.CatedraApiProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * El {@link RestClient} con el que se habla con la API de la catedra.
 *
 * <p>Es un bean propio y con nombre, no el {@code RestClient} por defecto:
 * este lleva la URL base de la catedra y timeouts pensados para ese enlace.
 * Cuando aparezcan otros clientes HTTP —el servicio de turnos, por ejemplo—
 * no tienen por que heredar esta configuracion.
 *
 * <p><b>Los timeouts no son decorativos.</b> El servidor de la catedra se
 * alcanza por ZeroTier y el enlace tiene ~228 ms de ida y vuelta medidos
 * (D6). Con los valores por defecto del JDK —sin timeout de lectura— una
 * llamada que no responde cuelga el hilo para siempre, y en el arranque eso
 * seria un servicio que nunca termina de arrancar ni dice por que. Los
 * valores concretos viven en {@code application.yaml} para poder ajustarlos
 * sin recompilar; el issue de robustez (#31) va a revisarlos junto con los
 * reintentos.
 */
@Configuration
@EnableConfigurationProperties(CatedraApiProperties.class)
public class CatedraRestClientConfig {

    /** Nombre del bean, para inyectarlo explicito en los adaptadores. */
    public static final String CATEDRA_REST_CLIENT = "catedraRestClient";

    /**
     * <p>Se arma con {@code RestClient.builder()} y no inyectando un
     * {@code RestClient.Builder}: en Spring Boot 4 la autoconfiguracion de
     * {@code RestClient} vive en un modulo aparte que este proyecto no trae,
     * asi que ese bean no existe. El builder estatico registra igual los
     * conversores por defecto, Jackson incluido, que es todo lo que hace
     * falta para este contrato JSON.
     *
     * <p>El timeout de conexion es del {@code HttpClient} del JDK y el de
     * lectura es de la factory: son dos cosas distintas y las dos importan.
     * Sin el primero, un host inalcanzable tarda lo que tarde el sistema
     * operativo; sin el segundo, un servidor que acepta la conexion y no
     * contesta nunca deja la llamada colgada.
     */
    @Bean(CATEDRA_REST_CLIENT)
    public RestClient catedraRestClient(CatedraApiProperties propiedades) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(propiedades.connectTimeout())
                // HTTP/1.1 explicito. El cliente del JDK, por defecto,
                // intenta negociar HTTP/2 y sobre HTTP sin TLS eso es un
                // upgrade h2c que no todos los servidores manejan igual: el
                // sintoma es un "Received RST_STREAM: Stream cancelled" en el
                // POST, que no dice nada sobre la causa. Lo encontro la
                // prueba del adaptador contra WireMock. No se pierde nada:
                // son unas pocas llamadas chicas y el contrato de la catedra
                // no necesita HTTP/2.
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(propiedades.readTimeout());

        return RestClient.builder()
                .baseUrl(propiedades.baseUrl())
                .requestFactory(factory)
                .build();
    }
}

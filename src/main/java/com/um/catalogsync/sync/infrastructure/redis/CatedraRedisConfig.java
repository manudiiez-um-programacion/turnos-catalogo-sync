package com.um.catalogsync.sync.infrastructure.redis;

import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra la conexion a Redis y sus propiedades.
 *
 * <p>Mismo patron que {@code CatedraRestClientConfig}: las propiedades se
 * habilitan donde se usan, y la pieza que habla con la catedra es un bean
 * con nombre propio y no el que pueda autoconfigurar Spring Boot. Cuando
 * aparezca el consumidor de Kafka (#24) va a necesitar su propia
 * configuracion por el mismo motivo —los datos de conexion tambien le llegan
 * de {@code integration}— y no deberia heredar nada de esta.
 *
 * <p>La conexion se declara como {@code @Bean} y no con {@code @Component}
 * sobre la clase porque {@code CatedraRedisConnection} no es un componente
 * descubrible: necesita las propiedades ya validadas y existe una sola.
 * Declararla aca deja en un solo archivo que se conecta, con que timeouts y
 * de donde salen.
 */
@Configuration
@EnableConfigurationProperties(CatedraRedisProperties.class)
public class CatedraRedisConfig {

    @Bean(destroyMethod = "destroy")
    public CatedraRedisConnection catedraRedisConnection(
            GetIntegrationConfigUseCase integracion, CatedraRedisProperties propiedades) {
        return new CatedraRedisConnection(integracion, propiedades);
    }
}

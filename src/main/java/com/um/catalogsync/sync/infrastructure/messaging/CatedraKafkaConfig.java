package com.um.catalogsync.sync.infrastructure.messaging;

import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registra el consumidor del topic de catalogo y sus propiedades.
 *
 * <p>Mismo patron que {@code CatedraRedisConfig} y por el mismo motivo: la
 * pieza que habla con la catedra es un bean con nombre propio y no la que
 * pueda autoconfigurar Spring Boot a partir de {@code spring.kafka.*}. El
 * consumidor se declara acá y no con {@code @Component} sobre la clase
 * porque necesita las propiedades ya validadas y existe uno solo.
 *
 * <p><b>Acá se habilita el planificador.</b> Lo usan dos cosas del slice: el
 * reintento que mantiene enganchado al consumidor y la purga de
 * {@code processed_event}. Las dos nacen de este issue —un consumidor que
 * arranca tarde y una tabla de idempotencia que crece— asi que la anotacion
 * vive donde se la necesita por primera vez y no en una clase de
 * configuracion global que no explicaria para que esta.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CatedraKafkaProperties.class)
public class CatedraKafkaConfig {

    @Bean(destroyMethod = "destroy")
    public CatalogUpdatedConsumer catalogUpdatedConsumer(
            GetIntegrationConfigUseCase integracion,
            CatalogUpdatedListener listener,
            CatedraKafkaProperties propiedades) {
        return new CatalogUpdatedConsumer(integracion, listener, propiedades);
    }
}

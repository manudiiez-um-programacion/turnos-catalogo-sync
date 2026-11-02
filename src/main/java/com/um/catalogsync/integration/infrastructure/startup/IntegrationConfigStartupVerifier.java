package com.um.catalogsync.integration.infrastructure.startup;

import com.um.catalogsync.integration.application.service.IntegrationService;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Resuelve la integracion tecnica al arrancar y detiene el arranque si no
 * esta usable.
 *
 * <p><b>Por que fallar al arrancar.</b> Lo pide el issue, y la razon es que
 * no hay nada que este servicio pueda hacer sin la configuracion: sin
 * {@code redisPassword} no lee los cambios incrementales y sin el topic no
 * sabe a que suscribirse. Un servicio que arranca sano y falla en la primera
 * sincronizacion esconde el problema detras de un error de Redis que no
 * menciona la causa. Fallar aca lo pone en la primera linea del log, cuando
 * todavia nadie depende del servicio.
 *
 * <p><b>Por que un {@code ApplicationRunner}.</b> Se ejecuta con el contexto
 * ya levantado y, si lanza, {@code SpringApplication.run} propaga y el
 * proceso termina con error. Un {@code @PostConstruct} correria durante la
 * creacion del bean y mezclaria una falla de red con un error de
 * configuracion del contexto, que se diagnostica distinto.
 *
 * <p><b>Por que se puede apagar.</b> Las pruebas levantan el contexto
 * completo ({@code @SpringBootTest}) y no tienen la red privada de la
 * catedra; en CI tampoco existe. Con la verificacion encendida, cada prueba
 * de integracion intentaria autenticarse contra un servidor inalcanzable y
 * fallaria por el entorno y no por el codigo. El perfil {@code test} la
 * apaga. Lo que no se apaga nunca es en el perfil real: el valor por
 * defecto es {@code true}, asi que olvidarse de configurarlo deja la
 * verificacion encendida, que es el lado seguro del olvido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "catedra.api",
        name = "verify-on-startup",
        havingValue = "true",
        matchIfMissing = true)
public class IntegrationConfigStartupVerifier implements ApplicationRunner {

    private final IntegrationService integracion;

    @Override
    public void run(ApplicationArguments args) {
        IntegrationConfig configuracion = integracion.current();
        log.info(
                "Integracion tecnica verificada. groupId={}, topic de catalogo={},"
                        + " consumer group={}",
                configuracion.getGroupId(),
                configuracion.getKafkaCatalogTopic(),
                configuracion.getKafkaConsumerGroupId());
    }
}

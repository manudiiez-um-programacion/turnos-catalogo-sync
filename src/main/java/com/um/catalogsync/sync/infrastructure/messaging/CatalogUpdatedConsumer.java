package com.um.catalogsync.sync.infrastructure.messaging;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * El consumidor del topic de catalogo, armado a mano y arrancado tarde.
 *
 * <p><b>Por que no hay ningun {@code @KafkaListener}.</b> La anotacion
 * necesita el topic y el consumer group en tiempo de registro de beans, y
 * este servicio no los tiene entonces: llegan en el objeto
 * {@code integration} de la catedra (§15.2). Resolverlos con SpEL al
 * registrar el listener obligaria a hablar con la catedra durante el arranque
 * del contexto, y el arranque no puede depender de la red privada: el
 * servicio tiene que levantar igual cuando ZeroTier no esta (D6, y es la
 * misma decision que tomaron la conexion a Redis y la sincronizacion de
 * arranque).
 *
 * <p><b>Como arranca entonces.</b> Se intenta cuando la aplicacion esta
 * lista y, si la configuracion de integracion todavia no se puede resolver,
 * se reintenta cada {@code startup-retry-interval}. No hay estado de "falló
 * para siempre": mientras el servicio este arriba, va a seguir intentando
 * engancharse al topic, y el intento que funcione no necesita un reinicio.
 *
 * <p><b>La confirmacion de offset es manual y eso es el contrato.</b>
 * {@code enable.auto.commit} va en {@code false} y el modo de confirmacion es
 * {@code MANUAL_IMMEDIATE}: el unico que confirma es el listener, despues de
 * que la transaccion commiteo (§16). Con confirmacion automatica, el cliente
 * de Kafka commitearia en segundo plano cada cinco segundos, y un mensaje
 * entregado pero no procesado podria quedar confirmado: una notificacion
 * perdida sin dejar rastro.
 *
 * <p><b>{@code auto.offset.reset} en {@code earliest}.</b> Un consumer group
 * nuevo —el primer arranque, o un cambio de group en {@code integration}—
 * arranca desde el principio del topic y no desde el final. Con
 * {@code latest} se perderian las notificaciones publicadas antes del primer
 * arranque, que es exactamente el caso del primer despliegue. Procesar de mas
 * es inofensivo: la deduplicacion absorbe lo ya visto y las versiones viejas
 * las resuelve la comparacion de versiones (#27).
 *
 * <p><b>Los reintentos son limitados y observables</b> (§18.4). Ante una
 * falla transitoria el mismo mensaje se reintenta {@code max-retries} veces
 * esperando {@code retry-interval}; si se agotan, queda un ERROR y el
 * consumidor <b>se detiene</b> en lugar de saltearse el mensaje. Detenerse
 * es lo correcto: como el offset nunca se confirmo, el reintento periodico
 * de arranque lo vuelve a levantar y Kafka reentrega desde el ultimo offset
 * confirmado. Saltearlo dejaria un hueco silencioso en las notificaciones.
 */
@Slf4j
@RequiredArgsConstructor
public class CatalogUpdatedConsumer implements DisposableBean {

    private final GetIntegrationConfigUseCase integracion;
    private final CatalogUpdatedListener listener;
    private final CatedraKafkaProperties propiedades;

    private ConcurrentMessageListenerContainer<String, String> contenedor;

    /** Topic y consumer group con los que se armo el contenedor vigente. */
    private String huella;

    /** Si el consumidor esta enganchado al topic ahora mismo. */
    public synchronized boolean estaEscuchando() {
        return contenedor != null && contenedor.isRunning();
    }

    /**
     * Arranca el consumidor si hace falta, o no hace nada si ya corre.
     *
     * <p>Es idempotente a proposito: la llaman el arranque de la aplicacion
     * y el reintento periodico, y las dos pueden pisarse. Tambien se
     * reconstruye el contenedor si cambiaron el topic o el consumer group,
     * por el mismo motivo que la conexion a Redis se reabre si cambia la
     * configuracion (§5.3, §5.4).
     *
     * @return si quedo escuchando
     */
    public synchronized boolean arrancarSiHaceFalta() {
        if (!propiedades.enabled()) {
            return false;
        }
        IntegrationConfig config = integracion.get();
        String huellaActual = config.getKafkaCatalogTopic() + "@" + config.getKafkaConsumerGroupId();

        if (estaEscuchando() && huellaActual.equals(huella)) {
            return true;
        }
        if (contenedor != null) {
            log.info("Reconstruyendo el consumidor del topic de catalogo");
            detener();
        }
        contenedor = construir(config);
        huella = huellaActual;
        contenedor.start();
        log.info("Consumidor enganchado al topic \"{}\" con el consumer group \"{}\"",
                config.getKafkaCatalogTopic(), config.getKafkaConsumerGroupId());
        return true;
    }

    private ConcurrentMessageListenerContainer<String, String> construir(IntegrationConfig config) {
        // StringDeserializer para clave y valor, y el JSON lo lee el mapper.
        // Con un JsonDeserializer, un mensaje malformado reventaria dentro
        // del cliente de Kafka —antes de que el listener lo vea— y la unica
        // forma de no trabar la particion seria envolverlo en un
        // ErrorHandlingDeserializer y despues reconocer el payload fallido.
        // Leyendo strings, el mensaje envenenado llega al listener como
        // cualquier otro y el salteo queda escrito donde se puede leer.
        var factory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, config.getKafkaConsumerGroupId(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));

        ContainerProperties propiedadesDelContenedor =
                new ContainerProperties(config.getKafkaCatalogTopic());
        propiedadesDelContenedor.setMessageListener(listener);
        propiedadesDelContenedor.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        propiedadesDelContenedor.setPollTimeout(propiedades.pollTimeout().toMillis());
        propiedadesDelContenedor.setGroupId(config.getKafkaConsumerGroupId());

        var nuevo = new ConcurrentMessageListenerContainer<>(factory, propiedadesDelContenedor);
        // Un solo hilo: el topic de catalogo es de notificaciones y las
        // notificaciones se procesan en orden de version. Mas consumidores
        // en el mismo group solo servirian si el topic tuviera varias
        // particiones, y aun asi el trabajo por mensaje es minimo.
        nuevo.setConcurrency(1);
        nuevo.setBeanName("catalogUpdatedContainer");
        nuevo.setCommonErrorHandler(manejadorDeFallas());
        return nuevo;
    }

    /**
     * Reintentos acotados y, al agotarse, parada del consumidor.
     *
     * <p>El "recoverer" de Spring se llama cuando se agotaron los intentos y
     * normalmente se saltea el registro. Acá no: se deja el ERROR y se
     * detiene el contenedor, porque el offset no se confirmo y detenerse es
     * lo que permite que el reintento de arranque lo reentregue. Saltearlo
     * perderia la notificacion.
     *
     * <p>La parada va en otro hilo: {@code stop()} espera a que el hilo del
     * listener termine, y acá lo estamos ejecutando nosotros mismos.
     */
    private DefaultErrorHandler manejadorDeFallas() {
        return new DefaultErrorHandler(
                (mensaje, falla) -> {
                    log.error("Se agotaron los {} reintentos del mensaje en {}-{}@{}:"
                                    + " se detiene el consumidor sin confirmar el offset,"
                                    + " para que se reentregue al volver a arrancar",
                            propiedades.maxRetries(), mensaje.topic(), mensaje.partition(),
                            mensaje.offset(), falla);
                    CompletableFuture.runAsync(this::detener);
                },
                new FixedBackOff(propiedades.retryInterval().toMillis(),
                        propiedades.maxRetries()));
    }

    /** Detiene el consumidor sin confirmar nada. Lo llama Spring al apagar. */
    @Override
    public void destroy() {
        detener();
    }

    private synchronized void detener() {
        if (contenedor != null) {
            contenedor.stop();
            contenedor = null;
            huella = null;
            log.info("Consumidor del topic de catalogo detenido");
        }
    }
}

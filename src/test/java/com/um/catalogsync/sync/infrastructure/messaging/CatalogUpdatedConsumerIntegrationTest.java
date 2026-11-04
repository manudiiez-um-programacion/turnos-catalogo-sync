package com.um.catalogsync.sync.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.domain.ports.in.HandleCatalogUpdatedUseCase;
import com.um.catalogsync.sync.infrastructure.messaging.mapper.CatalogUpdatedMapper;
import com.um.catalogsync.sync.infrastructure.metrics.SyncMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * El consumidor contra un Kafka real y la base de verdad.
 *
 * <p><b>Que agrega sobre las pruebas sin broker.</b> Tres cosas que un doble
 * no puede demostrar, y son justamente los criterios del issue:
 *
 * <ul>
 *   <li>Que el offset confirmado sea real: se detiene el consumidor, se
 *       arranca otro con el <b>mismo</b> consumer group y el mensaje ya
 *       procesado no vuelve.
 *   <li>Que un mensaje reentregado se absorba por deduplicacion: se arranca
 *       un consumer group <b>nuevo</b>, que vuelve a leer el topic desde el
 *       principio, y el estado final no cambia (§18.1).
 *   <li>Que un mensaje envenenado no trabe la particion: se publica uno que
 *       no se puede deserializar, y el siguiente se procesa igual.
 * </ul>
 *
 * <p><b>El consumidor se arma a mano</b> con un doble del unico puerto que no
 * es Kafka —la configuracion de integracion, de donde salen el bootstrap, el
 * topic y el consumer group— igual que hace la prueba del adaptador de Redis
 * con la clave. Todo lo demas es el codigo de produccion: el listener, el
 * mapper, el caso de uso y el adaptador JPA que escribe en PostgreSQL.
 *
 * <p>Un contenedor de Kafka para toda la clase, arrancado en un bloque
 * estatico y no con {@code @Container}, por el mismo motivo que el PostgreSQL
 * de la clase base: Ryuk lo limpia al terminar la JVM.
 */
class CatalogUpdatedConsumerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        KAFKA.start();
    }

    @Autowired
    private HandleCatalogUpdatedUseCase handleCatalogUpdated;

    @Autowired
    private JdbcTemplate jdbc;

    /** Un topic por prueba: el contenedor es compartido. */
    private String topic;

    private CatalogUpdatedConsumer consumidor;
    private KafkaProducer<String, String> productor;

    @BeforeEach
    void prepararElTopicYElProductor() {
        topic = "catedra.catalog.prueba-" + UUID.randomUUID();
        productor = new KafkaProducer<>(propiedadesDelProductor());
    }

    @AfterEach
    void apagar() {
        if (consumidor != null) {
            consumidor.destroy();
        }
        productor.close();
    }

    // --- El camino del contrato -------------------------------------------

    @Test
    @DisplayName("Un CatalogUpdated del topic queda registrado en processed_event")
    void consumeYRegistra() {
        publicar(CatalogUpdatedFixtures.mensaje());
        arrancar("grupo-" + UUID.randomUUID());

        esperarQueHaya(1);
        assertThat(idsRegistrados()).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
    }

    @Test
    @DisplayName("Varias notificaciones se procesan todas")
    void consumeVarias() {
        publicar(CatalogUpdatedFixtures.mensaje("evento-7", 7L));
        publicar(CatalogUpdatedFixtures.mensaje("evento-8", 8L));
        publicar(CatalogUpdatedFixtures.mensaje("evento-9", 9L));
        arrancar("grupo-" + UUID.randomUUID());

        esperarQueHaya(3);
        assertThat(idsRegistrados()).containsExactly("evento-7", "evento-8", "evento-9");
    }

    @Test
    @DisplayName("El consumidor arranca desde el principio del topic")
    void arrancaDesdeElPrincipio() {
        // auto.offset.reset en earliest: el mensaje se publico ANTES de que
        // el consumidor existiera. Con latest se perderian las
        // notificaciones previas al primer arranque, que es exactamente el
        // caso del primer despliegue.
        publicar(CatalogUpdatedFixtures.mensaje());

        arrancar("grupo-" + UUID.randomUUID());

        esperarQueHaya(1);
    }

    // --- Que el offset confirmado sea real --------------------------------

    @Test
    @DisplayName("Un consumidor nuevo del mismo grupo no reprocesa lo confirmado")
    void elOffsetConfirmadoSobrevive() {
        String grupo = "grupo-" + UUID.randomUUID();
        publicar(CatalogUpdatedFixtures.mensaje());
        arrancar(grupo);
        esperarQueHaya(1);

        // Se baja el consumidor y se levanta otro con el mismo group: Kafka
        // le entrega desde el ultimo offset confirmado. Si el listener no
        // hubiera confirmado, el mensaje volveria.
        consumidor.destroy();
        limpiarLaTabla();
        arrancar(grupo);

        // Nada vuelve a procesarse. Se espera un rato real: la ausencia de
        // mensajes no se puede verificar al instante.
        esperarUnRatoYVerificarQueSiga(0);
    }

    // --- La deduplicacion de §18.1 ----------------------------------------

    @Test
    @DisplayName("El mismo mensaje publicado dos veces se procesa una sola")
    void elDuplicadoEnElTopicSeAbsorbe() {
        // La entrega es al menos una vez y la catedra puede republicar: dos
        // mensajes con el mismo eventId son una sola notificacion.
        publicar(CatalogUpdatedFixtures.mensaje());
        publicar(CatalogUpdatedFixtures.mensaje());
        arrancar("grupo-" + UUID.randomUUID());

        esperarUnRatoYVerificarQueSiga(1);
        assertThat(idsRegistrados()).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
    }

    @Test
    @DisplayName("Un grupo nuevo relee el topic entero y el estado final no cambia")
    void relecturaCompletaSinEfectos() {
        publicar(CatalogUpdatedFixtures.mensaje("evento-7", 7L));
        publicar(CatalogUpdatedFixtures.mensaje("evento-8", 8L));
        arrancar("grupo-" + UUID.randomUUID());
        esperarQueHaya(2);
        consumidor.destroy();

        // Es el caso de §18.1 llevado al extremo: un consumer group nuevo
        // vuelve a leer todo desde el offset 0 y los dos eventos se
        // reentregan. El resultado esperado es "el estado final permanece
        // igual", y eso es lo que se verifica.
        arrancar("grupo-" + UUID.randomUUID());

        esperarUnRatoYVerificarQueSiga(2);
        assertThat(idsRegistrados()).containsExactly("evento-7", "evento-8");
    }

    // --- El mensaje envenenado --------------------------------------------

    @Test
    @DisplayName("Un mensaje que no se puede deserializar no traba la particion")
    void elMensajeEnvenenadoNoTraba() {
        // Es el criterio explicito del issue. Si el consumidor reintentara
        // este mensaje, el siguiente no llegaria nunca y con el todas las
        // notificaciones posteriores.
        publicar("{esto no es json");
        publicar(CatalogUpdatedFixtures.mensaje());
        arrancar("grupo-" + UUID.randomUUID());

        esperarQueHaya(1);
        assertThat(idsRegistrados()).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
    }

    @Test
    @DisplayName("Un mensaje con otro schemaVersion tampoco traba la particion")
    void elEsquemaDesconocidoNoTraba() {
        publicar(CatalogUpdatedFixtures.mensaje()
                .replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));
        publicar(CatalogUpdatedFixtures.mensaje("evento-posterior", 8L));
        arrancar("grupo-" + UUID.randomUUID());

        esperarQueHaya(1);
        assertThat(idsRegistrados()).containsExactly("evento-posterior");
    }

    // --- El ciclo de vida del consumidor ----------------------------------

    @Test
    @DisplayName("Si esta apagado por configuracion, no se engancha a nada")
    void apagadoPorConfiguracion() {
        consumidor = new CatalogUpdatedConsumer(
                () -> configuracion("grupo-apagado"),
                listener(),
                propiedades(false));

        assertThat(consumidor.arrancarSiHaceFalta()).isFalse();
        assertThat(consumidor.estaEscuchando()).isFalse();
    }

    @Test
    @DisplayName("Arrancar dos veces no duplica el consumidor")
    void arrancarEsIdempotente() {
        String grupo = "grupo-" + UUID.randomUUID();
        publicar(CatalogUpdatedFixtures.mensaje());
        arrancar(grupo);

        // Lo llaman el arranque de la aplicacion y el reintento periodico, y
        // se pueden pisar. Si el segundo armara otro contenedor, habria dos
        // consumidores del mismo grupo y el mensaje se procesaria una vez
        // igual —por la deduplicacion— pero quedarian dos conexiones vivas.
        assertThat(consumidor.arrancarSiHaceFalta()).isTrue();
        assertThat(consumidor.estaEscuchando()).isTrue();

        esperarUnRatoYVerificarQueSiga(1);
    }

    @Test
    @DisplayName("Si cambia el topic en integration, el consumidor se reconstruye")
    void seReconstruyeSiCambiaLaConfiguracion() {
        // La configuracion de integracion se puede releer (§5.3, §5.4). Si
        // el contenedor viejo se reusara, el servicio seguiria escuchando el
        // topic anterior sin que nadie se enterara.
        String otroTopic = "catedra.catalog.otro-" + UUID.randomUUID();
        var configuracionMutable = new java.util.concurrent.atomic.AtomicReference<>(
                configuracion("grupo-" + UUID.randomUUID()));

        consumidor = new CatalogUpdatedConsumer(
                configuracionMutable::get, listener(), propiedades(true));
        consumidor.arrancarSiHaceFalta();

        configuracionMutable.set(IntegrationFixtures.conKafkaEn(
                KAFKA.getBootstrapServers(), otroTopic, "grupo-" + UUID.randomUUID()));
        consumidor.arrancarSiHaceFalta();

        publicarEn(otroTopic, CatalogUpdatedFixtures.mensaje());
        esperarQueHaya(1);
    }

    // --- Auxiliares -------------------------------------------------------

    private void arrancar(String consumerGroupId) {
        consumidor = new CatalogUpdatedConsumer(
                () -> configuracion(consumerGroupId), listener(), propiedades(true));
        consumidor.arrancarSiHaceFalta();
        await().atMost(Duration.ofSeconds(20)).until(consumidor::estaEscuchando);
    }

    private CatalogUpdatedListener listener() {
        return new CatalogUpdatedListener(
                new CatalogUpdatedMapper(new ObjectMapper()),
                handleCatalogUpdated,
                new SyncMetrics(new SimpleMeterRegistry()));
    }

    private IntegrationConfig configuracion(String consumerGroupId) {
        return IntegrationFixtures.conKafkaEn(
                KAFKA.getBootstrapServers(), topic, consumerGroupId);
    }

    private CatedraKafkaProperties propiedades(boolean encendido) {
        return new CatedraKafkaProperties(
                encendido,
                Duration.ofMillis(300),
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                1);
    }

    private void publicar(String valor) {
        publicarEn(topic, valor);
    }

    private void publicarEn(String enQueTopic, String valor) {
        try {
            // get() y no fire-and-forget: la prueba necesita que el mensaje
            // este en el topic antes de arrancar el consumidor.
            productor.send(new ProducerRecord<>(enQueTopic, "7", valor)).get();
        } catch (InterruptedException interrumpida) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrumpida);
        } catch (java.util.concurrent.ExecutionException noSePudo) {
            throw new IllegalStateException("No se pudo publicar en el topic", noSePudo);
        }
    }

    private Properties propiedadesDelProductor() {
        Properties propiedades = new Properties();
        propiedades.putAll(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));
        return propiedades;
    }

    private List<String> idsRegistrados() {
        return jdbc.queryForList(
                "SELECT event_id FROM processed_event ORDER BY processed_at, event_id",
                String.class);
    }

    private long cuantosHay() {
        return jdbc.queryForObject("SELECT count(*) FROM processed_event", Long.class);
    }

    private void limpiarLaTabla() {
        jdbc.execute("TRUNCATE processed_event");
    }

    private void esperarQueHaya(long cuantos) {
        await().atMost(Duration.ofSeconds(30)).until(() -> cuantosHay() == cuantos);
    }

    /**
     * Verifica que el total se mantenga, no solo que se alcance.
     *
     * <p>Hace falta donde lo que se prueba es que algo <b>no</b> pase: un
     * duplicado que se procesa de mas aparece despues del primero, asi que
     * un {@code until} lo dejaria pasar. {@code during} exige que la
     * condicion se cumpla sostenida en el tiempo.
     */
    private void esperarUnRatoYVerificarQueSiga(long cuantos) {
        await().atMost(Duration.ofSeconds(30))
                .during(Duration.ofSeconds(3))
                .until(() -> cuantosHay() == cuantos);
    }
}

package com.um.catalogsync.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.shared.domain.model.Secret;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.application.service.SyncStatusService;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncHealth;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import com.um.catalogsync.sync.infrastructure.client.SnapshotFixtures;
import com.um.catalogsync.sync.infrastructure.messaging.CatalogUpdatedConsumer;
import com.um.catalogsync.sync.infrastructure.messaging.CatalogUpdatedFixtures;
import com.um.catalogsync.sync.infrastructure.messaging.CatalogUpdatedListener;
import com.um.catalogsync.sync.infrastructure.messaging.CatedraKafkaProperties;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisForTests;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Idempotencia, orden y recuperacion con las <b>tres</b> integraciones reales
 * a la vez: el issue #30.
 *
 * <p>Es la suite que §10.1 y §11 piden poder mostrar. PostgreSQL, Redis y
 * Kafka son contenedores de verdad, la catedra REST es WireMock, y entre el
 * mensaje que entra al topic y la fila que queda en la base no hay ningun
 * doble: el consumidor, el listener, la deduplicacion, la decision de §6.2, la
 * lectura de los hashes y las transacciones son los de produccion.
 *
 * <p><b>Que agrega sobre las pruebas que ya existen</b>, que es la pregunta
 * que justifica una suite mas:
 *
 * <ul>
 *   <li>{@code CatalogUpdatedConsumerIntegrationTest} tiene Kafka real, pero
 *       no Redis: el evento se deduplica y ahi termina. Nunca llega a aplicar
 *       una version.
 *   <li>{@code IncrementalSyncIntegrationTest} tiene Redis real, pero entra
 *       por el caso de uso: ninguna notificacion lo dispara.
 *   <li>{@code ReconciliationIntegrationTest} tiene Redis real y el chequeo
 *       periodico, pero la reconstruccion por snapshot usa un doble.
 *   <li>{@code FullSyncEndToEndTest} tiene la catedra REST, pero ni Redis ni
 *       Kafka.
 * </ul>
 *
 * <p>Lo que falta en todas es el <b>pegamento completo</b>: que un mensaje
 * duplicado en el topic no duplique efectos <i>sobre el catalogo</i>, que una
 * notificacion deje la copia local en la ultima de dos versiones pendientes, y
 * que una discontinuidad caiga al snapshot sin intentar el camino incremental.
 *
 * <p><b>Lo unico que se reemplaza es el objeto {@code integration}</b>, que
 * apunta a los contenedores en lugar de a la catedra. Es exactamente de donde
 * el servicio lo toma en produccion (§14.1, §15.2), asi que ni los adaptadores
 * ni el consumidor se enteran de la diferencia. El token tecnico <b>no</b> se
 * reemplaza: sale del login real contra WireMock, por el mismo camino que el
 * snapshot.
 *
 * <p><b>Por que el consumidor se arma a mano.</b> El perfil {@code test} lo
 * deja apagado a proposito, y eso es lo que le da a la prueba el control de
 * cuando se consume y con que consumer group. El grupo importa: hay escenarios
 * que necesitan uno que ya confirmo offsets y otros que necesitan uno que
 * nunca confirmo nada.
 *
 * <p>Para correrla sola:
 *
 * <pre>
 * ./mvnw test -Dtest=SyncRecoveryEndToEndTest
 * </pre>
 */
class SyncRecoveryEndToEndTest extends AbstractPostgresIntegrationTest {

    private static final String RUTA_LOGIN = "/api/authenticate";
    private static final String RUTA_SNAPSHOT = "/api/synchronization/snapshot";

    /** La version en la que arranca la copia local en todos los escenarios. */
    private static final long VERSION_LOCAL = 7L;

    private static final long CATEGORIA_DEL_SNAPSHOT = 1L;
    private static final long CATEGORIA_NUEVA = 2L;
    private static final long PROFESIONAL = 15L;
    private static final long HORARIO_NUEVO = 22L;

    private static final WireMockServer CATEDRA =
            new WireMockServer(wireMockConfig().dynamicPort());

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        CATEDRA.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void laCatedraEsElDoble(DynamicPropertyRegistry registry) {
        registry.add("catedra.api.base-url", CATEDRA::baseUrl);
    }

    @AfterAll
    static void apagarLaCatedra() {
        CATEDRA.stop();
    }

    /**
     * El unico doble: de aca salen los datos de Redis y de Kafka.
     *
     * <p>No reemplaza el token tecnico, que vive en {@code TechnicalSessionCache}
     * y se obtiene del login real contra WireMock.
     */
    @MockitoBean
    private GetIntegrationConfigUseCase integracion;

    @Autowired
    private CatalogUpdatedListener listener;

    @Autowired
    private CatalogSyncService sincronizacion;

    @Autowired
    private SyncStatusService estado;

    @Autowired
    private ApplyCatalogSnapshotUseCase applySnapshot;

    @Autowired
    private CatalogVersionRepository versiones;

    @Autowired
    private SyncRunRepository historial;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private WeeklyScheduleRepository horarios;

    @Autowired
    private JdbcTemplate jdbc;

    /** Un topic y un consumer group por prueba: los contenedores se comparten. */
    private String topic;
    private String grupo;

    private KafkaProducer<String, String> productor;
    private CatalogUpdatedConsumer consumidor;

    @BeforeEach
    void prepararLasTresIntegraciones() {
        topic = "catedra.catalog.prueba-" + UUID.randomUUID();
        grupo = "alumnos-prueba-" + UUID.randomUUID();
        productor = new KafkaProducer<>(propiedadesDelProductor());

        CatedraRedisForTests.limpiar();
        when(integracion.get()).thenReturn(configuracion(grupo));

        CATEDRA.resetAll();
        CATEDRA.stubFor(post(urlEqualTo(RUTA_LOGIN))
                .willReturn(okJson(IntegrationFixtures.loginJson("PROVISIONED"))));

        // La copia local arranca en la version 7, con el catalogo del ejemplo
        // de §7: una categoria, un profesional y un horario.
        applySnapshot.apply(CatalogSnapshotFixtures.version(VERSION_LOCAL));
    }

    @AfterEach
    void apagar() {
        if (consumidor != null) {
            consumidor.destroy();
            consumidor = null;
        }
        productor.close();
    }

    @Nested
    @DisplayName("Idempotencia (§18.1)")
    class Idempotencia {

        @Test
        @DisplayName("El mismo CatalogUpdated dos veces en el topic deja un solo efecto")
        void elDuplicadoEnElTopicNoDuplicaEfectos() {
            laCatedraPublicaLaVersion(8L, 4L);
            hashProfesional(PROFESIONAL, CATEGORIA_DEL_SNAPSHOT, "Ana", "Gonzalez");
            claveDeCambios(8L, List.of(), List.of(PROFESIONAL), List.of());

            // El mismo mensaje, byte por byte, publicado dos veces: es lo que
            // produce la entrega al menos una vez de §15.1.
            publicar(CatalogUpdatedFixtures.mensaje());
            publicar(CatalogUpdatedFixtures.mensaje());
            arrancarElConsumidor();

            esperarQueLaVersionLocalSea(8L);

            // Un solo eventId registrado: el segundo mensaje se reconocio
            // como duplicado antes de hacer nada.
            assertThat(eventosRegistrados()).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
            // Y el efecto quedo aplicado una sola vez. El apellido es el de
            // la version 8 y la copia local no acumulo filas.
            assertThat(profesionales.findById(PROFESIONAL)).get()
                    .satisfies(p -> assertThat(p.getLastName()).isEqualTo("Gonzalez"));
            assertThat(profesionales.findAll()).hasSize(1);
            // Una sola corrida: el duplicado no disparo una segunda.
            assertThat(corridasPosteriores()).hasSize(1);
        }

        @Test
        @DisplayName("Dos eventos distintos de la misma version no la aplican dos veces")
        void dosEventosDeLaMismaVersion() {
            laCatedraPublicaLaVersion(8L, 4L);
            claveDeCambios(8L, List.of(), List.of(), List.of());

            // §15.1 permite dos avisos distintos para la misma version: la
            // idempotencia es por eventId, asi que los dos se procesan. Lo
            // que no se repite es el efecto, porque el segundo encuentra la
            // copia local ya al dia.
            publicar(CatalogUpdatedFixtures.mensaje());
            publicar(CatalogUpdatedFixtures.mensaje("catalog-event-8-otro", 8L));
            arrancarElConsumidor();

            esperarQueHayaEventos(2);
            esperarQueLaVersionLocalSea(8L);

            // Dos eventos registrados, pero una sola corrida: el segundo
            // comparo versiones, no encontro atraso y no registro nada.
            assertThat(eventosRegistrados()).hasSize(2);
            assertThat(corridasPosteriores()).hasSize(1);
        }

        @Test
        @DisplayName("Caida entre persistir y confirmar el offset: la reentrega no duplica nada")
        void laCaidaEntrePersistirYConfirmarNoDuplicaEfectos() {
            laCatedraPublicaLaVersion(8L, 4L);
            hashProfesional(PROFESIONAL, CATEGORIA_DEL_SNAPSHOT, "Ana", "Gonzalez");
            claveDeCambios(8L, List.of(), List.of(PROFESIONAL), List.of());
            publicar(CatalogUpdatedFixtures.mensaje());

            // --- La caida ---
            // Se procesa el mensaje con el listener de produccion pero con un
            // acknowledgment que no confirma: es exactamente la ventana de
            // §16, el instante entre "los efectos ya estan commiteados" y "el
            // offset quedo confirmado". No hace falta tirar ningun contenedor.
            ConsumerRecord<String, String> entregado = leerDelTopicSinConfirmar();
            listener.onMessage(entregado, () -> { });

            // Los efectos SI quedaron: la transaccion del caso de uso
            // commiteo antes de que el proceso se cayera.
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getVersion()).isEqualTo(8L));
            assertThat(eventosRegistrados()).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
            long corridasAntesDeLaReentrega = corridasPosteriores().size();

            // --- La reentrega ---
            // El consumidor arranca con el MISMO grupo, que nunca confirmo
            // nada, asi que Kafka le entrega el mensaje de nuevo. Es la
            // reentrega de verdad, no una llamada repetida.
            arrancarElConsumidor();
            esperarQueElConsumidorConfirme();

            // Nada se duplico: un solo evento, un solo profesional, la misma
            // version y ninguna corrida nueva.
            assertThat(eventosRegistrados()).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
            assertThat(profesionales.findAll()).hasSize(1);
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getVersion()).isEqualTo(8L));
            assertThat(corridasPosteriores()).hasSize((int) corridasAntesDeLaReentrega);
        }

        @Test
        @DisplayName("Un grupo nuevo relee el topic entero y el estado final no cambia")
        void laRelecturaCompletaNoCambiaElEstado() {
            laCatedraPublicaLaVersion(8L, 4L);
            hashProfesional(PROFESIONAL, CATEGORIA_DEL_SNAPSHOT, "Ana", "Gonzalez");
            claveDeCambios(8L, List.of(), List.of(PROFESIONAL), List.of());
            publicar(CatalogUpdatedFixtures.mensaje());

            arrancarElConsumidor();
            esperarQueLaVersionLocalSea(8L);
            consumidor.destroy();

            // Un consumer group nuevo arranca desde el principio del topic
            // (auto.offset.reset=earliest), asi que vuelve a recibir todo lo
            // que ya se proceso. Es el peor caso de reproceso que §18.1
            // contempla.
            grupo = "alumnos-relectura-" + UUID.randomUUID();
            arrancarElConsumidor();
            esperarQueElConsumidorConfirme();

            assertThat(eventosRegistrados()).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
            assertThat(profesionales.findAll()).hasSize(1);
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getVersion()).isEqualTo(8L));
        }
    }

    @Nested
    @DisplayName("El orden de las versiones")
    class OrdenDeLasVersiones {

        @Test
        @DisplayName("Dos versiones pendientes dejan la copia local en la ultima")
        void dosVersionesPendientesDejanLaLocalEnLaUltima() {
            // La catedra publico hasta la 9 y la copia local esta en la 7:
            // faltan dos versiones y una sola notificacion las dispara.
            laCatedraPublicaLaVersion(9L, 4L);

            hashCategoria(CATEGORIA_NUEVA, "Pediatria", true);
            hashProfesional(PROFESIONAL, CATEGORIA_DEL_SNAPSHOT, "Ana", "Gonzalez");
            claveDeCambios(8L, List.of(CATEGORIA_NUEVA), List.of(PROFESIONAL), List.of());

            hashHorario(HORARIO_NUEVO, PROFESIONAL, "TUESDAY");
            claveDeCambios(9L, List.of(), List.of(), List.of(HORARIO_NUEVO));

            publicar(CatalogUpdatedFixtures.mensaje());
            arrancarElConsumidor();

            esperarQueLaVersionLocalSea(9L);

            // Los efectos de las DOS versiones estan: la categoria llego en
            // la 8 y el horario en la 9.
            assertThat(categorias.findById(CATEGORIA_NUEVA)).isPresent();
            assertThat(horarios.findById(HORARIO_NUEVO)).isPresent();
            assertThat(profesionales.findById(PROFESIONAL)).get()
                    .satisfies(p -> assertThat(p.getLastName()).isEqualTo("Gonzalez"));

            // Y quedo una sola corrida incremental que cubre el rango entero,
            // no una por version: el recorrido es una unidad de decision
            // aunque cada version sea su propia transaccion.
            assertThat(corridasPosteriores()).singleElement().satisfies(corrida -> {
                assertThat(corrida.get("mode")).isEqualTo(SyncMode.INCREMENTAL.name());
                assertThat(corrida.get("triggered_by")).isEqualTo(SyncTrigger.EVENT.name());
                assertThat(corrida.get("decision_reason"))
                        .isEqualTo(SyncDecisionReason.BEHIND_CURRENT_VERSION.name());
                assertThat(corrida.get("from_version")).isEqualTo(VERSION_LOCAL);
                assertThat(corrida.get("to_version")).isEqualTo(9L);
                assertThat(corrida.get("status")).isEqualTo(SyncStatus.SUCCEEDED.name());
            });
            // Por el camino incremental, no reconstruyendo.
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getSource())
                            .isEqualTo(CatalogVersionSource.INCREMENTAL));
        }

        @Test
        @DisplayName("El recorrido no se saltea una version: se detiene en la que falta")
        void noSeSalteaLaVersionQueFalta() {
            // La 8 esta y la 9 no. Si el recorrido saltara a la 10, la copia
            // local afirmaria una version cuyos cambios nunca aplico.
            laCatedraPublicaLaVersion(10L, 4L);
            hashCategoria(CATEGORIA_NUEVA, "Pediatria", true);
            claveDeCambios(8L, List.of(CATEGORIA_NUEVA), List.of(), List.of());
            claveDeCambios(10L, List.of(), List.of(), List.of());
            laCatedraSirveElSnapshotEnLaVersion(12L);

            publicar(CatalogUpdatedFixtures.mensaje());
            arrancarElConsumidor();

            // Al detenerse en la 9, cae al snapshot, que viene en la 12.
            esperarQueLaVersionLocalSea(12L);

            // Dos corridas y en este orden: la incremental que llego hasta
            // donde pudo, y la completa que explica por que no alcanzo.
            assertThat(corridasPosteriores()).hasSize(2);
            assertThat(corridasPosteriores().getFirst()).satisfies(incremental -> {
                assertThat(incremental.get("mode")).isEqualTo(SyncMode.INCREMENTAL.name());
                assertThat(incremental.get("to_version")).isEqualTo(8L);
            });
            assertThat(corridasPosteriores().getLast()).satisfies(completa -> {
                assertThat(completa.get("mode")).isEqualTo(SyncMode.FULL.name());
                assertThat(completa.get("decision_reason"))
                        .isEqualTo(SyncDecisionReason.MISSING_VERSION.name());
            });
            // La 10 estaba disponible y NO se aplico por el camino
            // incremental: lo prueba que la version local venga del snapshot.
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getSource())
                            .isEqualTo(CatalogVersionSource.SNAPSHOT));
        }
    }

    @Nested
    @DisplayName("Recuperacion por discontinuidad (§18.2)")
    class RecuperacionPorDiscontinuidad {

        @Test
        @DisplayName("Version fuera de la ventana: pide el snapshot y no intenta el incremental")
        void fueraDeLaVentanaPideElSnapshot() {
            // El caso canonico de §18.2 con los numeros del documento: la
            // copia local esta por debajo de la ventana retenida, asi que el
            // eslabon que la conectaria con los cambios disponibles no existe.
            laCatedraPublicaLaVersion(12L, 10L);
            laCatedraSirveElSnapshotEnLaVersion(12L);
            // A proposito NO se publica ninguna clave changes:*, para que si
            // el recorrido incremental se intentara, se detuviera y la corrida
            // quedara con MISSING_VERSION en lugar de VERSION_OUT_OF_WINDOW.

            publicar(CatalogUpdatedFixtures.mensaje());
            arrancarElConsumidor();

            esperarQueLaVersionLocalSea(12L);

            // Una sola corrida, completa, y el motivo dice que la decision se
            // tomo por la ventana: no se intento el camino incremental.
            assertThat(corridasPosteriores()).singleElement().satisfies(corrida -> {
                assertThat(corrida.get("mode")).isEqualTo(SyncMode.FULL.name());
                assertThat(corrida.get("decision_reason"))
                        .isEqualTo(SyncDecisionReason.VERSION_OUT_OF_WINDOW.name());
                assertThat(corrida.get("from_version")).isEqualTo(VERSION_LOCAL);
                assertThat(corrida.get("to_version")).isEqualTo(12L);
            });
            // Y el snapshot se pidio de verdad, por REST, con el token del
            // login: es la costura entre los tres transportes.
            CATEDRA.verify(getRequestedFor(urlEqualTo(RUTA_SNAPSHOT)));
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getSource())
                            .isEqualTo(CatalogVersionSource.SNAPSHOT));
        }

        @Test
        @DisplayName("Si el estado del origen no se puede verificar, reconstruye (§6.2)")
        void siElOrigenNoSeVerificaReconstruye() {
            // Redis contesta, pero lo que publica no cumple §14.3: la version
            // actual no es un numero. Con eso no hay forma de saber cuan
            // atrasada esta la copia local, y §6.2 manda completa. El snapshot
            // va por REST, que es un camino distinto y puede estar sano aunque
            // Redis publique cualquier cosa.
            //
            // Es distinto de "la catedra no publico nada": eso no es un estado
            // que no se pueda verificar, es que no hay destino, y entonces no
            // se hace nada —lo prueba
            // SynchronizeCatalogUseCaseImplTest#sinMetadataPublicada—.
            CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.METADATA,
                    "currentVersion", "ocho",
                    "oldestAvailableVersion", "4",
                    "publishedAt", "2026-09-10T13:44:59Z");
            laCatedraSirveElSnapshotEnLaVersion(12L);

            sincronizacion.synchronize(SyncTrigger.RECONCILIATION);

            assertThat(corridasPosteriores()).singleElement().satisfies(corrida -> {
                assertThat(corrida.get("mode")).isEqualTo(SyncMode.FULL.name());
                assertThat(corrida.get("decision_reason"))
                        .isEqualTo(SyncDecisionReason.STATE_UNVERIFIABLE.name());
            });
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getVersion()).isEqualTo(12L));
        }
    }

    @Nested
    @DisplayName("Reinicio con una sincronizacion a medias")
    class ReinicioConUnaSincronizacionAMedias {

        @Test
        @DisplayName("La copia local queda consistente y el reinicio cierra el atraso")
        void elReinicioRecuperaElAtraso() {
            // El estado que deja una caida en medio de una sincronizacion: la
            // corrida quedo abierta para siempre porque nadie llego a
            // cerrarla, y la version local es la de antes. No hay estado a
            // medias en los datos —la transaccion lo hace imposible— pero si
            // en el historial.
            SyncRun interrumpida = historial.start(SyncMode.INCREMENTAL, SyncTrigger.EVENT,
                    SyncDecisionReason.BEHIND_CURRENT_VERSION, VERSION_LOCAL);

            // Antes del reinicio, el servicio informa que esta reconstruyendo:
            // es lo ultimo que se sabe de el.
            assertThat(estado.getStatus().lastRun().getId()).isEqualTo(interrumpida.getId());
            assertThat(estado.getStatus().health()).isEqualTo(SyncHealth.REBUILDING);

            // El reinicio: la catedra publico mientras el servicio estaba
            // caido y la comparacion de versiones lo detecta sola, sin
            // depender de que llegue ninguna notificacion (§8, §16).
            laCatedraPublicaLaVersion(8L, 4L);
            hashProfesional(PROFESIONAL, CATEGORIA_DEL_SNAPSHOT, "Ana", "Gonzalez");
            claveDeCambios(8L, List.of(), List.of(PROFESIONAL), List.of());

            sincronizacion.synchronize(SyncTrigger.STARTUP);

            // El atraso quedo cerrado y la copia local es consistente con lo
            // que la catedra publica.
            assertThat(versiones.findCurrent()).get()
                    .satisfies(v -> assertThat(v.getVersion()).isEqualTo(8L));
            assertThat(profesionales.findById(PROFESIONAL)).get()
                    .satisfies(p -> assertThat(p.getLastName()).isEqualTo("Gonzalez"));

            // La corrida interrumpida sigue abierta en el historial: el
            // arranque no la cierra ni la borra, porque no sabe como termino.
            // Es informacion, no basura.
            assertThat(unaCorrida(interrumpida.getId()))
                    .containsEntry("status", SyncStatus.RUNNING.name())
                    .containsEntry("finished_at", null);

            // Y el estado informado vuelve a ser "al dia", porque lo que
            // decide es la corrida mas reciente y esa termino bien.
            assertThat(estado.getStatus().health()).isEqualTo(SyncHealth.UP_TO_DATE);
            assertThat(estado.getStatus().lastRun().getStatus())
                    .isEqualTo(SyncStatus.SUCCEEDED);
        }
    }

    // --- Kafka -------------------------------------------------------------

    private void arrancarElConsumidor() {
        consumidor = new CatalogUpdatedConsumer(
                () -> configuracion(grupo), listener, propiedadesDeKafka());
        consumidor.arrancarSiHaceFalta();
        await().atMost(Duration.ofSeconds(20)).until(consumidor::estaEscuchando);
    }

    private void publicar(String mensaje) {
        try {
            // get() y no fire-and-forget: el mensaje tiene que estar en el
            // topic antes de que el consumidor arranque.
            productor.send(new ProducerRecord<>(topic, "8", mensaje)).get();
        } catch (InterruptedException interrumpida) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrumpida);
        } catch (java.util.concurrent.ExecutionException noSePudo) {
            throw new IllegalStateException("No se pudo publicar en el topic", noSePudo);
        }
    }

    /**
     * Lee el primer mensaje del topic con el consumer group de la prueba y
     * <b>sin confirmar el offset</b>.
     *
     * <p>Es la mitad que simula la caida: el mensaje se entrego de verdad,
     * desde el broker, con sus bytes reales, y el grupo queda sin ningun
     * offset confirmado. Lo que viene despues —arrancar el consumidor de
     * produccion con ese mismo grupo— es una reentrega genuina de Kafka.
     */
    private ConsumerRecord<String, String> leerDelTopicSinConfirmar() {
        try (KafkaConsumer<String, String> crudo =
                new KafkaConsumer<>(propiedadesDelConsumidorCrudo())) {
            crudo.subscribe(List.of(topic));
            ConsumerRecords<String, String> entregados =
                    crudo.poll(Duration.ofSeconds(20));
            assertThat(entregados.count())
                    .as("el topic tenia que entregar el mensaje publicado")
                    .isEqualTo(1);
            return entregados.iterator().next();
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

    private Properties propiedadesDelConsumidorCrudo() {
        Properties propiedades = new Properties();
        propiedades.putAll(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, grupo,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                // Lo que hace que esto simule una caida: nadie confirma nada.
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"));
        return propiedades;
    }

    private CatedraKafkaProperties propiedadesDeKafka() {
        return new CatedraKafkaProperties(
                true, Duration.ofMillis(300), Duration.ofSeconds(1), Duration.ofMillis(100), 1);
    }

    private IntegrationConfig configuracion(String consumerGroupId) {
        return IntegrationFixtures.builderProvisionado()
                .redisHost(CatedraRedisForTests.host())
                .redisPort(CatedraRedisForTests.puerto())
                .redisUsername(CatedraRedisForTests.USUARIO)
                .redisPassword(Secret.of(CatedraRedisForTests.CLAVE))
                .kafkaBootstrapServers(KAFKA.getBootstrapServers())
                .kafkaCatalogTopic(topic)
                .kafkaConsumerGroupId(consumerGroupId)
                .build();
    }

    // --- La catedra: REST y Redis -----------------------------------------

    private void laCatedraSirveElSnapshotEnLaVersion(long version) {
        CATEDRA.stubFor(get(urlEqualTo(RUTA_SNAPSHOT))
                .willReturn(okJson(SnapshotFixtures.snapshotEnLaVersion(version))));
    }

    private void laCatedraPublicaLaVersion(long actual, long masAntigua) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.METADATA,
                "currentVersion", String.valueOf(actual),
                "oldestAvailableVersion", String.valueOf(masAntigua),
                "publishedAt", "2026-09-10T13:44:59Z");
    }

    private void claveDeCambios(long version, List<Long> cats, List<Long> profs, List<Long> hors) {
        CatedraRedisForTests.cli("SET", PublishedCatalogKeys.changes(version), """
                {"version": %d, "changes": {"professionalCategories": %s,
                 "professionals": %s, "weeklySchedules": %s}}
                """.formatted(version, cats, profs, hors));
    }

    private void hashCategoria(long id, String nombre, boolean habilitada) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.PROFESSIONAL_CATEGORIES,
                String.valueOf(id), """
                        {"id": %d, "name": "%s", "description": "Descripcion de %s",
                         "enabled": %b, "createdAt": "2026-07-01T12:00:00Z",
                         "updatedAt": "2026-09-10T13:40:00Z"}
                        """.formatted(id, nombre, nombre, habilitada));
    }

    private void hashProfesional(long id, long categoriaId, String nombre, String apellido) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.PROFESSIONALS,
                String.valueOf(id), """
                        {"id": %d, "categoryId": %d, "firstName": "%s", "lastName": "%s",
                         "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                         "updatedAt": "2026-09-10T13:40:00Z"}
                        """.formatted(id, categoriaId, nombre, apellido));
    }

    private void hashHorario(long id, long profesionalId, String dia) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.WEEKLY_SCHEDULES,
                String.valueOf(id), """
                        {"id": %d, "professionalId": %d, "dayOfWeek": "%s",
                         "startTime": "14:00:00", "endTime": "18:00:00",
                         "slotDurationMinutes": 30, "enabled": true,
                         "createdAt": "2026-07-01T12:00:00Z",
                         "updatedAt": "2026-09-10T13:40:00Z"}
                        """.formatted(id, profesionalId, dia));
    }

    // --- Lo que quedo en la base ------------------------------------------

    private List<String> eventosRegistrados() {
        return jdbc.queryForList(
                "SELECT event_id FROM processed_event ORDER BY processed_at, event_id",
                String.class);
    }

    /**
     * Todas las corridas registradas, en orden de arranque.
     *
     * <p>Son exactamente las que produjo el escenario: la clase base trunca
     * las tablas antes de cada prueba, y el snapshot con el que arranca cada
     * una se aplica por el caso de uso —{@code ApplyCatalogSnapshotUseCase}—,
     * que no registra corrida. Por eso las pruebas pueden afirmar la cantidad
     * y no solo el contenido.
     */
    private List<Map<String, Object>> corridasPosteriores() {
        return jdbc.queryForList("SELECT * FROM sync_run ORDER BY id");
    }

    private Map<String, Object> unaCorrida(long id) {
        return jdbc.queryForMap("SELECT * FROM sync_run WHERE id = ?", id);
    }

    private void esperarQueLaVersionLocalSea(long version) {
        await().atMost(Duration.ofSeconds(30)).until(() -> versiones.findCurrent()
                .filter(actual -> actual.getVersion() == version)
                .isPresent());
    }

    private void esperarQueHayaEventos(int cuantos) {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> eventosRegistrados().size() == cuantos);
    }

    /**
     * Espera a que el consumidor haya resuelto lo que tenia pendiente.
     *
     * <p>No hay una señal directa de "confirmo el offset", asi que se espera
     * a que el consumidor este escuchando y a que el evento este registrado,
     * que es lo que ocurre antes de la confirmacion en el orden de §16. La
     * confirmacion en si la verifica
     * {@code CatalogUpdatedConsumerIntegrationTest#elOffsetConfirmadoSobrevive},
     * que es el lugar donde se puede observar sin adivinar.
     */
    private void esperarQueElConsumidorConfirme() {
        esperarQueHayaEventos(1);
        await().atMost(Duration.ofSeconds(10)).until(consumidor::estaEscuchando);
    }
}

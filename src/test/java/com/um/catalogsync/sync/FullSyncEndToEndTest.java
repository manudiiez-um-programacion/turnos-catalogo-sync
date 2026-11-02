package com.um.catalogsync.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.application.exception.UnknownProfessionalException;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.shared.infrastructure.security.TokensDePrueba;
import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * La sincronizacion completa de punta a punta: el issue #21.
 *
 * <p>Es la unica prueba del repo donde la cadena entera corre junta y con
 * piezas reales: un PostgreSQL de verdad por Testcontainers, la catedra
 * simulada con WireMock, los beans de produccion sin ningun doble en el medio
 * y la busqueda consultada por HTTP sobre el puerto del servidor. El recorrido
 * es el que evalua §11 —login tecnico, snapshot REST, parseo, unidad de
 * trabajo, version aplicada, busqueda— y ninguna de las pruebas que ya
 * existen lo cubre de extremo a extremo:
 *
 * <ul>
 *   <li>{@code RestCatalogSnapshotAdapterTest} prueba el adaptador contra
 *       WireMock, pero no aplica nada en la base.
 *   <li>{@code FullSyncIntegrationTest} prueba la unidad de trabajo contra
 *       PostgreSQL, pero con un {@code CatalogSnapshotPort} de mentira: el
 *       JSON, el login y los timeouts no participan.
 *   <li>{@code ProfessionalSearchApiIntegrationTest} prueba la busqueda por
 *       HTTP, pero con la copia local cargada a mano por los repositorios.
 * </ul>
 *
 * <p>Lo que esta prueba agrega es el pegamento: que lo que la catedra sirve
 * como JSON sea exactamente lo que la busqueda devuelve despues, sin que nadie
 * haya escrito una fila a mano. Un error en cualquiera de las costuras —un
 * campo que el mapper no copia, un token que no se reusa, una version que se
 * asienta antes de tiempo— aparece aca y no en las pruebas de cada pieza.
 *
 * <p><b>Por que WireMock y no el servidor real.</b> Al entorno de la catedra
 * se llega por la red privada ZeroTier y con las credenciales de la cuenta
 * tecnica (D6): en CI no existen ninguna de las dos cosas, y no deben existir.
 * El doble se levanta en un puerto local y la URL base entra por
 * {@code @DynamicPropertySource}, asi que la aplicacion habla con el por el
 * mismo adaptador, el mismo {@code RestClient} y los mismos timeouts con los
 * que hablaria con la catedra. La prueba no necesita ningun secreto.
 *
 * <p><b>Por que la sincronizacion se dispara desde la prueba.</b> El perfil
 * {@code test} apaga {@code sync.startup.enabled}: con la sincronizacion de
 * arranque encendida, cada {@code @SpringBootTest} del repo intentaria traer
 * un snapshot, y sobre todo esta prueba perderia el control de cuando corre
 * —el arranque sincroniza en un hilo aparte, asi que las aserciones serian
 * una carrera contra el. Aca se llama a {@link CatalogSyncService}, que es el
 * mismo camino que usa el runner de #14. Que el arranque decida bien ya lo
 * prueba {@code InitialSyncOnStartupIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullSyncEndToEndTest extends AbstractPostgresIntegrationTest {

    private static final String RUTA_LOGIN = "/api/authenticate";
    private static final String RUTA_SNAPSHOT = "/api/synchronization/snapshot";
    private static final String RUTA_BUSQUEDA = "/api/professionals";

    private static final long VERSION = 7L;
    private static final long CLINICA = 1L;
    private static final long DERMATOLOGIA = 2L;
    private static final long ANA = 15L;
    private static final long LUIS = 16L;
    private static final long BRUNO = 17L;

    private static final ObjectMapper JACKSON = new ObjectMapper();

    /**
     * El doble de la catedra, uno para toda la clase.
     *
     * <p>Se arranca en un bloque estatico y no con {@code @RegisterExtension},
     * como hacen las pruebas de los adaptadores: la URL base se publica con
     * {@code @DynamicPropertySource}, que se evalua al construir el contexto
     * de Spring, y para entonces el puerto ya tiene que estar asignado. Con la
     * extension de JUnit el orden entre su arranque y el del contexto depende
     * del orden de registro de las extensiones, y una prueba que funciona por
     * casualidad de ordenamiento no es una prueba.
     */
    private static final WireMockServer CATEDRA =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CATEDRA.start();
    }

    @DynamicPropertySource
    static void laCatedraEsElDoble(DynamicPropertyRegistry registry) {
        registry.add("catedra.api.base-url", CATEDRA::baseUrl);
    }

    @AfterAll
    static void apagarLaCatedra() {
        CATEDRA.stop();
    }

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    @Autowired
    private CatalogSyncService sincronizacion;

    @Autowired
    private TechnicalSessionCache sesionTecnica;

    @Autowired
    private CatalogVersionRepository versiones;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private WeeklyScheduleRepository horarios;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Deja el doble en blanco y con el login respondiendo.
     *
     * <p>El snapshot lo stubea cada prueba, porque es lo que cambia entre
     * una y otra. El login va aca porque todas lo necesitan: sin el, la
     * sesion tecnica no se puede obtener y el adaptador del snapshot no
     * llega a pedir nada.
     */
    @BeforeEach
    void prepararLaCatedra() {
        CATEDRA.resetAll();
        CATEDRA.stubFor(post(urlEqualTo(RUTA_LOGIN))
                .willReturn(okJson(IntegrationFixtures.loginJson("PROVISIONED"))));
    }

    // --- Base vacia a copia local sincronizada ----------------------------

    @Test
    @DisplayName("Base vacia: trae el snapshot de la catedra y la version queda en snapshotVersion")
    void baseVaciaQuedaSincronizada() {
        laCatedraSirveSuCatalogo(VERSION);

        SyncRun corrida = sincronizacion.runFullSync(SyncTrigger.STARTUP);

        // Las tres colecciones del JSON quedaron en la copia local, con los
        // ids y el estado que mando la catedra. El deshabilitado tambien: es
        // una baja logica que la copia conserva (§14.4).
        assertThat(categorias.findAll())
                .extracting("id", "name")
                .containsExactlyInAnyOrder(
                        tuple(CLINICA, "Clinica medica"), tuple(DERMATOLOGIA, "Dermatologia"));
        assertThat(profesionales.findAll())
                .extracting("id", "lastName", "enabled")
                .containsExactlyInAnyOrder(
                        tuple(ANA, "Gomez", true),
                        tuple(LUIS, "Diaz", false),
                        tuple(BRUNO, "Alvarez", true));
        assertThat(horarios.findAll()).extracting("id").containsExactlyInAnyOrder(21L, 22L, 23L);

        // El criterio central del escenario: la version local es la del
        // snapshot, no una que el servicio se haya inventado, y quedo
        // marcada como venida de un snapshot.
        assertThat(versiones.findCurrent()).get().satisfies(version -> {
            assertThat(version.getVersion()).isEqualTo(VERSION);
            assertThat(version.getSource()).isEqualTo(CatalogVersionSource.SNAPSHOT);
        });
        assertThat(corrida.getStatus()).isEqualTo(SyncStatus.SUCCEEDED);
        assertThat(corrida.getToVersion()).isEqualTo(VERSION);

        assertThat(corridas()).singleElement().satisfies(fila -> {
            assertThat(fila).containsEntry("mode", "FULL");
            assertThat(fila).containsEntry("triggered_by", "STARTUP");
            assertThat(fila).containsEntry("status", "SUCCEEDED");
            // Nulo y no cero: no veniamos de ninguna version previa.
            assertThat(fila).containsEntry("from_version", null);
            assertThat(fila).containsEntry("to_version", VERSION);
        });
        assertThat(errores()).isEmpty();

        // Un solo viaje por el enlace de la catedra, que a ~228 ms de ida y
        // vuelta (D6) no es un detalle: el snapshot se pide una vez por
        // corrida y no una vez por coleccion.
        CATEDRA.verify(1, getRequestedFor(urlEqualTo(RUTA_SNAPSHOT)));
    }

    @Test
    @DisplayName("El login tecnico usa la cuenta configurada y su token firma el snapshot")
    void elLoginTecnicoFirmaElPedidoDelSnapshot() {
        laCatedraSirveSuCatalogo(VERSION);

        // Se fuerza un login fresco a proposito. La sesion tecnica vive en
        // memoria y dura todo el proceso (§4: el token vale un año), asi que
        // si otra prueba de esta clase ya se autentico, una corrida comun no
        // volveria a pedir el token y esta prueba dependeria del orden.
        sesionTecnica.renew();

        CATEDRA.verify(postRequestedFor(urlEqualTo(RUTA_LOGIN))
                // Las credenciales son las del perfil test. Van escritas y no
                // leidas de la configuracion: el valor tiene que ser visible
                // para que se entienda que es lo que se esta verificando.
                .withRequestBody(equalToJson("""
                        {
                          "username": "cuenta-de-prueba",
                          "password": "clave-de-prueba",
                          "rememberMe": true
                        }
                        """)));

        sincronizacion.runFullSync(SyncTrigger.STARTUP);

        // Y el token que devolvio ese login es el que viaja como bearer al
        // snapshot (§4). Es la costura entre los slices integration y sync:
        // el adaptador del snapshot no sabe de donde sale el token, y esta es
        // la prueba de que sale del login y no de una constante olvidada.
        CATEDRA.verify(getRequestedFor(urlEqualTo(RUTA_SNAPSHOT))
                .withHeader(
                        "Authorization", equalTo("Bearer " + IntegrationFixtures.TOKEN_FALSO)));
    }

    // --- El fallo a mitad de la aplicacion --------------------------------

    @Test
    @DisplayName("Si el snapshot se contradice, la version no avanza y la copia queda entera")
    void elFalloAMitadNoAvanzaLaVersion() {
        dadaLaCopiaSincronizada();

        // La version 8 trae un horario de un profesional que ella misma no
        // incluye, asi que estalla en el ultimo paso de la unidad de trabajo,
        // cuando categorias y profesionales ya se reemplazaron. Es el unico
        // fallo a mitad de camino que se puede provocar desde el JSON, que es
        // justamente lo que esta prueba tiene que ejercer: el error entra por
        // la frontera real y no inyectado en el medio.
        laCatedraSirve(catalogoInconsistente(8L));

        assertThatThrownBy(() -> sincronizacion.runFullSync(SyncTrigger.EVENT))
                .isInstanceOf(UnknownProfessionalException.class);

        // La version sigue siendo la 7: avanza solo cuando la unidad de
        // trabajo termino completa, que es la regla no negociable del
        // proyecto y lo que hace que la copia local sea confiable.
        assertThat(versiones.findCurrent()).get()
                .extracting(CatalogVersion::getVersion).isEqualTo(VERSION);

        // Y la copia local es la de la version 7 entera, incluidos los dos
        // profesionales y los dos horarios que el snapshot 8 no traia: el
        // rollback devolvio todo, no solo lo que alcanzo a fallar.
        assertThat(categorias.findAll()).extracting("id")
                .containsExactlyInAnyOrder(CLINICA, DERMATOLOGIA);
        assertThat(profesionales.findAll()).extracting("id")
                .containsExactlyInAnyOrder(ANA, LUIS, BRUNO);
        assertThat(horarios.findAll()).extracting("id")
                .containsExactlyInAnyOrder(21L, 22L, 23L);

        // El historial, en cambio, no vuelve atras: se escribe en una
        // transaccion propia para que el intento fallido quede registrado.
        assertThat(corridas()).extracting("status", "triggered_by", "to_version")
                .containsExactly(
                        tuple("SUCCEEDED", "STARTUP", VERSION),
                        tuple("FAILED", "EVENT", null));
        assertThat(errores()).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("code", "CATALOG_INCONSISTENT"));
    }

    @Test
    @DisplayName("Despues del fallo, la busqueda sigue respondiendo la copia anterior")
    void despuesDelFalloLaBusquedaSigueRespondiendo() {
        dadaLaCopiaSincronizada();
        laCatedraSirve(catalogoInconsistente(8L));

        assertThatThrownBy(() -> sincronizacion.runFullSync(SyncTrigger.EVENT))
                .isInstanceOf(UnknownProfessionalException.class);

        // Esto es lo que ve la app KMP mientras la sincronizacion esta mal: la
        // copia de la version 7, completa y declarando con que version se
        // resolvio. No un 503 ni una lista a medias. Una sincronizacion
        // fallida no deja al servicio sin catalogo, deja al catalogo viejo.
        JsonNode cuerpo = cuerpoDe("");
        assertThat(idsDe(cuerpo)).containsExactlyInAnyOrder(ANA, LUIS, BRUNO);
        assertThat(cuerpo.path("catalogVersion").path("version").asLong()).isEqualTo(VERSION);
    }

    // --- La busqueda sobre lo que trajo el snapshot -----------------------

    @Test
    @DisplayName("Los cuatro filtros obligatorios responden con los datos que trajo el snapshot")
    void losFiltrosObligatoriosDevuelvenLoDelSnapshot() {
        dadaLaCopiaSincronizada();

        // Categoria, nombre, habilitado y disponibilidad: los cuatro filtros
        // minimos que pide el enunciado (§4.1), resueltos sobre filas que
        // nadie escribio a mano —vinieron del JSON de la catedra—.
        assertThat(idsDe(cuerpoDe("?categoryId=" + CLINICA)))
                .containsExactlyInAnyOrder(ANA, LUIS);
        assertThat(idsDe(cuerpoDe("?categoryId=" + DERMATOLOGIA))).containsExactly(BRUNO);
        assertThat(idsDe(cuerpoDe("?name=gomez"))).containsExactly(ANA);
        assertThat(idsDe(cuerpoDe("?enabled=false"))).containsExactly(LUIS);

        // Luis tiene agenda el lunes igual que Ana, y no aparece: esta
        // deshabilitado, y estar disponible incluye estar habilitado.
        assertThat(idsDe(cuerpoDe("?availableOn=MONDAY"))).containsExactly(ANA);
        assertThat(idsDe(cuerpoDe("?availableOn=WEDNESDAY"))).containsExactly(BRUNO);

        // Y combinados, que es como los va a usar la app.
        assertThat(idsDe(cuerpoDe("?categoryId=" + CLINICA + "&name=ana&enabled=true"
                        + "&availableOn=MONDAY&availableFrom=09:00&availableTo=10:00")))
                .containsExactly(ANA);
    }

    @Test
    @DisplayName("La respuesta declara la version de catalogo con la que se resolvio")
    void laRespuestaDeclaraLaVersion() {
        dadaLaCopiaSincronizada();

        JsonNode version = cuerpoDe("").path("catalogVersion");

        // Cierra el recorrido: la version que la app KMP lee es la misma que
        // la catedra puso en snapshotVersion.
        assertThat(version.path("version").asLong()).isEqualTo(VERSION);
        assertThat(version.path("source").asString()).isEqualTo("SNAPSHOT");
    }

    @Test
    @DisplayName("Ninguna busqueda vuelve a hablar con la catedra")
    void ningunaBusquedaHablaConLaCatedra() {
        dadaLaCopiaSincronizada();

        // Se olvida todo lo que la sincronizacion pidio, asi lo que quede
        // registrado despues es unicamente lo que hicieron las busquedas.
        CATEDRA.resetRequests();

        cuerpoDe("");
        cuerpoDe("?name=gomez");
        cuerpoDe("?categoryId=" + CLINICA + "&enabled=true");
        cuerpoDe("?availableOn=MONDAY&availableFrom=09:00&availableTo=13:00");
        cuerpoDe("?name=nadie");

        // La regla no negociable de §6.2, verificada en el unico lugar donde
        // se puede verificar de verdad: contra el doble de la catedra, que
        // registra cualquier pedido que le llegue —tenga stub o no—. Si
        // manana una cache "inteligente" o un fallback saliera a la red
        // cuando la copia local no tiene resultados, esta lista deja de estar
        // vacia. Con el puerto mockeado solo se veria que no se uso ese
        // puerto; aca se ve que no se uso la red.
        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    // --- Auxiliares -------------------------------------------------------

    /** Deja la copia local en la version 7, sincronizada desde el doble. */
    private void dadaLaCopiaSincronizada() {
        laCatedraSirveSuCatalogo(VERSION);
        sincronizacion.runFullSync(SyncTrigger.STARTUP);
    }

    private static void laCatedraSirveSuCatalogo(long version) {
        laCatedraSirve(catalogoDeLaCatedra(version));
    }

    private static void laCatedraSirve(String snapshotJson) {
        CATEDRA.stubFor(get(urlEqualTo(RUTA_SNAPSHOT)).willReturn(okJson(snapshotJson)));
    }

    /**
     * El catalogo que sirve el doble, con la forma literal de §7.
     *
     * <p>Tiene mas entidades que el ejemplo de la referencia porque los
     * filtros necesitan algo que discriminar: dos categorias, un profesional
     * deshabilitado y un horario de ese deshabilitado, que es el caso que
     * separa "tiene agenda" de "esta disponible".
     */
    private static String catalogoDeLaCatedra(long version) {
        return """
                {
                  "snapshotVersion": %d,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "professionalCategories": [
                    {
                      "id": 1, "name": "Clinica medica",
                      "description": "Atencion clinica general", "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"
                    },
                    {
                      "id": 2, "name": "Dermatologia", "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"
                    }
                  ],
                  "professionals": [
                    {
                      "id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gomez",
                      "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"
                    },
                    {
                      "id": 16, "categoryId": 1, "firstName": "Luis", "lastName": "Diaz",
                      "enabled": false,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:41:00Z"
                    },
                    {
                      "id": 17, "categoryId": 2, "firstName": "Bruno", "lastName": "Alvarez",
                      "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:42:00Z"
                    }
                  ],
                  "weeklySchedules": [
                    {
                      "id": 21, "professionalId": 15, "dayOfWeek": "MONDAY",
                      "startTime": "09:00:00", "endTime": "13:00:00",
                      "slotDurationMinutes": 30, "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"
                    },
                    {
                      "id": 22, "professionalId": 17, "dayOfWeek": "WEDNESDAY",
                      "startTime": "14:00:00", "endTime": "18:00:00",
                      "slotDurationMinutes": 45, "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:42:00Z"
                    },
                    {
                      "id": 23, "professionalId": 16, "dayOfWeek": "MONDAY",
                      "startTime": "09:00:00", "endTime": "13:00:00",
                      "slotDurationMinutes": 30, "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:41:00Z"
                    }
                  ]
                }
                """.formatted(version);
    }

    /**
     * Un catalogo que se contradice: el horario es de un profesional que el
     * propio snapshot no trae.
     *
     * <p>Falla en el ultimo de los cinco pasos de la unidad de trabajo, con
     * las categorias y los profesionales ya reemplazados. Es la unica forma
     * de provocar un fallo a mitad de camino sin tocar el codigo de
     * produccion ni inyectar un doble en el medio.
     */
    private static String catalogoInconsistente(long version) {
        return """
                {
                  "snapshotVersion": %d,
                  "generatedAt": "2026-09-11T10:00:00Z",
                  "professionalCategories": [
                    {
                      "id": 1, "name": "Clinica medica", "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-11T09:00:00Z"
                    }
                  ],
                  "professionals": [
                    {
                      "id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gomez",
                      "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-11T09:00:00Z"
                    }
                  ],
                  "weeklySchedules": [
                    {
                      "id": 21, "professionalId": 999, "dayOfWeek": "MONDAY",
                      "startTime": "09:00:00", "endTime": "13:00:00",
                      "slotDurationMinutes": 30, "enabled": true,
                      "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-11T09:00:00Z"
                    }
                  ]
                }
                """.formatted(version);
    }

    private List<Map<String, Object>> corridas() {
        return jdbc.queryForList("SELECT * FROM sync_run ORDER BY id");
    }

    private List<Map<String, Object>> errores() {
        return jdbc.queryForList("SELECT * FROM sync_error ORDER BY id");
    }

    private JsonNode cuerpoDe(String query) {
        HttpResponse<String> respuesta = buscar(query);
        assertThat(respuesta.statusCode())
                .as("GET %s%s", RUTA_BUSQUEDA, query)
                .isEqualTo(200);
        return JACKSON.readTree(respuesta.body());
    }

    /** La busqueda por HTTP real, con el JWT del usuario final (ADR 0002). */
    private HttpResponse<String> buscar(String query) {
        HttpRequest pedido = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + RUTA_BUSQUEDA + query))
                .header("Authorization", "Bearer " + TokensDePrueba.deUsuario())
                .GET()
                .build();
        try {
            return cliente.send(
                    pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException(
                    "No se pudo consultar " + RUTA_BUSQUEDA + query, error);
        }
    }

    private static List<Long> idsDe(JsonNode cuerpo) {
        List<Long> ids = new ArrayList<>();
        cuerpo.path("content").forEach(fila -> ids.add(fila.path("id").asLong()));
        return ids;
    }
}

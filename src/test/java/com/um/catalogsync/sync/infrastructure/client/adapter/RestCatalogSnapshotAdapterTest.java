package com.um.catalogsync.sync.infrastructure.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.infrastructure.client.CatedraClientFixtures;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.shared.domain.model.Secret;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotForbiddenException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnauthorizedException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException;
import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.application.exception.TechnicalIntegrationMissingException;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.infrastructure.client.SnapshotFixtures;
import com.um.catalogsync.sync.infrastructure.client.mapper.CatalogSnapshotMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * El adaptador del snapshot contra un doble de la API de la catedra.
 *
 * <p>WireMock y no el servidor real: las pruebas automatizadas no pueden
 * depender de la red privada ZeroTier ni de las credenciales tecnicas (D6),
 * y en CI no existen. El cliente HTTP se arma con la <b>misma</b> clase de
 * configuracion que usa la aplicacion, asi que lo que se prueba son los
 * timeouts, la URL base y los conversores de verdad.
 *
 * <p>Lo que mas se ejerce es la traduccion de errores. El criterio de §13
 * —decidir por {@code status} y {@code code}, nunca por {@code detail}— se
 * puede romper con un {@code if} sobre el texto y nadie lo notaria, asi que
 * los JSON de error traen un {@code detail} que dice algo distinto del
 * {@code code} y las pruebas verifican que gana el {@code code}.
 */
class RestCatalogSnapshotAdapterTest {

    @RegisterExtension
    static final WireMockExtension CATEDRA =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String RUTA = "/api/synchronization/snapshot";

    private static RestCatalogSnapshotAdapter adaptador() {
        return adaptador(Duration.ofSeconds(2));
    }

    private static RestCatalogSnapshotAdapter adaptador(Duration readTimeout) {
        return new RestCatalogSnapshotAdapter(
                CatedraClientFixtures.restClient(
                        CatedraClientFixtures.propiedades(CATEDRA.baseUrl(), readTimeout)),
                // El puerto de entrada del slice integration, resuelto con un
                // doble: el adaptador no sabe de donde sale el token.
                () -> Secret.of(IntegrationFixtures.TOKEN_FALSO),
                new CatalogSnapshotMapper());
    }

    private static void responde(ResponseDefinitionBuilder respuesta) {
        CATEDRA.stubFor(get(urlEqualTo(RUTA)).willReturn(respuesta));
    }

    private static ResponseDefinitionBuilder problema(int status, String code, String detail) {
        return WireMock.aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody(SnapshotFixtures.problemJson(status, code, detail));
    }

    @Test
    @DisplayName("Mapea las tres colecciones del ejemplo de §7")
    void traeElSnapshotDeLaReferencia() {
        responde(okJson(SnapshotFixtures.snapshotDeLaReferencia()));

        CatalogSnapshot snapshot = adaptador().fetch();

        assertThat(snapshot.getSnapshotVersion()).isEqualTo(SnapshotFixtures.VERSION);
        assertThat(snapshot.getGeneratedAt())
                .isEqualTo(Instant.parse(SnapshotFixtures.GENERATED_AT));
        assertThat(snapshot.getCategories()).hasSize(1);
        assertThat(snapshot.getProfessionals()).hasSize(1);
        assertThat(snapshot.getSchedules()).hasSize(1);
        assertThat(snapshot.getCategories().getFirst().getName()).isEqualTo("Clinica medica");
        assertThat(snapshot.getProfessionals().getFirst().getCategoryId())
                .isEqualTo(SnapshotFixtures.CATEGORIA_ID);
        assertThat(snapshot.getSchedules().getFirst().getDayOfWeek())
                .isEqualTo(DayOfWeek.MONDAY);
        assertThat(snapshot.getSchedules().getFirst().getStartTime())
                .isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("Trae las entidades deshabilitadas e ignora los campos desconocidos")
    void traeLasDeshabilitadas() {
        responde(okJson(SnapshotFixtures.snapshotConEntidadesDeshabilitadas()));

        CatalogSnapshot snapshot = adaptador().fetch();

        // El snapshot devuelve habilitadas y deshabilitadas (§7): una
        // entidad deshabilitada es una baja logica que la copia local tiene
        // que conservar, no una fila que se descarte al leer.
        assertThat(snapshot.getCategories()).hasSize(2);
        assertThat(snapshot.getCategories())
                .extracting("id", "enabled", "description")
                .containsExactly(
                        tuple(1L, true, "Atencion clinica general"),
                        tuple(2L, false, null));
        assertThat(snapshot.getProfessionals())
                .extracting("id", "enabled")
                .containsExactly(tuple(15L, true), tuple(16L, false));
        assertThat(snapshot.getSchedules())
                .extracting("id", "enabled", "dayOfWeek", "startTime")
                .containsExactly(
                        tuple(21L, true, DayOfWeek.MONDAY, LocalTime.of(9, 0)),
                        tuple(22L, false, DayOfWeek.SATURDAY, LocalTime.of(8, 30)));
    }

    @Test
    @DisplayName("Las tres colecciones vacias son un snapshot valido")
    void snapshotVacio() {
        responde(okJson(SnapshotFixtures.snapshotVacio()));

        CatalogSnapshot snapshot = adaptador().fetch();

        assertThat(snapshot.getSnapshotVersion()).isZero();
        assertThat(snapshot.getCategories()).isEmpty();
        assertThat(snapshot.getProfessionals()).isEmpty();
        assertThat(snapshot.getSchedules()).isEmpty();
    }

    @Test
    @DisplayName("Manda el JWT tecnico como bearer, que es lo que pide §4")
    void mandaElBearer() {
        responde(okJson(SnapshotFixtures.snapshotDeLaReferencia()));

        adaptador().fetch();

        CATEDRA.verify(getRequestedFor(urlEqualTo(RUTA))
                .withHeader("Authorization", equalTo("Bearer " + IntegrationFixtures.TOKEN_FALSO)));
    }

    @Test
    @DisplayName("Un 401 es token rechazado: reautenticarse puede resolverlo")
    void tokenRechazado() {
        responde(problema(401, null, "Full authentication is required"));

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(CatalogSnapshotUnauthorizedException.class)
                .hasMessageContaining(RUTA)
                .hasMessageContaining("sin code")
                .hasNoCause();
    }

    @Test
    @DisplayName("Un 403 con STUDENT_INTEGRATION_NOT_FOUND manda a revisar el alta")
    void sinIntegracionTecnica() {
        responde(problema(403, TechnicalIntegrationMissingException.CODE, "Access denied"));

        // El unico caso donde el code decide: un 403 cualquiera es falta de
        // permiso, y este es una cuenta sin integracion asociada (§13.1).
        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(TechnicalIntegrationMissingException.class)
                .hasMessageContaining("§5.1");
    }

    @Test
    @DisplayName("Un 403 con otro code es falta de permiso, no falta de integracion")
    void prohibidoPorOtroMotivo() {
        responde(problema(403, "SOME_FUTURE_CODE", "Access denied"));

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(CatalogSnapshotForbiddenException.class)
                .hasMessageContaining("SOME_FUTURE_CODE");
    }

    @Test
    @DisplayName("Un 403 sin code se tolera: Spring Security no siempre lo manda")
    void prohibidoSinCode() {
        responde(WireMock.forbidden());

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(CatalogSnapshotForbiddenException.class)
                .hasMessageContaining("sin code");
    }

    @Test
    @DisplayName("Decide por code y no por el texto de detail")
    void decidePorCodeYNoPorDetail() {
        // El detail nombra el code de la falta de integracion y el code dice
        // otra cosa. Si la traduccion mirara el texto, elegiria la excepcion
        // equivocada. §13 es explicito: nunca por detail.
        responde(problema(403, "SOME_FUTURE_CODE",
                "integration missing: " + TechnicalIntegrationMissingException.CODE));

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(CatalogSnapshotForbiddenException.class);
    }

    @Test
    @DisplayName("Un 503 es infraestructura: vale reintentar mas tarde")
    void dependenciaCaida() {
        responde(problema(503, null, "Redis unavailable"));

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(CatalogSnapshotUnavailableException.class)
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("Un 500 con un cuerpo que no es problem+json no rompe la traduccion")
    void errorConCuerpoQueNoEsProblemJson() {
        // El servidor de la catedra tiene un catch-all de frontend que
        // responde HTML (verificado en #11). Si el intento de leer el code
        // fallara con su propia excepcion, el error real quedaria tapado por
        // un problema de parseo.
        responde(WireMock.aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "text/html")
                .withBody("<!doctype html><html><body>Internal Server Error</body></html>"));

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(CatalogSnapshotUnavailableException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("sin code");
    }

    @Test
    @DisplayName("Un 200 con cuerpo vacio no se acepta")
    void cuerpoVacio() {
        responde(WireMock.aResponse().withStatus(200));

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("cuerpo vacio");
    }

    @Test
    @DisplayName("Un 200 sin una de las tres colecciones no se acepta")
    void sinUnaColeccion() {
        responde(okJson("""
                {
                  "snapshotVersion": 7,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "professionalCategories": [],
                  "professionals": []
                }
                """));

        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("weeklySchedules");
    }

    @Test
    @DisplayName("Un 200 con HTML es el catch-all de frontend, no un snapshot")
    void doscientosConHtml() {
        responde(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<!doctype html><html><body>app</body></html>"));

        // Es el sintoma de CATEDRA_API_BASE_URL con /api de mas, el error que
        // CatedraApiProperties ya rechaza al construir.
        assertThatThrownBy(() -> adaptador().fetch())
                .isInstanceOf(CatalogSnapshotUnavailableException.class)
                .hasMessageContaining("ZeroTier");
    }

    @Test
    @DisplayName("Si la catedra no responde, el timeout corta y el mensaje habla de ZeroTier")
    void timeoutDeLectura() {
        responde(okJson(SnapshotFixtures.snapshotDeLaReferencia()).withFixedDelay(2000));

        // Sin timeout de lectura la llamada colgaria el hilo para siempre.
        // El valor es configurable y no infinito, y acá se verifica que
        // efectivamente se aplica al snapshot.
        assertThatThrownBy(() -> adaptador(Duration.ofMillis(200)).fetch())
                .isInstanceOf(CatalogSnapshotUnavailableException.class)
                .hasMessageContaining("ZeroTier")
                .hasCauseInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }

    @Test
    @DisplayName("El JWT tecnico no aparece en el mensaje de ninguna excepcion")
    void elTokenNoSeFiltra() {
        responde(problema(401, null, "token " + IntegrationFixtures.TOKEN_FALSO + " rejected"));

        // El 401 es el peor caso: el servidor puede hacer eco del token en el
        // detail. Como la traduccion solo usa status y code, y no encadena la
        // excepcion HTTP, el secreto no llega a la traza.
        Throwable falla = catchThrowable(() -> adaptador().fetch());

        assertThat(trazaDe(falla)).doesNotContain(IntegrationFixtures.TOKEN_FALSO);
    }

    private static String trazaDe(Throwable falla) {
        StringWriter texto = new StringWriter();
        falla.printStackTrace(new PrintWriter(texto));
        return texto.toString();
    }
}

package com.um.catalogsync.sync.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.shared.infrastructure.security.TokensDePrueba;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.PublishedCatalogPort;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El endpoint de estado y errores, de punta a punta.
 *
 * <p>Contra PostgreSQL real y por HTTP real, porque lo que importa acá es
 * justamente lo que un doble se saltearia: la serializacion del JSON con sus
 * nulos, la cadena de seguridad, y que el endpoint conteste 200 en los estados
 * en que las otras dos APIs contestan 503.
 *
 * <p><b>El origen se reemplaza por un doble</b> y no por un Redis real: el
 * adaptador ya tiene sus pruebas contra un contenedor
 * ({@code RedisPublishedCatalogAdapterTest}), y lo que esta clase necesita
 * controlar es que el reporte dice cuando el origen no se puede leer. Con un
 * Redis real habria que apagarlo en medio de la prueba para llegar a ese caso.
 *
 * <p>La prueba de {@code esquemaExacto} verifica el conjunto <b>completo</b> de
 * campos en cada nivel: un campo nuevo en un DTO rompe esta prueba a proposito,
 * porque del otro lado hay un servicio que parsea esto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SyncStatusApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final Instant PUBLICADO = Instant.parse("2026-11-03T18:29:50Z");

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    @MockitoBean
    private PublishedCatalogPort origen;

    @Autowired
    private CatalogVersionRepository versiones;

    @Autowired
    private SyncRunRepository corridas;

    @BeforeEach
    void laCatedraPublicaLaVersion128() {
        when(origen.metadata()).thenReturn(Optional.of(PublishedCatalogMetadata.builder()
                .currentVersion(128L)
                .oldestAvailableVersion(120L)
                .publishedAt(PUBLICADO)
                .build()));
    }

    @Nested
    @DisplayName("El reporte")
    class Reporte {

        @Test
        @DisplayName("devuelve el esquema exacto del contrato")
        void esquemaExacto() {
            dadaUnaCopiaAlDia();
            dadaUnaCorridaFallida();

            JsonNode cuerpo = estado();

            assertThat(cuerpo.propertyNames())
                    .containsExactlyInAnyOrder(
                            "health", "localVersion", "remote", "versionsBehind", "lastSync",
                            "recentErrors");

            assertThat(cuerpo.path("localVersion").propertyNames())
                    .containsExactlyInAnyOrder("version", "appliedAt", "source");

            assertThat(cuerpo.path("remote").propertyNames())
                    .containsExactlyInAnyOrder(
                            "state", "currentVersion", "oldestAvailableVersion", "publishedAt");

            assertThat(cuerpo.path("lastSync").propertyNames())
                    .containsExactlyInAnyOrder(
                            "id", "mode", "trigger", "decisionReason", "status", "fromVersion",
                            "toVersion", "startedAt", "finishedAt");

            assertThat(cuerpo.path("recentErrors").path(0).propertyNames())
                    .containsExactlyInAnyOrder("syncRunId", "occurredAt", "code", "detail");
        }

        @Test
        @DisplayName("al dia: la version local, la remota y el atraso en cero")
        void alDia() {
            dadaUnaCopiaAlDia();

            JsonNode cuerpo = estado();

            assertThat(cuerpo.path("health").asString()).isEqualTo("UP_TO_DATE");
            assertThat(cuerpo.path("localVersion").path("version").asLong()).isEqualTo(128L);
            assertThat(cuerpo.path("localVersion").path("source").asString())
                    .isEqualTo("SNAPSHOT");
            assertThat(cuerpo.path("remote").path("state").asString()).isEqualTo("KNOWN");
            assertThat(cuerpo.path("remote").path("currentVersion").asLong()).isEqualTo(128L);
            assertThat(cuerpo.path("remote").path("oldestAvailableVersion").asLong())
                    .isEqualTo(120L);
            assertThat(cuerpo.path("versionsBehind").asLong()).isZero();
        }

        @Test
        @DisplayName("atrasado: informa cuantas versiones faltan")
        void atrasado() {
            versiones.advanceTo(120L, CatalogVersionSource.SNAPSHOT);

            JsonNode cuerpo = estado();

            // Se recupera solo: es la distincion con DEGRADED que pide el
            // criterio del issue.
            assertThat(cuerpo.path("health").asString()).isEqualTo("BEHIND");
            assertThat(cuerpo.path("versionsBehind").asLong()).isEqualTo(8L);
        }

        @Test
        @DisplayName("la ultima sincronizacion viaja con su tipo, motivo, inicio, fin y resultado")
        void laUltimaSincronizacion() {
            dadaUnaCopiaAlDia();
            SyncRun corrida = corridas.start(SyncMode.INCREMENTAL, SyncTrigger.EVENT,
                    SyncDecisionReason.BEHIND_CURRENT_VERSION, 127L);
            corridas.succeed(corrida.getId(), 128L);

            JsonNode ultima = estado().path("lastSync");

            assertThat(ultima.path("id").asLong()).isEqualTo(corrida.getId());
            assertThat(ultima.path("mode").asString()).isEqualTo("INCREMENTAL");
            assertThat(ultima.path("trigger").asString()).isEqualTo("EVENT");
            assertThat(ultima.path("decisionReason").asString())
                    .isEqualTo("BEHIND_CURRENT_VERSION");
            assertThat(ultima.path("status").asString()).isEqualTo("SUCCEEDED");
            assertThat(ultima.path("fromVersion").asLong()).isEqualTo(127L);
            assertThat(ultima.path("toVersion").asLong()).isEqualTo(128L);
            assertThat(ultima.path("startedAt").asString()).isNotBlank();
            assertThat(ultima.path("finishedAt").asString()).isNotBlank();
        }

        @Test
        @DisplayName("los errores vienen con su codigo estable y su detalle")
        void losErrores() {
            dadaUnaCopiaAlDia();
            dadaUnaCorridaFallida();

            JsonNode error = estado().path("recentErrors").path(0);

            assertThat(error.path("code").asString()).isEqualTo("SNAPSHOT_UNAVAILABLE");
            assertThat(error.path("detail").asString()).isEqualTo("la catedra no respondio");
            assertThat(error.path("syncRunId").isNumber()).isTrue();
        }

        @Test
        @DisplayName("una corrida en curso se informa como REBUILDING")
        void enCurso() {
            versiones.advanceTo(120L, CatalogVersionSource.SNAPSHOT);
            corridas.start(SyncMode.FULL, SyncTrigger.DISCONTINUITY,
                    SyncDecisionReason.VERSION_OUT_OF_WINDOW, 120L);

            JsonNode cuerpo = estado();

            // Mientras dura, la copia local se esta reemplazando entera:
            // decir "atrasado" seria cierto e inutil.
            assertThat(cuerpo.path("health").asString()).isEqualTo("REBUILDING");
            assertThat(cuerpo.path("lastSync").path("status").asString()).isEqualTo("RUNNING");
            assertThat(cuerpo.path("lastSync").path("finishedAt").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Los estados que las otras APIs no pueden informar")
    class EstadosDegradados {

        @Test
        @DisplayName("sin catalogo aplicado responde 200, no 503")
        void sinCatalogoAplicado() {
            // Es la diferencia con la busqueda (#15) y la agenda (#18), que
            // contestan 503 mientras no haya version local. Un 503 acá
            // dejaria al servicio sin forma de explicar por que esta caido.
            HttpResponse<String> respuesta = pedir(TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(200);
            JsonNode cuerpo = JACKSON.readTree(respuesta.body());
            assertThat(cuerpo.path("localVersion").isNull()).isTrue();
            assertThat(cuerpo.path("lastSync").isNull()).isTrue();
            assertThat(cuerpo.path("versionsBehind").isNull()).isTrue();
            assertThat(cuerpo.path("recentErrors")).isEmpty();
            assertThat(cuerpo.path("health").asString()).isEqualTo("BEHIND");
        }

        @Test
        @DisplayName("con la catedra inalcanzable responde 200 y lo dice")
        void conLaCatedraCaida() {
            dadaUnaCopiaAlDia();
            when(origen.metadata()).thenThrow(new PublishedCatalogUnavailableException(
                    "no se pudo conectar con el Redis de la catedra", new RuntimeException()));

            HttpResponse<String> respuesta = pedir(TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(200);
            JsonNode cuerpo = JACKSON.readTree(respuesta.body());
            assertThat(cuerpo.path("health").asString()).isEqualTo("DEGRADED");
            assertThat(cuerpo.path("remote").path("state").asString()).isEqualTo("UNREACHABLE");
            assertThat(cuerpo.path("remote").path("currentVersion").isNull()).isTrue();
            assertThat(cuerpo.path("versionsBehind").isNull()).isTrue();
            // La copia local sigue siendo servible y el reporte lo dice: lo
            // que no se sabe es cuan vieja es.
            assertThat(cuerpo.path("localVersion").path("version").asLong()).isEqualTo(128L);
        }

        @Test
        @DisplayName("el ultimo intento fallido degrada aunque el origen se lea bien")
        void conElUltimoIntentoFallido() {
            dadaUnaCopiaAlDia();
            dadaUnaCorridaFallida();

            JsonNode cuerpo = estado();

            // Que Redis conteste no significa que la sincronizacion funcione.
            assertThat(cuerpo.path("health").asString()).isEqualTo("DEGRADED");
            assertThat(cuerpo.path("remote").path("state").asString()).isEqualTo("KNOWN");
        }
    }

    @Test
    @DisplayName("El reporte no publica ningun secreto")
    void noPublicaSecretos() {
        dadaUnaCopiaAlDia();
        dadaUnaCorridaFallida();

        HttpResponse<String> respuesta = pedir(TokensDePrueba.deServicioDeTurnos());

        // Es el criterio del issue, y se prueba sobre la respuesta entera y
        // no campo por campo: lo que hay que descartar es que alguien
        // agregue al reporte la configuracion de integracion, que es donde
        // viven el JWT tecnico y la clave de Redis. Un campo nuevo con un
        // secreto adentro rompe esta prueba sin que haya que acordarse de
        // actualizarla.
        assertThat(respuesta.body())
                .doesNotContain(TokensDePrueba.SECRETO_INTERNO)
                .doesNotContain(TokensDePrueba.SECRETO_USUARIO)
                .doesNotContain("Bearer ")
                .doesNotContain("redisPassword")
                .doesNotContain("password");
    }

    @Nested
    @DisplayName("Solo el servicio de turnos entra")
    class Autorizacion {

        @Test
        @DisplayName("sin token: 401")
        void sinToken() {
            assertThat(pedir(null).statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("con el token del usuario final: 401, el estado no es informacion suya")
        void conTokenDeUsuarioFinal() {
            // Da 401 y no 403 porque esta cadena solo conoce el secreto
            // interno: falla en la verificacion de firma (ADR 0002).
            assertThat(pedir(TokensDePrueba.deUsuario()).statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("con un token tecnico sin la autoridad SERVICE_TURNOS: 403")
        void sinLaAutoridad() {
            HttpResponse<String> respuesta = pedir(TokensDePrueba.deServicioSinAutoridad());

            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(JACKSON.readTree(respuesta.body()).path("code").asString())
                    .isEqualTo("AUTH_FORBIDDEN");
        }
    }

    private void dadaUnaCopiaAlDia() {
        versiones.advanceTo(128L, CatalogVersionSource.SNAPSHOT);
    }

    private void dadaUnaCorridaFallida() {
        SyncRun fallida = corridas.start(SyncMode.FULL, SyncTrigger.MANUAL,
                SyncDecisionReason.REQUESTED, 128L);
        corridas.fail(fallida.getId(), SyncErrorCode.SNAPSHOT_UNAVAILABLE,
                "la catedra no respondio");
    }

    private JsonNode estado() {
        HttpResponse<String> respuesta = pedir(TokensDePrueba.deServicioDeTurnos());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return JACKSON.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String token) {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/internal/sync/status"))
                .GET();
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        try {
            return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("no se pudo consultar el estado", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("consulta interrumpida", error);
        }
    }
}

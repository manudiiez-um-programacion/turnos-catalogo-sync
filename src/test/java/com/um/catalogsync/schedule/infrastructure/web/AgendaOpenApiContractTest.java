package com.um.catalogsync.schedule.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El documento OpenAPI que publica el servicio, contra lo que el contrato
 * promete.
 *
 * <p>Esta prueba existe porque un documento generado puede quedar correcto y
 * aun asi <b>mentir</b>: alcanza con olvidarse de anotar un campo como
 * requerido, o con que una operacion no declare el esquema de seguridad que
 * realmente exige, para que del otro lado alguien implemente un cliente contra
 * una promesa que el servicio no cumple. Lo que se verifica es justamente lo
 * que un repo distinto va a leer sin poder preguntar.
 *
 * <p>Levanta con {@code springdoc.api-docs.enabled=true} porque el perfil
 * {@code test} lo apaga. Eso ejercita de paso la regla de seguridad: cuando la
 * documentacion esta publicada, se puede leer sin token.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "springdoc.api-docs.enabled=true")
class AgendaOpenApiContractTest extends AbstractPostgresIntegrationTest {

    private static final String RUTA_AGENDA = "/api/internal/professionals/{professionalId}/agenda";
    private static final ObjectMapper JACKSON = new ObjectMapper();

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    private JsonNode documento;

    @BeforeEach
    void traerElDocumento() throws Exception {
        HttpResponse<String> respuesta =
                cliente.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/v3/api-docs"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode())
                .as("con la documentacion encendida, /v3/api-docs se lee sin token")
                .isEqualTo(200);

        documento = JACKSON.readTree(respuesta.body());
    }

    @Test
    @DisplayName("declara la version del contrato")
    void version() {
        // La version del contrato, no la del artefacto: sube cuando cambia lo
        // que el otro lado tiene que saber.
        assertThat(documento.path("info").path("version").asString()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("publica la agenda y solo con GET")
    void soloLectura() {
        JsonNode operaciones = documento.path("paths").path(RUTA_AGENDA);

        assertThat(operaciones.isMissingNode()).isFalse();
        assertThat(operaciones.propertyNames()).containsExactly("get");
    }

    @Test
    @DisplayName("la agenda exige el esquema del JWT tecnico interno")
    void seguridadDeclarada() {
        JsonNode seguridad = documento.path("paths").path(RUTA_AGENDA).path("get").path("security");

        assertThat(seguridad.path(0).propertyNames()).containsExactly("jwt-interno");
    }

    @Test
    @DisplayName("declara las dos identidades como esquemas separados")
    void dosEsquemasDeSeguridad() {
        // Un unico "bearerAuth" haria que el documento no distinga las dos
        // credenciales, que es justo lo que el ADR 0002 separa.
        assertThat(documento.path("components").path("securitySchemes").propertyNames())
                .containsExactlyInAnyOrder("jwt-interno", "jwt-usuario");
    }

    @Test
    @DisplayName("el esquema de la respuesta declara sus campos como requeridos")
    void esquemaDeLaRespuesta() {
        assertThat(requeridos("CurrentAgenda"))
                .containsExactlyInAnyOrder("professional", "schedules", "catalogVersion");

        assertThat(requeridos("AgendaProfessional"))
                .containsExactlyInAnyOrder("id", "categoryId", "firstName", "lastName", "enabled");

        assertThat(requeridos("AgendaWeeklySchedule"))
                .containsExactlyInAnyOrder(
                        "id", "dayOfWeek", "startTime", "endTime", "slotDurationMinutes");

        assertThat(requeridos("AppliedCatalogVersion"))
                .containsExactlyInAnyOrder("version", "appliedAt", "source");
    }

    @Test
    @DisplayName("documenta todos los codigos de respuesta del contrato")
    void respuestasDocumentadas() {
        // Si una condicion de error existe en el codigo y no esta aca, el otro
        // repo se entera cuando la recibe en produccion.
        assertThat(documento.path("paths").path(RUTA_AGENDA).path("get").path("responses").propertyNames())
                .containsExactlyInAnyOrder("200", "401", "403", "404", "409", "503");
    }

    @Test
    @DisplayName("no publica rutas de prueba")
    void sinRutasDePrueba() {
        // Los controladores que usa la prueba de seguridad viven en el mismo
        // paquete raiz y los toma el component scan: si alguno perdiera su
        // @Hidden, apareceria en el contrato que lee el otro repo.
        assertThat(documento.path("paths").propertyNames())
                .allSatisfy(ruta -> assertThat(ruta).doesNotContain("/test"));
    }

    private Iterable<String> requeridos(String esquema) {
        JsonNode lista = documento.path("components").path("schemas").path(esquema).path("required");

        assertThat(lista.isArray()).as("el esquema %s no declara campos requeridos", esquema).isTrue();

        return lista.valueStream().map(JsonNode::asString).toList();
    }
}

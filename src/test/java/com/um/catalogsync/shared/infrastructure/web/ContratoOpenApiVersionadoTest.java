package com.um.catalogsync.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * El {@code openapi.yaml} versionado tiene que ser el que el servicio publica.
 *
 * <p>Un contrato exportado a mano envejece: alguien agrega un campo, se olvida
 * de regenerar el archivo, y el repo de turnos implementa contra un documento
 * que ya no describe al servicio. Como del otro lado no hay forma de notarlo
 * —el archivo se lee, parsea y se ve correcto— el error aparece recien en
 * integracion. Esta prueba lo convierte en un fallo de build.
 *
 * <p><b>Para regenerarlo</b>, cuando el cambio es intencional:
 *
 * <pre>
 * ./mvnw test -Dtest=ContratoOpenApiVersionadoTest -Dcontrato.exportar=true
 * </pre>
 *
 * <p>Y despues revisar el diff: ese diff <b>es</b> el cambio de contrato, y es
 * lo que hay que mirar para decidir si mueve la version del documento.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "springdoc.api-docs.enabled=true")
class ContratoOpenApiVersionadoTest extends AbstractPostgresIntegrationTest {

    private static final Path ARCHIVO = Path.of("docs", "contratos", "openapi.yaml");

    /** Con esto en {@code true}, la prueba escribe el archivo en lugar de compararlo. */
    private static final String EXPORTAR = "contrato.exportar";

    private static final String COMANDO_PARA_REGENERAR =
            "./mvnw test -Dtest=ContratoOpenApiVersionadoTest -Dcontrato.exportar=true";

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    @Test
    @DisplayName("el archivo versionado coincide con lo que el servicio publica")
    void elContratoVersionadoEstaAlDia() throws Exception {
        String publicado = documentoPublicado();

        if (Boolean.getBoolean(EXPORTAR)) {
            Files.createDirectories(ARCHIVO.getParent());
            Files.writeString(ARCHIVO, publicado, StandardCharsets.UTF_8);
            return;
        }

        assertThat(ARCHIVO)
                .as("falta %s. Generarlo con: %s", ARCHIVO, COMANDO_PARA_REGENERAR)
                .exists();

        assertThat(Files.readString(ARCHIVO, StandardCharsets.UTF_8))
                .as(
                        "%s no coincide con lo que publica el servicio. Si el cambio es"
                                + " intencional, regenerarlo con: %s",
                        ARCHIVO, COMANDO_PARA_REGENERAR)
                .isEqualTo(publicado);
    }

    private String documentoPublicado() throws Exception {
        HttpResponse<String> respuesta =
                cliente.send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + puerto + "/v3/api-docs.yaml"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        return respuesta.body();
    }
}

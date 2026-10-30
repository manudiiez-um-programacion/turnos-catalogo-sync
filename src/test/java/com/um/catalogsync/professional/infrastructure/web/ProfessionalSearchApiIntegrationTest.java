package com.um.catalogsync.professional.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.shared.infrastructure.security.TokensDePrueba;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogSnapshotPort;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * La busqueda de profesionales, de punta a punta.
 *
 * <p>Contra PostgreSQL real y por HTTP real, porque es lo que la app KMP va a
 * ver: la serializacion del JSON, el formato de los errores y la cadena de
 * seguridad son justamente lo que un doble se saltearia.
 *
 * <p><b>El doble del snapshot es el criterio de §6.2 convertido en prueba.</b>
 * {@link CatalogSnapshotPort} es la unica via por la que este servicio pide
 * datos a la catedra, y aca se reemplaza por un mock que no hace nada. Si
 * alguna vez la busqueda empezara a consultar al servicio central —por una
 * cache "inteligente", por un fallback cuando la copia esta vacia—, el
 * {@code verifyNoInteractions} lo detecta. Es lo que convierte "las busquedas
 * nunca consultan al servicio central" en algo verificado y no solo
 * documentado. No hay doble de Redis porque todavia no hay adaptador de Redis:
 * cuando lo haya (#25), su puerto se suma aca.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProfessionalSearchApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String RUTA = "/api/professionals";
    private static final long CLINICA = 1L;
    private static final long DERMATOLOGIA = 2L;
    private static final long VERSION = 128L;
    private static final Instant CUANDO = Instant.parse("2026-10-30T12:00:00Z");
    private static final ObjectMapper JACKSON = new ObjectMapper();

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private CatalogVersionRepository versiones;

    /** La unica puerta hacia la catedra, tapiada para toda esta clase. */
    @MockitoBean
    private CatalogSnapshotPort catedra;

    @Nested
    @DisplayName("Con la copia local sincronizada")
    class CopiaSincronizada {

        @Test
        @DisplayName("devuelve el esquema exacto de la respuesta")
        void esquemaExacto() {
            dadoUnCatalogo();

            HttpResponse<String> respuesta = buscar("", TokensDePrueba.deUsuario());
            assertThat(respuesta.statusCode()).isEqualTo(200);

            JsonNode cuerpo = JACKSON.readTree(respuesta.body());
            assertThat(cuerpo.propertyNames())
                    .containsExactlyInAnyOrder("content", "page", "catalogVersion");

            assertThat(cuerpo.path("content").path(0).propertyNames())
                    .containsExactlyInAnyOrder(
                            "id", "categoryId", "firstName", "lastName", "enabled");

            // Los metadatos son cuatro campos propios y no el pageable de
            // Spring Data serializado: ese publicaria estructura interna de
            // una libreria como si fuera contrato.
            assertThat(cuerpo.path("page").propertyNames())
                    .containsExactlyInAnyOrder("number", "size", "totalElements", "totalPages");

            assertThat(cuerpo.path("catalogVersion").propertyNames())
                    .containsExactlyInAnyOrder("version", "appliedAt", "source");
        }

        @Test
        @DisplayName("la respuesta trae la version de catalogo con la que se resolvio")
        void traeLaVersionDeCatalogo() {
            dadoUnCatalogo();

            JsonNode version = cuerpoDe("").path("catalogVersion");

            // Es un criterio de aceptacion: le permite a la app KMP saber
            // sobre que copia esta trabajando sin preguntar nada mas.
            assertThat(version.path("version").asLong()).isEqualTo(VERSION);
            assertThat(version.path("source").asString()).isEqualTo("SNAPSHOT");
            assertThat(version.path("appliedAt").asString()).isNotBlank();
        }

        @Test
        @DisplayName("sin filtros devuelve el catalogo completo, con los deshabilitados")
        void sinFiltrosDevuelveTodo() {
            dadoUnCatalogo();

            JsonNode cuerpo = cuerpoDe("");

            assertThat(idsDe(cuerpo)).containsExactlyInAnyOrder(10L, 11L, 12L, 20L);
            assertThat(cuerpo.path("page").path("totalElements").asLong()).isEqualTo(4L);
        }

        @Test
        @DisplayName("los tres filtros viajan por query string y se combinan")
        void losTresFiltrosSeCombinan() {
            dadoUnCatalogo();

            assertThat(idsDe(cuerpoDe("?categoryId=" + DERMATOLOGIA))).containsExactly(20L);
            assertThat(idsDe(cuerpoDe("?enabled=false")))
                    .containsExactlyInAnyOrder(11L, 12L);
            assertThat(idsDe(cuerpoDe("?name=" + url("GÓMEZ"))))
                    .containsExactlyInAnyOrder(10L, 11L);
            assertThat(idsDe(cuerpoDe("?categoryId=" + CLINICA + "&name=gomez&enabled=true")))
                    .containsExactly(10L);
        }

        @Test
        @DisplayName("el nombre encuentra sin acentos y sin importar mayusculas")
        void nombreInsensibleAAcentos() {
            dadoUnCatalogo();

            // 10 es "Ana Gomez" y 11 es "Ana Gómez": las dos formas del
            // termino tienen que traer las dos filas.
            assertThat(idsDe(cuerpoDe("?name=gomez"))).containsExactlyInAnyOrder(10L, 11L);
            assertThat(idsDe(cuerpoDe("?name=" + url("Gómez"))))
                    .containsExactlyInAnyOrder(10L, 11L);
        }

        @Test
        @DisplayName("una pagina sin resultados es 200 con content vacio, no un 404")
        void paginaVaciaEs200() {
            dadoUnCatalogo();

            HttpResponse<String> respuesta = buscar("?name=nadie", TokensDePrueba.deUsuario());

            assertThat(respuesta.statusCode()).isEqualTo(200);
            JsonNode cuerpo = JACKSON.readTree(respuesta.body());
            assertThat(cuerpo.path("content")).isEmpty();
            assertThat(cuerpo.path("page").path("totalElements").asLong()).isZero();
            // La version sigue viajando: la busqueda se resolvio bien, lo que
            // no hubo son coincidencias.
            assertThat(cuerpo.path("catalogVersion").path("version").asLong()).isEqualTo(VERSION);
        }

        @Test
        @DisplayName("el paginado es estable y los metadatos describen la pagina pedida")
        void paginadoEstable() {
            dadoUnCatalogo();

            JsonNode primera = cuerpoDe("?page=0&size=2");
            JsonNode segunda = cuerpoDe("?page=1&size=2");

            assertThat(primera.path("page").path("number").asInt()).isZero();
            assertThat(primera.path("page").path("size").asInt()).isEqualTo(2);
            assertThat(primera.path("page").path("totalPages").asInt()).isEqualTo(2);
            assertThat(idsDe(primera)).doesNotContainAnyElementsOf(idsDe(segunda));
        }

        @Test
        @DisplayName("sin size el tamaño por defecto es el del dominio")
        void tamanioPorDefecto() {
            dadoUnCatalogo();

            assertThat(cuerpoDe("").path("page").path("size").asInt())
                    .isEqualTo(ProfessionalSearchCriteria.TAMANIO_POR_DEFECTO);
        }

        @Test
        @DisplayName("ninguna busqueda toca a la catedra")
        void nuncaConsultaALaCatedra() {
            dadoUnCatalogo();

            cuerpoDe("");
            cuerpoDe("?name=gomez");
            cuerpoDe("?categoryId=" + CLINICA + "&enabled=true&page=0&size=5");
            cuerpoDe("?name=nadie");

            // Es la regla no negociable de §6.2 y la razon de ser de la copia
            // local. Si la busqueda saliera a la red por cada tecleo del
            // usuario, el servicio central seria el cuello de botella de la app.
            verifyNoInteractions(catedra);
        }
    }

    @Nested
    @DisplayName("Los parametros invalidos")
    class ParametrosInvalidos {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?page=-1",
                    "?size=0",
                    "?size=101",
                    "?size=100000",
                    "?categoryId=abc",
                    "?page=primera",
                    "?enabled=quizas"
                })
        @DisplayName("devuelven 400 VALIDATION_ERROR en problem+json")
        void devuelven400(String query) {
            dadoUnCatalogo();

            HttpResponse<String> respuesta = buscar(query, TokensDePrueba.deUsuario());

            assertThat(respuesta.statusCode()).isEqualTo(400);
            assertThat(problema(respuesta, "code")).isEqualTo("VALIDATION_ERROR");
            assertThat(respuesta.headers().firstValue("content-type"))
                    .hasValueSatisfying(
                            tipo -> assertThat(tipo).startsWith("application/problem+json"));
        }

        @Test
        @DisplayName("el detalle nombra el campo que hay que corregir")
        void elDetalleNombraElCampo() {
            dadoUnCatalogo();

            assertThat(problema(buscar("?size=101", TokensDePrueba.deUsuario()), "detail"))
                    .contains("size")
                    .contains(String.valueOf(ProfessionalSearchCriteria.TAMANIO_MAXIMO));
        }

        @Test
        @DisplayName("el tamaño maximo exacto se acepta: el limite esta donde dice")
        void elMaximoExactoSeAcepta() {
            dadoUnCatalogo();

            HttpResponse<String> respuesta =
                    buscar(
                            "?size=" + ProfessionalSearchCriteria.TAMANIO_MAXIMO,
                            TokensDePrueba.deUsuario());

            assertThat(respuesta.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("un nombre en blanco no es un error: equivale a no filtrar")
        void elNombreEnBlancoNoFiltra() {
            dadoUnCatalogo();

            assertThat(idsDe(cuerpoDe("?name=")))
                    .containsExactlyInAnyOrder(10L, 11L, 12L, 20L);
        }
    }

    @Nested
    @DisplayName("Sin catalogo aplicado")
    class SinCatalogo {

        @Test
        @DisplayName("503 CATALOG_NOT_SYNCHRONIZED, no una lista vacia enganosa")
        void sinVersionAplicada() {
            // Base vacia: ni version ni profesionales. Un content vacio diria
            // "no hay profesionales que cumplan los filtros" cuando lo que no
            // hay es catalogo, y eso si se arregla reintentando.
            HttpResponse<String> respuesta = buscar("", TokensDePrueba.deUsuario());

            assertThat(respuesta.statusCode()).isEqualTo(503);
            assertThat(problema(respuesta, "code")).isEqualTo("CATALOG_NOT_SYNCHRONIZED");
        }
    }

    @Nested
    @DisplayName("La identidad que entra es la del usuario final")
    class Autorizacion {

        @Test
        @DisplayName("sin token: 401")
        void sinToken() {
            assertThat(buscar("", null).statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("con el JWT tecnico interno: 401, esta API no es la interna")
        void conTokenInterno() {
            // Las dos identidades no son intercambiables (ADR 0002): esta
            // cadena solo conoce el secreto del usuario, asi que el token del
            // servicio falla en la verificacion de firma.
            assertThat(buscar("", TokensDePrueba.deServicioDeTurnos()).statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("con un token de usuario vencido: 401")
        void conTokenVencido() {
            assertThat(buscar("", TokensDePrueba.deUsuarioVencido()).statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("la autorizacion se verifica antes de tocar la base")
        void rechazaAntesDeConsultar() {
            // Sin catalogo aplicado, un pedido autenticado daria 503. Que sin
            // token de 401 y no 503 muestra que el filtro corre primero: un
            // anonimo no llega a enterarse del estado interno del servicio.
            assertThat(buscar("", null).statusCode()).isEqualTo(401);
        }
    }

    // --- Auxiliares ------------------------------------------------------

    private void dadoUnCatalogo() {
        versiones.advanceTo(VERSION, CatalogVersionSource.SNAPSHOT);
        categoria(CLINICA, "Clinica medica");
        categoria(DERMATOLOGIA, "Dermatologia");
        profesionales.upsert(profesional(10L, "Ana", "Gomez", CLINICA, true));
        profesionales.upsert(profesional(11L, "Ana", "Gómez", CLINICA, false));
        profesionales.upsert(profesional(12L, "Luis", "Nunez", CLINICA, false));
        profesionales.upsert(profesional(20L, "Bruno", "Alvarez", DERMATOLOGIA, true));
    }

    private void categoria(long id, String nombre) {
        categorias.upsert(
                ProfessionalCategory.builder()
                        .id(id)
                        .name(nombre)
                        .enabled(true)
                        .createdAt(CUANDO)
                        .updatedAt(CUANDO)
                        .build());
    }

    private static Professional profesional(
            Long id, String nombre, String apellido, Long categoria, boolean habilitado) {
        return Professional.builder()
                .id(id)
                .categoryId(categoria)
                .firstName(nombre)
                .lastName(apellido)
                .enabled(habilitado)
                .createdAt(CUANDO)
                .updatedAt(CUANDO)
                .build();
    }

    private HttpResponse<String> buscar(String query, String token) {
        HttpRequest.Builder pedido =
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA + query))
                        .GET();
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        try {
            return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString(
                    StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo consultar " + RUTA + query, error);
        }
    }

    private JsonNode cuerpoDe(String query) {
        HttpResponse<String> respuesta = buscar(query, TokensDePrueba.deUsuario());
        assertThat(respuesta.statusCode()).as("GET %s%s", RUTA, query).isEqualTo(200);
        return JACKSON.readTree(respuesta.body());
    }

    private static List<Long> idsDe(JsonNode cuerpo) {
        List<Long> ids = new ArrayList<>();
        cuerpo.path("content").forEach(fila -> ids.add(fila.path("id").asLong()));
        return ids;
    }

    private static String problema(HttpResponse<String> respuesta, String campo) {
        return JACKSON.readValue(respuesta.body(), Map.class).get(campo).toString();
    }

    private static String url(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}

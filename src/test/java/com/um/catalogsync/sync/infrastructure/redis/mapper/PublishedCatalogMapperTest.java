package com.um.catalogsync.sync.infrastructure.redis.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogFixtures;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/**
 * La traduccion de lo que viene de Redis, sin contenedor.
 *
 * <p>Lo que se prueba aca y no en la prueba del adaptador es la calidad del
 * mensaje de error. Un valor malformado en Redis se diagnostica mirando la
 * clave y el field del otro lado, asi que el mensaje tiene que nombrarlos:
 * un "no se pudo parsear" obliga a adivinar cual de los tres hashes y cual
 * de los cientos de fields era. Eso se verifica barato y en memoria, y deja
 * la prueba con contenedor para lo que de verdad necesita un Redis.
 */
class PublishedCatalogMapperTest {

    private final PublishedCatalogMapper mapper = new PublishedCatalogMapper(new ObjectMapper());

    // --- Las claves de version (§14.2) ------------------------------------

    @Test
    @DisplayName("Un string decimal es la version")
    void versionDecimal() {
        assertThat(mapper.toVersion(PublishedCatalogKeys.CURRENT_VERSION, "7")).isEqualTo(7L);
    }

    @Test
    @DisplayName("Los espacios alrededor no cuentan")
    void versionConEspacios() {
        // Redis guarda el valor tal cual se lo manda, y un publicador que
        // agregue un salto de linea no esta rompiendo el contrato de §14.2:
        // el numero sigue siendo el mismo. Fallar por eso seria elegir la
        // interpretacion mas fragil.
        assertThat(mapper.toVersion(PublishedCatalogKeys.CURRENT_VERSION, " 7\n")).isEqualTo(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"siete", "", " ", "7.0", "7,0", "0x7", "9999999999999999999999"})
    @DisplayName("Lo que no es un decimal nombra la clave y el valor que llego")
    void versionInvalida(String valor) {
        assertThatThrownBy(() -> mapper.toVersion(PublishedCatalogKeys.CURRENT_VERSION, valor))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(PublishedCatalogKeys.CURRENT_VERSION)
                .hasMessageContaining("§14");
    }

    // --- La metadata (§14.3) ----------------------------------------------

    @Test
    @DisplayName("El hash de ejemplo da los tres campos")
    void metadataCompleta() {
        PublishedCatalogMetadata metadata = mapper.toMetadata(metadataDeLaReferencia());

        assertThat(metadata.getCurrentVersion()).isEqualTo(7L);
        assertThat(metadata.getOldestAvailableVersion()).isEqualTo(4L);
        assertThat(metadata.getPublishedAt()).isEqualTo(Instant.parse("2026-09-10T13:44:59Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"currentVersion", "oldestAvailableVersion", "publishedAt"})
    @DisplayName("Si falta cualquiera de los tres campos, el mensaje lo nombra")
    void metadataIncompleta(String campoQueFalta) {
        Map<String, String> hash = metadataDeLaReferencia();
        hash.remove(campoQueFalta);

        // Ninguno de los tres se puede suponer: tomar una version faltante
        // como cero diria que la ventana de versiones empieza en el origen
        // de los tiempos, y eso llevaria a pedir un snapshot sin necesidad o
        // a no pedirlo cuando hace falta.
        assertThatThrownBy(() -> mapper.toMetadata(hash))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(campoQueFalta)
                .hasMessageContaining(PublishedCatalogKeys.METADATA);
    }

    @Test
    @DisplayName("Un campo en blanco cuenta como faltante")
    void metadataConCampoEnBlanco() {
        Map<String, String> hash = metadataDeLaReferencia();
        hash.put("publishedAt", "   ");

        assertThatThrownBy(() -> mapper.toMetadata(hash))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("publishedAt");
    }

    @Test
    @DisplayName("Un publishedAt que no es ISO-8601 se rechaza nombrando §4")
    void metadataConInstanteInvalido() {
        Map<String, String> hash = metadataDeLaReferencia();
        hash.put("publishedAt", "10/09/2026 13:44");

        assertThatThrownBy(() -> mapper.toMetadata(hash))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("§4")
                .hasMessageContaining("10/09/2026");
    }

    // --- Las entidades (§14.4) --------------------------------------------

    @Test
    @DisplayName("Las tres entidades del ejemplo se traducen a dominio")
    void entidadesDeLaReferencia() {
        assertThat(mapper.toCategory(PublishedCatalogKeys.PROFESSIONAL_CATEGORIES, 1L,
                        PublishedCatalogFixtures.categoria()).getName())
                .isEqualTo("Clinica medica");
        assertThat(mapper.toProfessional(PublishedCatalogKeys.PROFESSIONALS, 15L,
                        PublishedCatalogFixtures.profesional()).getLastName())
                .isEqualTo("Gomez");
        assertThat(mapper.toSchedule(PublishedCatalogKeys.WEEKLY_SCHEDULES, 21L,
                        PublishedCatalogFixtures.horario()).getSlotDurationMinutes())
                .isEqualTo(30);
    }

    @Test
    @DisplayName("Un campo obligatorio faltante nombra la clave, el id y el campo")
    void entidadSinUnCampo() {
        String sinApellido = """
                {
                  "id": 15,
                  "categoryId": 1,
                  "firstName": "Ana",
                  "enabled": true,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:40:00Z"
                }
                """;

        assertThatThrownBy(() -> mapper.toProfessional(
                        PublishedCatalogKeys.PROFESSIONALS, 15L, sinApellido))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(PublishedCatalogKeys.PROFESSIONALS + "[15].lastName");
    }

    @Test
    @DisplayName("La descripcion de una categoria si puede faltar")
    void categoriaSinDescripcion() {
        // Es el unico campo opcional del contrato, y el unico que la tabla
        // local no declara NOT NULL.
        String sinDescripcion = """
                {
                  "id": 2,
                  "name": "Dermatologia",
                  "enabled": false,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:41:00Z"
                }
                """;

        assertThatCode(() -> mapper.toCategory(
                        PublishedCatalogKeys.PROFESSIONAL_CATEGORIES, 2L, sinDescripcion))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El literal null es JSON valido y no una entidad")
    void entidadNula() {
        // "null" se parsea sin error y da un DTO nulo. Sin la guarda, el
        // sintoma seria un NullPointerException en el mapper de entidades.
        assertThatThrownBy(() -> mapper.toProfessional(
                        PublishedCatalogKeys.PROFESSIONALS, 15L, "null"))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("nulo");
    }

    @Test
    @DisplayName("Un JSON roto encadena la causa pero no vuelca el valor entero")
    void entidadConJsonRoto() {
        String roto = "{\"id\": 15, \"firstName\": ";

        assertThatThrownBy(() -> mapper.toProfessional(
                        PublishedCatalogKeys.PROFESSIONALS, 15L, roto))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("field 15")
                .hasMessageNotContaining("firstName")
                .hasCauseInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    @DisplayName("Un dayOfWeek desconocido falla con la excepcion del dominio")
    void entidadConDiaInvalido() {
        String conDiaRaro = PublishedCatalogFixtures.horario()
                .replace("\"MONDAY\"", "\"LUNES\"");

        // No se traduce a MalformedPublishedCatalogException: la excepcion
        // del dominio dice que valor llego y es la misma que ya conoce el
        // camino del snapshot. Envolverla perderia ese detalle.
        assertThatThrownBy(() -> mapper.toSchedule(
                        PublishedCatalogKeys.WEEKLY_SCHEDULES, 21L, conDiaRaro))
                .isInstanceOf(
                        com.um.catalogsync.schedule.domain.model.UnknownDayOfWeekException.class)
                .hasMessageContaining("LUNES");
    }

    private static Map<String, String> metadataDeLaReferencia() {
        Map<String, String> hash = new HashMap<>();
        hash.put("currentVersion", "7");
        hash.put("oldestAvailableVersion", "4");
        hash.put("publishedAt", "2026-09-10T13:44:59Z");
        return hash;
    }
}

package com.um.catalogsync.sync.infrastructure.redis.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.domain.model.CatalogChanges;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogFixtures;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/**
 * La traduccion del JSON de cambios, sin contenedor.
 *
 * <p>Lo que se prueba aca y no en la prueba del adaptador es lo que un
 * contenedor no agrega: que forma del JSON se acepta, que forma se rechaza y
 * con que mensaje. Un cambio malformado se diagnostica sabiendo que clave y
 * que array estaban mal, asi que el mensaje tiene que nombrarlos.
 *
 * <p>La version de las claves es la 7, la del ejemplo de §14.5.
 */
class CatalogChangesMapperTest {

    private static final long VERSION = PublishedCatalogFixtures.VERSION_ACTUAL;
    private static final String CLAVE = PublishedCatalogKeys.changes(VERSION);

    private final CatalogChangesMapper mapper = new CatalogChangesMapper(new ObjectMapper());

    // --- Lo que el contrato publica ---------------------------------------

    @Test
    @DisplayName("El ejemplo de §14.5 trae un id de profesional y nada mas")
    void elEjemploDeLaReferencia() {
        CatalogChanges cambios = mapper.toChanges(CLAVE, VERSION, PublishedCatalogFixtures.cambios());

        assertThat(cambios.getVersion()).isEqualTo(VERSION);
        assertThat(cambios.getProfessionalIds())
                .containsExactly(PublishedCatalogFixtures.PROFESIONAL_ID);
        assertThat(cambios.getCategoryIds()).isEmpty();
        assertThat(cambios.getScheduleIds()).isEmpty();
        assertThat(cambios.isEmpty()).isFalse();
        assertThat(cambios.totalIds()).isEqualTo(1);
    }

    @Test
    @DisplayName("Los tres arrays con ids se leen completos y en su coleccion")
    void lasTresColecciones() {
        CatalogChanges cambios =
                mapper.toChanges(CLAVE, VERSION, PublishedCatalogFixtures.cambiosDeLasTres(VERSION));

        // Que cada array caiga en su campo no es trivial: los tres son
        // List<Long> y un cruce entre ellos compilaria igual y aplicaria
        // horarios con ids de categoria.
        assertThat(cambios.getCategoryIds()).containsExactly(1L, 2L);
        assertThat(cambios.getProfessionalIds()).containsExactly(15L);
        assertThat(cambios.getScheduleIds()).containsExactly(21L, 22L, 23L);
        assertThat(cambios.totalIds()).isEqualTo(6);
    }

    @Test
    @DisplayName("Los campos que el contrato todavia no tiene se ignoran")
    void ignoraLosCamposDesconocidos() {
        // El fixture trae un "generatedAt" que §14.5 no define. §4 pide
        // tolerar campos nuevos compatibles: fallar por esto dejaria la
        // sincronizacion incremental a merced de cualquier agregado.
        assertThat(mapper.toChanges(CLAVE, VERSION,
                        PublishedCatalogFixtures.cambiosDeLasTres(VERSION)))
                .isNotNull();
    }

    @Test
    @DisplayName("Una version sin cambios es valida, no un error")
    void unaVersionSinCambios() {
        CatalogChanges cambios =
                mapper.toChanges(CLAVE, VERSION, PublishedCatalogFixtures.cambiosVacios(VERSION));

        // Es el nucleo del issue: esto se puede aplicar y hace avanzar la
        // version local. Si fuera un error, una version de la catedra que no
        // toco nada bloquearia el camino incremental para siempre.
        assertThat(cambios.isEmpty()).isTrue();
        assertThat(cambios.totalIds()).isZero();
        assertThat(cambios.getVersion()).isEqualTo(VERSION);
    }

    @Test
    @DisplayName("Los ids repetidos se conservan: aplicar dos veces es idempotente")
    void losIdsRepetidosSeConservan() {
        // No se deduplica a proposito. Lo que #25 aplica es el estado
        // vigente de cada id, no un delta, asi que leerlo dos veces deja el
        // mismo resultado. Deduplicar seria esconder que la catedra publico
        // algo raro.
        String json = """
                {"version": 7, "changes": {"professionalCategories": [],
                 "professionals": [15, 15], "weeklySchedules": []}}
                """;

        assertThat(mapper.toChanges(CLAVE, VERSION, json).getProfessionalIds())
                .containsExactly(15L, 15L);
    }

    @Test
    @DisplayName("Las listas del modelo no se pueden modificar desde afuera")
    void lasListasSonInmutables() {
        CatalogChanges cambios = mapper.toChanges(CLAVE, VERSION, PublishedCatalogFixtures.cambios());

        // #25 va a recorrer estas listas mientras aplica una version: que
        // nadie pueda agregarles un id a mitad de camino es parte de que la
        // unidad de trabajo sea la version que se leyo.
        assertThatThrownBy(() -> cambios.getProfessionalIds().add(99L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Lo que no cumple §14.5 -------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"{esto no es json", "", "   ", "[]", "null"})
    @DisplayName("Un contenido que no es el objeto de §14.5 nombra la clave")
    void contenidoInvalido(String json) {
        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, json))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(CLAVE);
    }

    @Test
    @DisplayName("Si la version del JSON no es la de la clave, se rechaza")
    void laVersionTieneQueCoincidirConLaClave() {
        // La clave dice 7 y el JSON dice 8. Sin este control, #25 aplicaria
        // los cambios de la 8 y asentaria la 7: la proxima lectura
        // incremental arrancaria desde el numero equivocado y el hueco no
        // dejaria ningun rastro, porque la version local se veria
        // consistente.
        String json = PublishedCatalogFixtures.cambiosVacios(8L);

        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, json))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(CLAVE)
                .hasMessageContaining("8");
    }

    @Test
    @DisplayName("Sin el campo version, se rechaza")
    void sinVersion() {
        String json = """
                {"changes": {"professionalCategories": [], "professionals": [],
                 "weeklySchedules": []}}
                """;

        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, json))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("version");
    }

    @Test
    @DisplayName("Sin el objeto changes, se rechaza")
    void sinObjetoChanges() {
        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, "{\"version\": 7}"))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("changes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"professionalCategories", "professionals", "weeklySchedules"})
    @DisplayName("Un array ausente es un error, no una coleccion sin cambios")
    void unArrayAusente(String campoQueFalta) {
        // Es la distincion que evita la peor falla de este camino: si un
        // array ausente se tomara como vacio, un cambio de nombre en el
        // contrato descartaria cambios sin ningun sintoma y la version local
        // avanzaria sobre una copia incompleta. §14.5 trae los tres arrays
        // siempre, vacios cuando no hubo cambios.
        String json = PublishedCatalogFixtures.cambios()
                .replace("\"" + campoQueFalta + "\"", "\"otroNombre\"");

        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, json))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(campoQueFalta)
                .hasMessageContaining("§14.5");
    }

    @Test
    @DisplayName("Un id nulo dentro de un array se rechaza en la lectura")
    void unIdNulo() {
        // Sin esto, el null llegaria hasta #25 y reventaria al pedirle al
        // hash un field que no existe, lejos de la causa.
        String json = """
                {"version": 7, "changes": {"professionalCategories": [],
                 "professionals": [15, null], "weeklySchedules": []}}
                """;

        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, json))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("professionals");
    }

    @Test
    @DisplayName("Un id que no es un numero se rechaza")
    void unIdQueNoEsNumero() {
        String json = """
                {"version": 7, "changes": {"professionalCategories": [],
                 "professionals": ["quince"], "weeklySchedules": []}}
                """;

        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, json))
                .isInstanceOf(MalformedPublishedCatalogException.class);
    }

    @Test
    @DisplayName("El mensaje de error no incluye el contenido de la clave")
    void elMensajeNoVuelcaElContenido() {
        // Una version con muchos cambios es una lista larga de ids, y una
        // traza no es lugar para volcarla: alcanza con saber que clave
        // mirar.
        String json = PublishedCatalogFixtures.cambiosDeLasTres(8L);

        assertThatThrownBy(() -> mapper.toChanges(CLAVE, VERSION, json))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .extracting(Throwable::getMessage)
                .asString()
                .doesNotContain("weeklySchedules");
    }
}

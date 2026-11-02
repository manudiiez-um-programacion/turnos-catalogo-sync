package com.um.catalogsync.professional.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La busqueda de profesionales contra PostgreSQL real.
 *
 * <p>Es la prueba que importa de #15, porque los criterios que mas valen son
 * propiedades de la consulta y no del codigo Java que la arma. Que buscar
 * "gomez" encuentre a "Gómez" depende de que la normalizacion al guardar y la
 * del termino sean la misma funcion; que el paginado sea estable depende de
 * que el {@code ORDER BY} sea total; que un {@code _} no actue como comodin
 * depende del escape del {@code LIKE}. Con un doble del repositorio, los tres
 * pasarian sin probar nada.
 *
 * <p>Va en una clase aparte de {@code JpaProfessionalRepositoryAdapterTest}
 * a proposito: esa cubre las escrituras y la traduccion de las FK, que es otro
 * tema. Mezclarlas daria una clase con dos juegos de datos de prueba
 * incompatibles —la de escritura necesita la base casi vacia y esta necesita
 * un catalogo poblado—.
 */
class ProfessionalSearchQueryTest extends AbstractPostgresIntegrationTest {

    private static final long CLINICA = 1L;
    private static final long DERMATOLOGIA = 2L;

    private static final Instant CUANDO = Instant.parse("2026-09-10T13:40:00Z");
    private static final OffsetDateTime CUANDO_TZ = CUANDO.atOffset(ZoneOffset.UTC);

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * El catalogo de prueba.
     *
     * <p>Los datos estan elegidos, no inventados: dos homonimos exactos con
     * ids distintos para el desempate del orden, el mismo apellido con y sin
     * acento en dos categorias distintas para el filtro insensible, uno
     * deshabilitado para el filtro de estado, y dos apellidos que se
     * diferencian solo en un caracter que el {@code LIKE} trataria como
     * comodin.
     */
    @BeforeEach
    void dadoUnCatalogo() {
        categoria(CLINICA, "Clinica medica");
        categoria(DERMATOLOGIA, "Dermatologia");

        profesionales.upsert(profesional(10L, "Ana", "Gomez", CLINICA, true));
        profesionales.upsert(profesional(11L, "Ana", "Gomez", CLINICA, true));
        profesionales.upsert(profesional(12L, "Luis", "Nunez", CLINICA, false));
        profesionales.upsert(profesional(13L, "Carla", "Diaz", CLINICA, true));
        profesionales.upsert(profesional(20L, "Bruno", "Alvarez", DERMATOLOGIA, true));
        profesionales.upsert(profesional(21L, "Ana", "Gómez", DERMATOLOGIA, true));
        profesionales.upsert(profesional(30L, "Mia", "Zz_zz", CLINICA, true));
        profesionales.upsert(profesional(31L, "Mia", "Zzazz", CLINICA, true));
    }

    // --- Los filtros, de a uno ---------------------------------------------

    @Test
    @DisplayName("sin filtros devuelve el catalogo completo paginado")
    void sinFiltrosDevuelveTodo() {
        ProfessionalPage pagina = buscar(criterio(null, null, null, 0, 100));

        assertThat(pagina.totalElements()).isEqualTo(8L);
        assertThat(ids(pagina)).containsExactlyInAnyOrder(10L, 11L, 12L, 13L, 20L, 21L, 30L, 31L);
    }

    @Test
    @DisplayName("filtra por categoria exacta")
    void filtraPorCategoria() {
        assertThat(ids(buscar(criterio(DERMATOLOGIA, null, null, 0, 100))))
                .containsExactlyInAnyOrder(20L, 21L);
    }

    @Test
    @DisplayName("filtra por estado, y el false es un filtro y no una ausencia de filtro")
    void filtraPorEstado() {
        assertThat(ids(buscar(criterio(null, null, true, 0, 100)))).doesNotContain(12L);
        assertThat(ids(buscar(criterio(null, null, false, 0, 100)))).containsExactly(12L);
    }

    @Nested
    @DisplayName("El filtro por nombre")
    class PorNombre {

        @Test
        @DisplayName("es parcial: un fragmento del apellido alcanza")
        void esParcial() {
            assertThat(ids(buscar(criterio(null, "ome", null, 0, 100))))
                    .containsExactlyInAnyOrder(10L, 11L, 21L);
        }

        @Test
        @DisplayName("es insensible a mayusculas y a acentos")
        void esInsensibleAMayusculasYAcentos() {
            // "Gómez" tiene que aparecer buscando "GOMEZ" sin tilde. Es el
            // criterio central: la columna search_name guarda el nombre
            // normalizado y el termino pasa por la misma funcion.
            assertThat(ids(buscar(criterio(null, "GOMEZ", null, 0, 100))))
                    .containsExactlyInAnyOrder(10L, 11L, 21L);
            assertThat(ids(buscar(criterio(null, "gómez", null, 0, 100))))
                    .containsExactlyInAnyOrder(10L, 11L, 21L);
        }

        @Test
        @DisplayName("busca sobre nombre y apellido juntos")
        void buscaSobreNombreYApellido() {
            // Ni el nombre solo ni el apellido solo: el termino completo
            // atraviesa los dos campos, que es lo que un buscador hace.
            assertThat(ids(buscar(criterio(null, "ana gomez", null, 0, 100))))
                    .containsExactlyInAnyOrder(10L, 11L, 21L);
            assertThat(ids(buscar(criterio(null, "mia", null, 0, 100))))
                    .containsExactlyInAnyOrder(30L, 31L);
        }

        @Test
        @DisplayName("el guion bajo del termino es un caracter, no un comodin")
        void escapaElGuionBajo() {
            // Sin escapar, '_' matchea cualquier caracter y "zz_zz" traeria
            // tambien a "Zzazz". No es una inyeccion: es un resultado
            // incorrecto y silencioso, que es peor de encontrar.
            assertThat(ids(buscar(criterio(null, "zz_zz", null, 0, 100))))
                    .containsExactly(30L);
        }

        @Test
        @DisplayName("el porcentaje del termino tampoco es un comodin")
        void escapaElPorcentaje() {
            // "%zz" sin escapar matchearia los dos; escapado no matchea
            // ninguno, porque ningun apellido tiene un % literal.
            assertThat(buscar(criterio(null, "%zz", null, 0, 100)).content()).isEmpty();
        }

        @Test
        @DisplayName("un termino que no matchea nada devuelve una pagina vacia, no un error")
        void sinCoincidenciasDevuelvePaginaVacia() {
            ProfessionalPage pagina = buscar(criterio(null, "nadie", null, 0, 100));

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.totalElements()).isZero();
            assertThat(pagina.totalPages()).isZero();
        }
    }

    // --- Los filtros combinados -------------------------------------------

    @Test
    @DisplayName("los tres filtros se combinan entre si")
    void losTresFiltrosSeCombinan() {
        assertThat(ids(buscar(criterio(CLINICA, "gomez", true, 0, 100))))
                .containsExactlyInAnyOrder(10L, 11L);

        // Misma busqueda en la otra categoria: el filtro de categoria no es
        // decorativo cuando hay nombre.
        assertThat(ids(buscar(criterio(DERMATOLOGIA, "gomez", true, 0, 100))))
                .containsExactly(21L);

        // Y combinado con un estado que ninguno de los dos tiene.
        assertThat(buscar(criterio(CLINICA, "gomez", false, 0, 100)).content()).isEmpty();
    }

    @Test
    @DisplayName("una combinacion imposible devuelve vacio sin fallar")
    void laCombinacionImposibleDevuelveVacio() {
        assertThat(buscar(criterio(DERMATOLOGIA, "nunez", null, 0, 100)).content()).isEmpty();
    }

    // --- El orden y el paginado ------------------------------------------

    @Nested
    @DisplayName("El orden y el paginado")
    class OrdenYPaginado {

        @Test
        @DisplayName("ordena por apellido y nombre, ascendente")
        void ordenaPorApellidoYNombre() {
            List<Long> todos = ids(buscar(criterio(null, null, null, 0, 100)));

            // Subsecuencia y no orden exacto: entre Gomez y Nunez esta
            // tambien "Gómez", y donde cae exactamente un acento depende del
            // collation de la base, que no es lo que esta prueba fija.
            assertThat(todos).containsSubsequence(20L, 13L, 10L, 11L, 12L, 30L);
        }

        @Test
        @DisplayName("el id desempata a los homonimos, que es lo que hace el orden total")
        void elIdDesempata() {
            // 10 y 11 son "Ana Gomez" las dos. Sin desempate, PostgreSQL no
            // garantiza que salgan siempre en el mismo orden, y el que cae en
            // el borde de una pagina se repite o se pierde en la siguiente.
            assertThat(ids(buscar(criterio(CLINICA, "ana gomez", null, 0, 100))))
                    .containsExactly(10L, 11L);
        }

        @Test
        @DisplayName("pagina sin repetir ni perder filas, y el total es el de todas")
        void paginaSinRepetirNiPerder() {
            ProfessionalPage primera = buscar(criterio(null, null, null, 0, 3));
            ProfessionalPage segunda = buscar(criterio(null, null, null, 1, 3));
            ProfessionalPage tercera = buscar(criterio(null, null, null, 2, 3));

            assertThat(primera.content()).hasSize(3);
            assertThat(segunda.content()).hasSize(3);
            assertThat(tercera.content()).hasSize(2);

            // El total es el de la busqueda completa en las tres paginas: es
            // lo que le permite al cliente saber cuanto le falta.
            assertThat(primera.totalElements()).isEqualTo(8L);
            assertThat(primera.totalPages()).isEqualTo(3);

            assertThat(ids(primera)).doesNotContainAnyElementsOf(ids(segunda));
            assertThat(ids(primera)).doesNotContainAnyElementsOf(ids(tercera));
            assertThat(ids(segunda)).doesNotContainAnyElementsOf(ids(tercera));
        }

        @Test
        @DisplayName("una pagina mas alla del final esta vacia pero informa el total")
        void masAllaDelFinal() {
            ProfessionalPage pagina = buscar(criterio(null, null, null, 99, 3));

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.totalElements()).isEqualTo(8L);
            assertThat(pagina.page()).isEqualTo(99);
        }

        @Test
        @DisplayName("la pagina devuelta informa la pagina y el tamaño que se pidieron")
        void informaLaPaginaPedida() {
            ProfessionalPage pagina = buscar(criterio(null, null, null, 1, 5));

            assertThat(pagina.page()).isEqualTo(1);
            assertThat(pagina.size()).isEqualTo(5);
        }
    }

    // --- Auxiliares ------------------------------------------------------

    private ProfessionalPage buscar(ProfessionalSearchCriteria criteria) {
        return profesionales.search(criteria);
    }

    private static ProfessionalSearchCriteria criterio(
            Long categoria, String nombre, Boolean habilitado, int pagina, int tamanio) {
        return new ProfessionalSearchCriteria(
                categoria, nombre, habilitado, null, pagina, tamanio);
    }

    private static List<Long> ids(ProfessionalPage pagina) {
        return pagina.content().stream().map(Professional::getId).toList();
    }

    private void categoria(long id, String nombre) {
        jdbc.update(
                "INSERT INTO professional_category (id, name, enabled, created_at, updated_at)"
                        + " VALUES (?, ?, true, ?, ?)",
                id,
                nombre,
                CUANDO_TZ,
                CUANDO_TZ);
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
}

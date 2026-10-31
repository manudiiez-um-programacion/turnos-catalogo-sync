package com.um.catalogsync.professional.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.schedule.domain.model.AvailabilityCriteria;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El criterio de busqueda.
 *
 * <p>Lo que importa fijar aca es que la normalizacion del nombre ocurre al
 * construir el criterio y no mas adelante. Es la garantia de que no existe un
 * criterio con el termino sin normalizar: si eso fuera posible, un camino
 * nuevo hacia la busqueda —otro controlador, una prueba— podria saltearse la
 * normalizacion y las consultas por "Gómez" dejarian de encontrar nada sin que
 * ninguna prueba del adaptador lo notara.
 */
class ProfessionalSearchCriteriaTest {

    private static final AvailabilityCriteria LOS_LUNES =
            new AvailabilityCriteria(DayOfWeek.MONDAY, null, null);

    private static ProfessionalSearchCriteria conNombre(String name) {
        return new ProfessionalSearchCriteria(null, name, null, null, 0, 20);
    }

    @Test
    @DisplayName("normaliza el nombre con la misma regla con la que se guardo")
    void normalizaElNombre() {
        assertThat(conNombre("Ana GÓMEZ").name()).isEqualTo("ana gomez");
        assertThat(conNombre("  Núñez  ").name()).isEqualTo("nunez");
        assertThat(conNombre("Ana    Gomez").name()).isEqualTo("ana gomez");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("un nombre en blanco equivale a no filtrar, no a un LIKE vacio")
    void elBlancoNoEsFiltro(String enBlanco) {
        ProfessionalSearchCriteria criteria = conNombre(enBlanco);

        // Dejarlo pasar daria LIKE '%%': recorre el indice entero para no
        // filtrar nada. Decir que no hay filtro es mas honesto y mas rapido.
        assertThat(criteria.name()).isNull();
        assertThat(criteria.filtraPorNombre()).isFalse();
    }

    @Test
    @DisplayName("un nombre nulo deja el filtro sin aplicar")
    void elNuloNoEsFiltro() {
        assertThat(conNombre(null).name()).isNull();
        assertThat(conNombre(null).filtraPorNombre()).isFalse();
    }

    @Test
    @DisplayName("los cuatro filtros son independientes y se pueden combinar")
    void losCuatroFiltrosSonIndependientes() {
        ProfessionalSearchCriteria todos = new ProfessionalSearchCriteria(
                7L, "gomez", true, LOS_LUNES, 0, 20);

        assertThat(todos.filtraPorCategoria()).isTrue();
        assertThat(todos.filtraPorNombre()).isTrue();
        assertThat(todos.filtraPorEstado()).isTrue();
        assertThat(todos.filtraPorDisponibilidad()).isTrue();

        ProfessionalSearchCriteria ninguno = ProfessionalSearchCriteria.primeraPagina();

        assertThat(ninguno.filtraPorCategoria()).isFalse();
        assertThat(ninguno.filtraPorNombre()).isFalse();
        assertThat(ninguno.filtraPorEstado()).isFalse();
        assertThat(ninguno.filtraPorDisponibilidad()).isFalse();
    }

    @Test
    @DisplayName("enabled en false es un filtro, no una ausencia de filtro")
    void elFalseEsUnFiltro() {
        // Es la razon de que el campo sea Boolean y no boolean: "quiero los
        // deshabilitados" y "no me importa el estado" son pedidos distintos.
        ProfessionalSearchCriteria soloDeshabilitados =
                new ProfessionalSearchCriteria(null, null, false, null, 0, 20);

        assertThat(soloDeshabilitados.filtraPorEstado()).isTrue();
        assertThat(soloDeshabilitados.enabled()).isFalse();
    }

    @Test
    @DisplayName("una pagina negativa no se puede expresar")
    void rechazaLaPaginaNegativa() {
        assertThatThrownBy(() -> new ProfessionalSearchCriteria(null, null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, ProfessionalSearchCriteria.TAMANIO_MAXIMO + 1, 100_000})
    @DisplayName("el tamaño de pagina queda acotado entre 1 y el maximo")
    void rechazaTamaniosFueraDeRango(int size) {
        // El tope no es decorativo: sin el, un size enorme convierte una
        // busqueda paginada en un volcado de la tabla y el costo lo paga el
        // servicio. La invariante vive en el dominio para que no dependa de
        // que el borde web la valide.
        assertThatThrownBy(() -> new ProfessionalSearchCriteria(null, null, null, null, 0, size))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el tamaño maximo exacto se acepta")
    void aceptaElTamanioMaximo() {
        ProfessionalSearchCriteria borde =
                new ProfessionalSearchCriteria(
                        null, null, null, null, 0, ProfessionalSearchCriteria.TAMANIO_MAXIMO);

        assertThat(borde.size()).isEqualTo(ProfessionalSearchCriteria.TAMANIO_MAXIMO);
    }

    @Test
    @DisplayName("primeraPagina no filtra nada y usa el tamaño por defecto")
    void primeraPaginaEsElCatalogoCompleto() {
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.primeraPagina();

        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(ProfessionalSearchCriteria.TAMANIO_POR_DEFECTO);
    }
}

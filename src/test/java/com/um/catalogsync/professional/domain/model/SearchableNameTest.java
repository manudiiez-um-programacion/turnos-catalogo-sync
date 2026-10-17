package com.um.catalogsync.professional.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Prueba de la normalizacion de nombres.
 *
 * <p>Es la pieza de la que depende que la busqueda de #15 funcione: si el
 * nombre guardado y el termino buscado no se normalizan igual, buscar
 * "gomez" no encuentra a "Gómez" por mas que el indice este perfecto. Por
 * eso tiene prueba propia aunque sea una funcion de tres lineas.
 */
class SearchableNameTest {

    @ParameterizedTest
    @CsvSource({
        "Gómez,          gomez",
        "GÓMEZ,          gomez",
        "Núñez,          nunez",
        "Peña,           pena",
        "Rodríguez,      rodriguez",
        "Ávila,          avila",
        "Sáenz Peña,     saenz pena",
        "  Gómez  ,      gomez",
    })
    @DisplayName("Pasa a minusculas, saca los acentos y colapsa los espacios")
    void normaliza(String entrada, String esperado) {
        assertThat(SearchableName.normalize(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("La ñ pierde la tilde, igual que hace unaccent en PostgreSQL")
    void laEnieSeConvierteEnN() {
        assertThat(SearchableName.normalize("Núñez")).isEqualTo("nunez");
        assertThat(SearchableName.normalize("ñandú")).isEqualTo("nandu");
    }

    @Test
    @DisplayName("Arma el nombre completo con nombre y apellido")
    void armaElNombreCompleto() {
        assertThat(SearchableName.of("Ana", "Gómez")).isEqualTo("ana gomez");
        assertThat(SearchableName.of("JOSÉ MARÍA", "Peña")).isEqualTo("jose maria pena");
    }

    @Test
    @DisplayName("Con null no explota: devuelve cadena vacia o ignora la parte que falta")
    void toleraNull() {
        assertThat(SearchableName.normalize(null)).isEmpty();
        assertThat(SearchableName.of(null, null)).isEmpty();
        assertThat(SearchableName.of("Ana", null)).isEqualTo("ana");
        assertThat(SearchableName.of(null, "Gómez")).isEqualTo("gomez");
    }

    @Test
    @DisplayName("Normalizar algo ya normalizado no lo cambia")
    void esIdempotente() {
        String unaVez = SearchableName.normalize("José Núñez");

        assertThat(SearchableName.normalize(unaVez)).isEqualTo(unaVez);
    }
}

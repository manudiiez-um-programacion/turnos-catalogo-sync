package com.um.catalogsync.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las dos preguntas que el modelo responde por su cuenta.
 *
 * <p>Estan en el modelo y no en quien lo consulta porque las necesitan #25
 * —para saltearse el trabajo de leer entidades— y #28 —para informar que la
 * version se aplico sin cambios—. Escritas en cada lado, serian la misma
 * condicion de tres listas repetida dos veces, y la que se olvidara de una
 * lista no fallaria: diria que no hubo cambios.
 */
class CatalogChangesTest {

    @Test
    @DisplayName("Las tres listas vacias son una version sin cambios")
    void sinCambios() {
        assertThat(cambios(List.of(), List.of(), List.of()).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Un id en cualquiera de las tres listas ya es un cambio")
    void conCambiosEnUnaSola() {
        // Las tres por separado: una condicion que mirara solo dos listas
        // pasaria la prueba de la tercera coleccion sin esto.
        assertThat(cambios(List.of(1L), List.of(), List.of()).isEmpty()).isFalse();
        assertThat(cambios(List.of(), List.of(15L), List.of()).isEmpty()).isFalse();
        assertThat(cambios(List.of(), List.of(), List.of(21L)).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("El total es cuantas entidades hay que ir a leer")
    void elTotalSumaLasTres() {
        // Es la cuenta de lecturas que #25 va a hacer contra los hashes, no
        // una cantidad de entidades distintas: los ids repetidos cuentan,
        // porque cada uno es una lectura.
        assertThat(cambios(List.of(1L, 2L), List.of(15L), List.of(21L, 22L, 23L)).totalIds())
                .isEqualTo(6);
        assertThat(cambios(List.of(), List.of(), List.of()).totalIds()).isZero();
    }

    private CatalogChanges cambios(
            List<Long> categorias, List<Long> profesionales, List<Long> horarios) {
        return CatalogChanges.builder()
                .version(7L)
                .categoryIds(categorias)
                .professionalIds(profesionales)
                .scheduleIds(horarios)
                .build();
    }
}

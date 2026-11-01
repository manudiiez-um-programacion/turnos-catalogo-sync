package com.um.catalogsync.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * La regla de §14.2 y §18.2 convertida en prueba: desde que version local
 * todavia se puede avanzar con cambios incrementales.
 *
 * <p>El borde exacto es lo unico interesante y es donde se equivoca
 * cualquiera que escriba la comparacion de memoria: una version local
 * <b>igual</b> a {@code oldestAvailableVersion} todavia sirve, porque lo que
 * hace falta son los cambios posteriores a ella y esos estan. Con un
 * {@code >} en lugar de {@code >=} el servicio pediria un snapshot completo
 * cada vez que se queda justo en el borde de la ventana.
 */
class PublishedCatalogMetadataTest {

    @ParameterizedTest
    @CsvSource({
        "4, 4, true",
        "4, 5, true",
        "4, 7, true",
        "4, 3, false",
        "4, 0, false"
    })
    @DisplayName("Se puede avanzar incremental desde la version mas antigua o mas arriba")
    void sePuedeAvanzar(long masAntigua, long local, boolean esperado) {
        assertThat(metadata(masAntigua).canCatchUpFrom(local)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Con la ventana entera disponible, cualquier version local sirve")
    void ventanaCompleta() {
        // oldestAvailableVersion en 0 es una catedra que no descarto nada
        // todavia: no hay discontinuidad posible.
        assertThat(metadata(0L).canCatchUpFrom(0L)).isTrue();
    }

    private static PublishedCatalogMetadata metadata(long oldestAvailableVersion) {
        return PublishedCatalogMetadata.builder()
                .currentVersion(7L)
                .oldestAvailableVersion(oldestAvailableVersion)
                .publishedAt(Instant.parse("2026-09-10T13:44:59Z"))
                .build();
    }
}

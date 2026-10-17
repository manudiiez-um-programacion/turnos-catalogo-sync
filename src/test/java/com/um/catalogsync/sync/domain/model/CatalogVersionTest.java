package com.um.catalogsync.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Prueba la regla de avance sin base de datos. Es dominio puro: la decision
 * de si una version puede avanzar a otra no necesita PostgreSQL para
 * verificarse, y tenerla aca es lo que permite leer la regla completa de un
 * solo lado.
 */
class CatalogVersionTest {

    private static CatalogVersion enVersion(long version) {
        return CatalogVersion.builder()
                .version(version)
                .appliedAt(Instant.parse("2026-09-10T13:40:00Z"))
                .source(CatalogVersionSource.INCREMENTAL)
                .build();
    }

    @ParameterizedTest
    @EnumSource(CatalogVersionSource.class)
    @DisplayName("Avanzar a una version mayor siempre se permite, con cualquier origen")
    void avanzarHaciaAdelanteSiempreSePermite(CatalogVersionSource origen) {
        assertThat(enVersion(5L).allowsAdvanceTo(6L, origen)).isTrue();
        assertThat(enVersion(5L).allowsAdvanceTo(999L, origen)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(CatalogVersionSource.class)
    @DisplayName("Quedarse en la misma version se permite: reprocesar no es retroceder")
    void laMismaVersionSePermite(CatalogVersionSource origen) {
        assertThat(enVersion(5L).allowsAdvanceTo(5L, origen)).isTrue();
    }

    @Test
    @DisplayName("Retroceder por sincronizacion incremental se rechaza")
    void retrocederPorIncrementalSeRechaza() {
        assertThat(enVersion(7L).allowsAdvanceTo(6L, CatalogVersionSource.INCREMENTAL)).isFalse();
        assertThat(enVersion(7L).allowsAdvanceTo(0L, CatalogVersionSource.INCREMENTAL)).isFalse();
    }

    @Test
    @DisplayName("Retroceder por snapshot se permite: es la reconstruccion por discontinuidad")
    void retrocederPorSnapshotSePermite() {
        // §18.2: version local 3, Redis informa oldestAvailable 4. Se pide el
        // snapshot y su snapshotVersion manda, aunque sea menor que la local.
        assertThat(enVersion(7L).allowsAdvanceTo(3L, CatalogVersionSource.SNAPSHOT)).isTrue();
        assertThat(enVersion(7L).allowsAdvanceTo(0L, CatalogVersionSource.SNAPSHOT)).isTrue();
    }

    @Test
    @DisplayName("El origen de la version vigente no influye: lo que decide es el del avance")
    void decideElOrigenDelAvanceNoElVigente() {
        CatalogVersion vigentePorSnapshot = CatalogVersion.builder()
                .version(7L)
                .appliedAt(Instant.parse("2026-09-10T13:40:00Z"))
                .source(CatalogVersionSource.SNAPSHOT)
                .build();

        assertThat(vigentePorSnapshot.allowsAdvanceTo(6L, CatalogVersionSource.INCREMENTAL))
                .isFalse();
        assertThat(vigentePorSnapshot.allowsAdvanceTo(6L, CatalogVersionSource.SNAPSHOT))
                .isTrue();
    }
}

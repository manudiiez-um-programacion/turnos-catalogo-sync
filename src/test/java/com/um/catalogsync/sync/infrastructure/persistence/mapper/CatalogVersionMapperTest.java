package com.um.catalogsync.sync.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CatalogVersionMapperTest {

    private static final Instant APLICADA = Instant.parse("2026-09-10T13:40:00Z");

    private final CatalogVersionMapper mapper = new CatalogVersionMapper();

    private static CatalogVersion version7() {
        return CatalogVersion.builder()
                .version(7L)
                .appliedAt(APLICADA)
                .source(CatalogVersionSource.SNAPSHOT)
                .build();
    }

    @Test
    @DisplayName("Ida y vuelta conserva version, instante y origen")
    void idaYVueltaConservaLosCampos() {
        CatalogVersion original = version7();

        CatalogVersion resultado = mapper.toDomain(mapper.toEntity(original));

        assertThat(resultado).usingRecursiveComparison().isEqualTo(original);
    }

    @ParameterizedTest
    @EnumSource(CatalogVersionSource.class)
    @DisplayName("Los dos origenes viajan en los dos sentidos")
    void losDosOrigenesIdaYVuelta(CatalogVersionSource origen) {
        CatalogVersion original = version7();
        original.setSource(origen);

        CatalogVersionEntity entity = mapper.toEntity(original);

        assertThat(entity.getSource()).isEqualTo(origen.name());
        assertThat(mapper.toDomain(entity).getSource()).isSameAs(origen);
    }

    @Test
    @DisplayName("Siempre escribe el id unico que el CHECK de la tabla acepta")
    void siempreEscribeElIdUnico() {
        assertThat(mapper.toEntity(version7()).getId()).isEqualTo(CatalogVersionEntity.ID_UNICA);
    }

    @Test
    @DisplayName("Un origen que esta aplicacion nunca escribe delata un estado corrupto")
    void unOrigenDesconocidoEsEstadoCorrupto() {
        CatalogVersionEntity corrupta = mapper.toEntity(version7());
        corrupta.setSource("A_MANO");

        assertThatThrownBy(() -> mapper.toDomain(corrupta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A_MANO");
    }

    @Test
    @DisplayName("Con null devuelve null a los dos lados")
    void toleraNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}

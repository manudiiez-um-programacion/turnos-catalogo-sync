package com.um.catalogsync.professional.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.infrastructure.persistence.entity.ProfessionalEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfessionalMapperTest {

    private static final Instant CREADO = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-10T13:40:00Z");

    private final ProfessionalMapper mapper = new ProfessionalMapper();

    private static Professional ana() {
        return Professional.builder()
                .id(15L)
                .categoryId(1L)
                .firstName("Ana")
                .lastName("Gómez")
                .enabled(true)
                .createdAt(CREADO)
                .updatedAt(ACTUALIZADO)
                .build();
    }

    @Test
    @DisplayName("Ida y vuelta conserva los campos del contrato")
    void idaYVueltaConservaLosCampos() {
        Professional original = ana();

        Professional resultado = mapper.toDomain(mapper.toEntity(original));

        assertThat(resultado).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("Al ir a la entidad deriva el nombre de busqueda normalizado")
    void derivaElNombreDeBusqueda() {
        ProfessionalEntity entity = mapper.toEntity(ana());

        assertThat(entity.getSearchName()).isEqualTo("ana gomez");
    }

    @Test
    @DisplayName("El nombre de busqueda se recalcula: no se puede desincronizar del nombre real")
    void recalculaElNombreDeBusqueda() {
        Professional renombrada = ana();
        renombrada.setLastName("Núñez");

        assertThat(mapper.toEntity(renombrada).getSearchName()).isEqualTo("ana nunez");
    }

    @Test
    @DisplayName("El dominio no lleva el nombre de busqueda: es dato derivado")
    void elDominioNoExponeElNombreDeBusqueda() {
        ProfessionalEntity entity = mapper.toEntity(ana());
        entity.setSearchName("algo escrito a mano");

        Professional dominio = mapper.toDomain(entity);

        // Al volver a la entidad se recalcula desde el nombre, no se arrastra.
        assertThat(mapper.toEntity(dominio).getSearchName()).isEqualTo("ana gomez");
    }

    @Test
    @DisplayName("Con null devuelve null, y con colecciones null devuelve lista vacia")
    void toleraNull() {
        assertThat(mapper.toDomain((ProfessionalEntity) null)).isNull();
        assertThat(mapper.toEntity((Professional) null)).isNull();
        assertThat(mapper.toDomain((List<ProfessionalEntity>) null)).isEmpty();
        assertThat(mapper.toEntity((List<Professional>) null)).isEmpty();
    }
}

package com.um.catalogsync.category.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba del mapper. Sin Spring y sin base: es una funcion pura, levantar
 * un contexto para probarla solo la haria mas lenta.
 */
class ProfessionalCategoryMapperTest {

    private static final Instant CREADA = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ACTUALIZADA = Instant.parse("2026-09-10T13:40:00Z");

    private final ProfessionalCategoryMapper mapper = new ProfessionalCategoryMapper();

    @Test
    @DisplayName("Ida y vuelta: dominio -> entidad -> dominio conserva todos los campos")
    void idaYVueltaConservaLosCampos() {
        ProfessionalCategory original = ProfessionalCategory.builder()
                .id(1L)
                .name("Clinica medica")
                .description("Atencion clinica general")
                .enabled(true)
                .createdAt(CREADA)
                .updatedAt(ACTUALIZADA)
                .build();

        ProfessionalCategory resultado = mapper.toDomain(mapper.toEntity(original));

        assertThat(resultado).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("Ida y vuelta desde la entidad conserva todos los campos")
    void idaYVueltaDesdeLaEntidad() {
        ProfessionalCategoryEntity original = ProfessionalCategoryEntity.builder()
                .id(2L)
                .name("Kinesiologia")
                .description(null)
                .enabled(false)
                .createdAt(CREADA)
                .updatedAt(ACTUALIZADA)
                .build();

        ProfessionalCategoryEntity resultado = mapper.toEntity(mapper.toDomain(original));

        assertThat(resultado).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("Con null devuelve null, sin explotar")
    void toleraNull() {
        assertThat(mapper.toDomain((ProfessionalCategoryEntity) null)).isNull();
        assertThat(mapper.toEntity((ProfessionalCategory) null)).isNull();
    }

    @Test
    @DisplayName("Con una coleccion null devuelve lista vacia, no null")
    void coleccionNullDevuelveListaVacia() {
        assertThat(mapper.toDomain((List<ProfessionalCategoryEntity>) null)).isEmpty();
        assertThat(mapper.toEntity((List<ProfessionalCategory>) null)).isEmpty();
    }

    @Test
    @DisplayName("Una descripcion nula sobrevive el mapeo: es un campo opcional del contrato")
    void conservaLaDescripcionNula() {
        ProfessionalCategory sinDescripcion = ProfessionalCategory.builder()
                .id(3L)
                .name("Nutricion")
                .enabled(true)
                .createdAt(CREADA)
                .updatedAt(ACTUALIZADA)
                .build();

        assertThat(mapper.toEntity(sinDescripcion).getDescription()).isNull();
        assertThat(mapper.toDomain(mapper.toEntity(sinDescripcion)).getDescription()).isNull();
    }
}

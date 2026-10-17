package com.um.catalogsync.category.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueba del adapter contra PostgreSQL real.
 *
 * <p>Se inyecta por el puerto ({@code CategoryRepository}) y no por la
 * clase concreta, a proposito: lo que hay que probar es que el contrato que
 * ve el dominio se cumple, no los detalles del adapter.
 */
class JpaCategoryRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant CREADA = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ACTUALIZADA = Instant.parse("2026-09-10T13:40:00Z");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private static ProfessionalCategory categoria(Long id, String nombre) {
        return ProfessionalCategory.builder()
                .id(id)
                .name(nombre)
                .description("Descripcion de " + nombre)
                .enabled(true)
                .createdAt(CREADA)
                .updatedAt(ACTUALIZADA)
                .build();
    }

    @Test
    @DisplayName("upsert inserta cuando el id no existe, conservando el id de la catedra")
    void upsertInserta() {
        ProfessionalCategory guardada = categoryRepository.upsert(categoria(1L, "Clinica medica"));

        assertThat(guardada.getId()).isEqualTo(1L);
        assertThat(guardada.getName()).isEqualTo("Clinica medica");
        assertThat(guardada.getCreatedAt()).isEqualTo(CREADA);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM professional_category", Integer.class)).isOne();
    }

    @Test
    @DisplayName("upsert sobre un id existente actualiza en lugar de duplicar")
    void upsertActualiza() {
        categoryRepository.upsert(categoria(1L, "Clinica medica"));

        ProfessionalCategory modificada = ProfessionalCategory.builder()
                .id(1L)
                .name("Clinica medica general")
                .description("Otra descripcion")
                .enabled(false)
                .createdAt(CREADA)
                .updatedAt(Instant.parse("2026-10-01T00:00:00Z"))
                .build();
        categoryRepository.upsert(modificada);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM professional_category", Integer.class)).isOne();
        ProfessionalCategory leida = categoryRepository.findById(1L).orElseThrow();
        assertThat(leida.getName()).isEqualTo("Clinica medica general");
        assertThat(leida.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("findById devuelve Optional vacio cuando no esta, no null ni excepcion")
    void findByIdVacio() {
        Optional<ProfessionalCategory> resultado = categoryRepository.findById(999L);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("findAll devuelve el modelo de dominio, nunca la entidad JPA")
    void findAllDevuelveDominio() {
        categoryRepository.upsert(categoria(1L, "Clinica medica"));
        categoryRepository.upsert(categoria(2L, "Kinesiologia"));

        List<ProfessionalCategory> todas = categoryRepository.findAll();

        assertThat(todas).hasSize(2);
        assertThat(todas).allSatisfy(c ->
                assertThat(c).isExactlyInstanceOf(ProfessionalCategory.class));
        assertThat(todas).extracting(ProfessionalCategory::getName)
                .containsExactlyInAnyOrder("Clinica medica", "Kinesiologia");
    }

    @Test
    @DisplayName("replaceAll deja solo el contenido nuevo: lo que no viene, se va")
    void replaceAllReemplazaTodo() {
        categoryRepository.upsert(categoria(1L, "Clinica medica"));
        categoryRepository.upsert(categoria(2L, "Kinesiologia"));

        List<ProfessionalCategory> resultado = categoryRepository.replaceAll(
                List.of(categoria(3L, "Nutricion"), categoria(4L, "Cardiologia")));

        assertThat(resultado).hasSize(2);
        assertThat(categoryRepository.findAll())
                .extracting(ProfessionalCategory::getId)
                .containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    @DisplayName("replaceAll con una coleccion vacia deja la copia local vacia")
    void replaceAllConVacioBorraTodo() {
        categoryRepository.upsert(categoria(1L, "Clinica medica"));

        categoryRepository.replaceAll(List.of());

        assertThat(categoryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("deleteByIdIn borra solo las indicadas y devuelve cuantas borro")
    void deleteByIdInBorraLasIndicadas() {
        categoryRepository.upsert(categoria(1L, "Clinica medica"));
        categoryRepository.upsert(categoria(2L, "Kinesiologia"));
        categoryRepository.upsert(categoria(3L, "Nutricion"));

        long borradas = categoryRepository.deleteByIdIn(List.of(1L, 3L));

        assertThat(borradas).isEqualTo(2L);
        assertThat(categoryRepository.findAll())
                .extracting(ProfessionalCategory::getId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("deleteByIdIn con una coleccion vacia no borra nada ni falla")
    void deleteByIdInConVacioNoHaceNada() {
        categoryRepository.upsert(categoria(1L, "Clinica medica"));

        assertThat(categoryRepository.deleteByIdIn(List.of())).isZero();
        assertThat(categoryRepository.deleteByIdIn(null)).isZero();
        assertThat(categoryRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Los timestamps de la catedra se guardan y se leen sin corrimiento")
    void conservaLosTimestamps() {
        categoryRepository.upsert(categoria(1L, "Clinica medica"));

        ProfessionalCategory leida = categoryRepository.findById(1L).orElseThrow();

        assertThat(leida.getCreatedAt()).isEqualTo(CREADA);
        assertThat(leida.getUpdatedAt()).isEqualTo(ACTUALIZADA);
    }
}

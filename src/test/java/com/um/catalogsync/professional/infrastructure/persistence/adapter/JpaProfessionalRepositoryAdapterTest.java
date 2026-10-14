package com.um.catalogsync.professional.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.professional.application.exception.UnknownCategoryException;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class JpaProfessionalRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant CREADO = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-10T13:40:00Z");

    private static final long CATEGORIA_EXISTENTE = 1L;
    private static final long CATEGORIA_INEXISTENTE = 777L;

    /**
     * Los mismos instantes, como OffsetDateTime. El driver JDBC crudo no
     * sabe que tipo SQL usar para un Instant, asi que los INSERT que este
     * test hace a mano —no los que pasan por JPA— necesitan esta forma.
     */
    private static final OffsetDateTime CREADO_TZ = CREADO.atOffset(ZoneOffset.UTC);
    private static final OffsetDateTime ACTUALIZADO_TZ = ACTUALIZADO.atOffset(ZoneOffset.UTC);

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * La base llega vacia desde {@code AbstractPostgresIntegrationTest}:
     * aca solo se precarga la categoria que los profesionales necesitan,
     * porque la FK es RESTRICT y sin ella no se puede insertar ninguno.
     */
    @BeforeEach
    void precargarLaCategoria() {
        jdbc.update("INSERT INTO professional_category "
                + "(id, name, enabled, created_at, updated_at) VALUES (?, ?, true, ?, ?)",
                CATEGORIA_EXISTENTE, "Clinica medica", CREADO_TZ, ACTUALIZADO_TZ);
    }

    private static Professional profesional(Long id, String nombre, String apellido) {
        return profesional(id, nombre, apellido, CATEGORIA_EXISTENTE);
    }

    private static Professional profesional(
            Long id, String nombre, String apellido, Long categoryId) {
        return Professional.builder()
                .id(id)
                .categoryId(categoryId)
                .firstName(nombre)
                .lastName(apellido)
                .enabled(true)
                .createdAt(CREADO)
                .updatedAt(ACTUALIZADO)
                .build();
    }

    @Test
    @DisplayName("upsert inserta y conserva el id de la catedra")
    void upsertInserta() {
        Professional guardado = professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));

        assertThat(guardado.getId()).isEqualTo(15L);
        assertThat(guardado.getCategoryId()).isEqualTo(CATEGORIA_EXISTENTE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM professional", Integer.class)).isOne();
    }

    @Test
    @DisplayName("upsert sobre un id ya presente actualiza en lugar de duplicar")
    void upsertSobreIdExistenteActualiza() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));

        professionalRepository.upsert(Professional.builder()
                .id(15L)
                .categoryId(CATEGORIA_EXISTENTE)
                .firstName("Ana Maria")
                .lastName("Gomez")
                .enabled(false)
                .createdAt(CREADO)
                .updatedAt(Instant.parse("2026-10-01T00:00:00Z"))
                .build());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM professional", Integer.class)).isOne();
        Professional leido = professionalRepository.findById(15L).orElseThrow();
        assertThat(leido.getFirstName()).isEqualTo("Ana Maria");
        assertThat(leido.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("upsert escribe el nombre de busqueda normalizado en la base")
    void upsertEscribeElNombreDeBusqueda() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gómez"));

        String searchName = jdbc.queryForObject(
                "SELECT search_name FROM professional WHERE id = 15", String.class);

        assertThat(searchName).isEqualTo("ana gomez");
    }

    @Test
    @DisplayName("El nombre de busqueda se actualiza al cambiar el apellido")
    void actualizaElNombreDeBusqueda() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gómez"));

        professionalRepository.upsert(profesional(15L, "Ana", "Núñez"));

        assertThat(jdbc.queryForObject(
                "SELECT search_name FROM professional WHERE id = 15", String.class))
                .isEqualTo("ana nunez");
    }

    @Test
    @DisplayName("Un profesional cuya categoria no esta localmente se RECHAZA")
    void rechazaCategoriaDesconocida() {
        Professional huerfano = profesional(15L, "Ana", "Gomez", CATEGORIA_INEXISTENTE);

        assertThatThrownBy(() -> professionalRepository.upsert(huerfano))
                .isInstanceOf(UnknownCategoryException.class)
                .hasMessageContaining(String.valueOf(CATEGORIA_INEXISTENTE));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM professional", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("replaceAll tambien rechaza si alguno referencia una categoria que no esta")
    void replaceAllRechazaCategoriaDesconocida() {
        assertThatThrownBy(() -> professionalRepository.replaceAll(List.of(
                profesional(15L, "Ana", "Gomez"),
                profesional(16L, "Juan", "Perez", CATEGORIA_INEXISTENTE))))
                .isInstanceOf(UnknownCategoryException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM professional", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Una violacion de otra FK se propaga tal cual, sin disfrazarse de categoria")
    void noConfundeLasDosFk() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));
        jdbc.update("INSERT INTO weekly_schedule (id, professional_id, day_of_week, start_time, "
                + "end_time, slot_duration_minutes, enabled, created_at, updated_at) "
                + "VALUES (21, 15, 'MONDAY', '09:00', '12:00', 30, true, ?, ?)",
                CREADO_TZ, ACTUALIZADO_TZ);

        // Borrar el profesional choca con fk_weekly_schedule_professional, que da
        // el mismo SQLSTATE que la FK de categoria pero significa lo opuesto.
        assertThatThrownBy(() -> professionalRepository.deleteByIdIn(List.of(15L)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(UnknownCategoryException.class);
    }

    @Test
    @DisplayName("findById devuelve Optional vacio cuando no esta")
    void findByIdVacio() {
        assertThat(professionalRepository.findById(999L)).isEmpty();
    }

    @Test
    @DisplayName("findAll devuelve el modelo de dominio, nunca la entidad JPA")
    void findAllDevuelveDominio() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));
        professionalRepository.upsert(profesional(16L, "Juan", "Perez"));

        List<Professional> todos = professionalRepository.findAll();

        assertThat(todos).hasSize(2)
                .allSatisfy(p -> assertThat(p).isExactlyInstanceOf(Professional.class))
                .extracting(Professional::getLastName)
                .containsExactlyInAnyOrder("Gomez", "Perez");
    }

    @Test
    @DisplayName("replaceAll deja solo el contenido nuevo")
    void replaceAllReemplazaTodo() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));
        professionalRepository.upsert(profesional(16L, "Juan", "Perez"));

        professionalRepository.replaceAll(List.of(profesional(17L, "Lucia", "Diaz")));

        assertThat(professionalRepository.findAll())
                .extracting(Professional::getId)
                .containsExactly(17L);
    }

    @Test
    @DisplayName("deleteByIdIn borra solo los indicados y devuelve cuantos borro")
    void deleteByIdInBorraLosIndicados() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));
        professionalRepository.upsert(profesional(16L, "Juan", "Perez"));

        assertThat(professionalRepository.deleteByIdIn(List.of(15L))).isEqualTo(1L);
        assertThat(professionalRepository.findAll())
                .extracting(Professional::getId)
                .containsExactly(16L);
    }

    @Test
    @DisplayName("deleteByIdIn con vacio o null no borra nada ni falla")
    void deleteByIdInConVacioNoHaceNada() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));

        assertThat(professionalRepository.deleteByIdIn(List.of())).isZero();
        assertThat(professionalRepository.deleteByIdIn(null)).isZero();
        assertThat(professionalRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Los timestamps de la catedra se guardan y se leen sin corrimiento")
    void conservaLosTimestamps() {
        professionalRepository.upsert(profesional(15L, "Ana", "Gomez"));

        Professional leido = professionalRepository.findById(15L).orElseThrow();

        assertThat(leido.getCreatedAt()).isEqualTo(CREADO);
        assertThat(leido.getUpdatedAt()).isEqualTo(ACTUALIZADO);
    }
}

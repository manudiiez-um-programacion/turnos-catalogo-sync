package com.um.catalogsync.professional.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.professional.domain.model.Professional;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfessionalServiceTest {

    private static final Professional ANA = Professional.builder()
            .id(15L)
            .categoryId(1L)
            .firstName("Ana")
            .lastName("Gomez")
            .enabled(true)
            .build();

    @Test
    @DisplayName("getById devuelve el profesional cuando esta")
    void getByIdDevuelveElProfesional() {
        ProfessionalService service = new ProfessionalService(List::of, id -> Optional.of(ANA));

        assertThat(service.getById(15L)).isSameAs(ANA);
    }

    @Test
    @DisplayName("getById convierte un Optional vacio en la excepcion del slice")
    void getByIdTraduceElVacioAExcepcion() {
        ProfessionalService service = new ProfessionalService(List::of, id -> Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findById deja pasar el vacio sin lanzar")
    void findByIdNoLanza() {
        ProfessionalService service = new ProfessionalService(List::of, id -> Optional.empty());

        assertThat(service.findById(999L)).isEmpty();
    }

    @Test
    @DisplayName("getAll delega en el caso de uso")
    void getAllDelega() {
        ProfessionalService service =
                new ProfessionalService(() -> List.of(ANA), id -> Optional.empty());

        assertThat(service.getAll()).containsExactly(ANA);
    }
}

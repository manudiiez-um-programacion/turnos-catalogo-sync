package com.um.catalogsync.category.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.category.application.exception.CategoryNotFoundException;
import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.in.GetAllCategoriesUseCase;
import com.um.catalogsync.category.domain.ports.in.GetCategoryByIdUseCase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba del facade. Con dobles escritos a mano en lugar de una base: lo
 * unico que decide este slice en la capa de aplicacion es que significa un
 * {@code Optional} vacio, y eso se prueba sin infraestructura. Es
 * justamente lo que la arquitectura hexagonal viene a permitir.
 */
class CategoryServiceTest {

    private static final ProfessionalCategory CLINICA = ProfessionalCategory.builder()
            .id(1L)
            .name("Clinica medica")
            .enabled(true)
            .build();

    @Test
    @DisplayName("getById devuelve la categoria cuando esta")
    void getByIdDevuelveLaCategoria() {
        CategoryService service = new CategoryService(List::of, id -> Optional.of(CLINICA));

        assertThat(service.getById(1L)).isSameAs(CLINICA);
    }

    @Test
    @DisplayName("getById convierte un Optional vacio en la excepcion del slice")
    void getByIdTraduceElVacioAExcepcion() {
        CategoryService service = new CategoryService(List::of, id -> Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findById deja pasar el Optional vacio para quien puede seguir sin la categoria")
    void findByIdNoLanza() {
        CategoryService service = new CategoryService(List::of, id -> Optional.empty());

        assertThat(service.findById(999L)).isEmpty();
    }

    @Test
    @DisplayName("getAll delega en el caso de uso y devuelve lo que este trae")
    void getAllDelega() {
        GetAllCategoriesUseCase todas = () -> List.of(CLINICA);
        GetCategoryByIdUseCase porId = id -> Optional.empty();

        assertThat(new CategoryService(todas, porId).getAll()).containsExactly(CLINICA);
    }
}

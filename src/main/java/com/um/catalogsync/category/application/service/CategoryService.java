package com.um.catalogsync.category.application.service;

import com.um.catalogsync.category.application.exception.CategoryNotFoundException;
import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.in.GetAllCategoriesUseCase;
import com.um.catalogsync.category.domain.ports.in.GetCategoryByIdUseCase;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade del slice de categorias: la unica puerta de entrada para el resto
 * de la aplicacion.
 *
 * <p>Compone los casos de uso y es quien decide que significa un
 * {@code Optional} vacio. Los puertos devuelven {@code Optional} porque "no
 * esta" es un dato, no un error; aca se traduce a la excepcion del slice,
 * que es la decision que la capa web necesita.
 *
 * <p>Se exponen las dos formas a proposito: {@link #getById(Long)} para
 * quien necesita la categoria y considera su ausencia un error, y
 * {@link #findById(Long)} para quien puede seguir sin ella.
 *
 * <p>No tiene operaciones de escritura. Las escrituras vienen de la
 * sincronizacion, que trabaja contra el puerto de salida del slice
 * ({@code CategoryRepository}), no contra este facade.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final GetAllCategoriesUseCase getAllCategories;
    private final GetCategoryByIdUseCase getCategoryById;

    public List<ProfessionalCategory> getAll() {
        return getAllCategories.getAll();
    }

    /**
     * @throws CategoryNotFoundException si la categoria no esta en la copia
     *     local
     */
    public ProfessionalCategory getById(Long id) {
        return getCategoryById.getById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    public Optional<ProfessionalCategory> findById(Long id) {
        return getCategoryById.getById(id);
    }
}

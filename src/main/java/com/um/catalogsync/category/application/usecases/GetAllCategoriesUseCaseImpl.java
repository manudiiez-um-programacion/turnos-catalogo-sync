package com.um.catalogsync.category.application.usecases;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.in.GetAllCategoriesUseCase;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lista las categorias locales.
 *
 * <p>Depende solo del puerto de salida: no conoce JPA ni la entidad. Las
 * busquedas nunca consultan al servicio central, solo la copia local.
 */
@Component
@RequiredArgsConstructor
public class GetAllCategoriesUseCaseImpl implements GetAllCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    @Override
    public List<ProfessionalCategory> getAll() {
        return categoryRepository.findAll();
    }
}

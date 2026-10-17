package com.um.catalogsync.category.application.usecases;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.in.GetCategoryByIdUseCase;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Busca una categoria local por el id de la catedra. */
@Component
@RequiredArgsConstructor
public class GetCategoryByIdUseCaseImpl implements GetCategoryByIdUseCase {

    private final CategoryRepository categoryRepository;

    @Override
    public Optional<ProfessionalCategory> getById(Long id) {
        return categoryRepository.findById(id);
    }
}

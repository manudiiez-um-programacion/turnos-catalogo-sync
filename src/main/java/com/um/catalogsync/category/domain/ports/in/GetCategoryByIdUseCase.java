package com.um.catalogsync.category.domain.ports.in;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import java.util.Optional;

/** Puerto de entrada: buscar una categoria por el id de la catedra. */
public interface GetCategoryByIdUseCase {

    /**
     * @return la categoria, o vacio si no esta. Convertir ese vacio en un
     *     error es responsabilidad del facade del slice, no de este puerto.
     */
    Optional<ProfessionalCategory> getById(Long id);
}

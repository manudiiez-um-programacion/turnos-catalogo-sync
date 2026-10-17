package com.um.catalogsync.category.domain.ports.in;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import java.util.List;

/** Puerto de entrada: listar todas las categorias de la copia local. */
public interface GetAllCategoriesUseCase {

    List<ProfessionalCategory> getAll();
}

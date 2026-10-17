package com.um.catalogsync.category.infrastructure.persistence.repository;

import com.um.catalogsync.category.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data sobre la entidad.
 *
 * <p>Detalle de infraestructura: no es el puerto. El puerto del dominio es
 * {@code CategoryRepository}, y quien lo implementa usando esto es el
 * adapter. La distincion importa porque este tipo habla de entidades JPA y
 * el dominio no puede verlas.
 */
public interface JpaProfessionalCategoryRepository
        extends JpaRepository<ProfessionalCategoryEntity, Long> {

    long deleteByIdIn(Collection<Long> ids);
}

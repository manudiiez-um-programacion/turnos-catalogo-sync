package com.um.catalogsync.professional.infrastructure.persistence.repository;

import com.um.catalogsync.professional.infrastructure.persistence.entity.ProfessionalEntity;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data. Detalle de infraestructura, no el puerto: el
 * puerto del dominio es {@code ProfessionalRepository}.
 */
public interface JpaProfessionalRepository extends JpaRepository<ProfessionalEntity, Long> {

    long deleteByIdIn(Collection<Long> ids);
}

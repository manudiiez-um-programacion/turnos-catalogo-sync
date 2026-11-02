package com.um.catalogsync.professional.infrastructure.persistence.repository;

import com.um.catalogsync.professional.infrastructure.persistence.entity.ProfessionalEntity;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio Spring Data. Detalle de infraestructura, no el puerto: el
 * puerto del dominio es {@code ProfessionalRepository}.
 *
 * <p>Suma {@code JpaSpecificationExecutor} para la busqueda con filtros
 * combinables (#15). Es la pieza que permite construir la consulta con solo
 * los filtros que vienen, en lugar de un metodo derivado por combinacion
 * posible —serian ocho con tres filtros, y dieciseis cuando se agregue la
 * disponibilidad (#16)—.
 */
public interface JpaProfessionalRepository
        extends JpaRepository<ProfessionalEntity, Long>,
                JpaSpecificationExecutor<ProfessionalEntity> {

    long deleteByIdIn(Collection<Long> ids);
}

package com.um.catalogsync.professional.infrastructure.persistence.mapper;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.SearchableName;
import com.um.catalogsync.professional.infrastructure.persistence.entity.ProfessionalEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre la entidad JPA y el modelo de dominio.
 *
 * <p>Ademas de copiar campos, deriva {@code searchName} al ir hacia la
 * entidad. Es el unico lugar donde se escribe esa columna, asi que no hay
 * forma de guardar un profesional con el nombre de busqueda desactualizado.
 * En el camino de vuelta no se lee: es dato derivado, el dominio no lo
 * necesita y tenerlo invitaria a que alguien lo modificara por su cuenta.
 */
@Component
public class ProfessionalMapper {

    public Professional toDomain(ProfessionalEntity entity) {
        if (entity == null) {
            return null;
        }
        return Professional.builder()
                .id(entity.getId())
                .categoryId(entity.getCategoryId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .enabled(entity.isEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ProfessionalEntity toEntity(Professional professional) {
        if (professional == null) {
            return null;
        }
        return ProfessionalEntity.builder()
                .id(professional.getId())
                .categoryId(professional.getCategoryId())
                .firstName(professional.getFirstName())
                .lastName(professional.getLastName())
                .searchName(SearchableName.of(
                        professional.getFirstName(), professional.getLastName()))
                .enabled(professional.isEnabled())
                .createdAt(professional.getCreatedAt())
                .updatedAt(professional.getUpdatedAt())
                .build();
    }

    public List<Professional> toDomain(Collection<ProfessionalEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(this::toDomain).toList();
    }

    public List<ProfessionalEntity> toEntity(Collection<Professional> professionals) {
        if (professionals == null) {
            return List.of();
        }
        return professionals.stream().map(this::toEntity).toList();
    }
}

package com.um.catalogsync.category.infrastructure.persistence.mapper;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre la entidad JPA y el modelo de dominio.
 *
 * <p>Es el borde que sostiene la regla de dependencias: gracias a este
 * mapeo, nada de {@code jakarta.persistence} cruza hacia
 * {@code application} o {@code domain}.
 *
 * <p>Todos los metodos toleran {@code null} y devuelven {@code null}, para
 * que el adapter pueda mapear el resultado de una busqueda sin preguntar
 * antes.
 */
@Component
public class ProfessionalCategoryMapper {

    public ProfessionalCategory toDomain(ProfessionalCategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProfessionalCategory.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .enabled(entity.isEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ProfessionalCategoryEntity toEntity(ProfessionalCategory category) {
        if (category == null) {
            return null;
        }
        return ProfessionalCategoryEntity.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .enabled(category.isEnabled())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public List<ProfessionalCategory> toDomain(Collection<ProfessionalCategoryEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(this::toDomain).toList();
    }

    public List<ProfessionalCategoryEntity> toEntity(Collection<ProfessionalCategory> categories) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream().map(this::toEntity).toList();
    }
}

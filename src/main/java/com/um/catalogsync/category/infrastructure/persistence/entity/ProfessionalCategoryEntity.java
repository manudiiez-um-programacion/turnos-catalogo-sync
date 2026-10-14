package com.um.catalogsync.category.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representacion JPA de una categoria.
 *
 * <p>Separada del modelo de dominio a proposito: aca viven las decisiones
 * de base de datos (nombre de tabla, columnas, nulabilidad) y en el dominio
 * las de negocio. Esta clase no sale de {@code infrastructure}: el adapter
 * mapea a {@code ProfessionalCategory} antes de devolver.
 *
 * <p>La tabla la crea la migracion {@code V1__catalogo_local.sql}, nunca
 * Hibernate: {@code ddl-auto} esta en {@code validate}, asi que si esta
 * clase y la migracion no coinciden, la aplicacion no arranca. Eso es
 * deliberado — es la red que avisa si un tipo quedo mal elegido.
 */
@Entity
@Table(name = "professional_category")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalCategoryEntity {

    /**
     * Id de la catedra. Sin {@code @GeneratedValue}: no se genera
     * localmente, llega en el snapshot y es la identidad de la entidad en
     * el sistema de origen.
     */
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

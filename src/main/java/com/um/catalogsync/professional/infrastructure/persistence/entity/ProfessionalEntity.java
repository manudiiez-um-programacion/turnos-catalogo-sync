package com.um.catalogsync.professional.infrastructure.persistence.entity;

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
 * Representacion JPA de un profesional.
 *
 * <p>La categoria es una columna {@code Long} y no un {@code @ManyToOne} a
 * {@code ProfessionalCategoryEntity}. Es deliberado: una relacion JPA
 * ataria este slice a la entidad del otro, justo lo que el slicing vertical
 * evita, y traeria carga perezosa y proxies que no necesitamos. La
 * integridad la garantiza la FK en la base, no el mapeo.
 */
@Entity
@Table(name = "professional")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalEntity {

    /** Id de la catedra. Sin {@code @GeneratedValue}. */
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "first_name", nullable = false, length = 255)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 255)
    private String lastName;

    /**
     * Nombre completo normalizado que indexa el GIN de trigramas.
     *
     * <p>Columna de apoyo a la busqueda, no un campo del contrato: por eso
     * existe aca y no en el modelo de dominio. La calcula el mapper con
     * {@code SearchableName} en cada escritura, asi que no puede quedar
     * desincronizada del nombre real.
     */
    @Column(name = "search_name", nullable = false, length = 511)
    private String searchName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

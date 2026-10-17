package com.um.catalogsync.category.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Categoria profesional de la copia local del catalogo.
 *
 * <p>POJO puro: sin anotaciones de JPA, de validacion ni de Jackson. El
 * dominio no sabe que existe una base de datos ni una API. La
 * {@code @Entity} equivalente vive en {@code infrastructure.persistence} y
 * se mapea en el borde.
 *
 * <p>El {@code id} es el de la catedra, no se genera localmente: este
 * servicio copia categorias, no las crea.
 *
 * <p>{@code createdAt} y {@code updatedAt} tambien son datos de la catedra
 * y no auditoria propia: llegan en el snapshot (§7) y se guardan como
 * vienen.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalCategory {

    private Long id;
    private String name;
    private String description;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}

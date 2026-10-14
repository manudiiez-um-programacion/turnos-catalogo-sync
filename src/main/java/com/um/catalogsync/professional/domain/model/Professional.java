package com.um.catalogsync.professional.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Profesional de la copia local del catalogo.
 *
 * <p>POJO puro, igual que {@code ProfessionalCategory}: sin JPA, sin
 * validacion, sin Jackson.
 *
 * <p>La categoria se referencia por {@code categoryId} y no por un objeto
 * {@code ProfessionalCategory} ni por una relacion JPA. Dos razones: el
 * slice queda autocontenido, sin depender del modelo de otro slice; y es lo
 * que trae el contrato (§7 manda {@code categoryId}, no la categoria
 * entera). Quien necesite los datos de la categoria los pide al slice de
 * categorias por su facade.
 *
 * <p>No tiene el nombre normalizado para busqueda: es un dato derivado de
 * {@code firstName} y {@code lastName}, y guardarlo aca permitiria que
 * quedara desincronizado del nombre real. Se calcula en el borde de
 * persistencia con {@link SearchableName}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Professional {

    private Long id;
    private Long categoryId;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}

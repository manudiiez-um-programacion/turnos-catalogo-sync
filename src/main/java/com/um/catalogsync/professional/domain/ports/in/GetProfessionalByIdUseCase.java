package com.um.catalogsync.professional.domain.ports.in;

import com.um.catalogsync.professional.domain.model.Professional;
import java.util.Optional;

/** Puerto de entrada: buscar un profesional por el id de la catedra. */
public interface GetProfessionalByIdUseCase {

    /**
     * @return el profesional, o vacio si no esta. Decidir si ese vacio es un
     *     error es responsabilidad del facade.
     */
    Optional<Professional> getById(Long id);
}

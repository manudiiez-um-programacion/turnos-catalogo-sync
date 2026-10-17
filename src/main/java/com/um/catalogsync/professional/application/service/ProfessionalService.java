package com.um.catalogsync.professional.application.service;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.in.GetAllProfessionalsUseCase;
import com.um.catalogsync.professional.domain.ports.in.GetProfessionalByIdUseCase;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade del slice de profesionales.
 *
 * <p>Misma division que en categorias: los puertos devuelven
 * {@code Optional} y aca se decide que significa el vacio. La busqueda y
 * los filtros se suman en #15; por ahora expone lo minimo para que el slice
 * sea usable.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final GetAllProfessionalsUseCase getAllProfessionals;
    private final GetProfessionalByIdUseCase getProfessionalById;

    public List<Professional> getAll() {
        return getAllProfessionals.getAll();
    }

    /** @throws ProfessionalNotFoundException si el profesional no esta */
    public Professional getById(Long id) {
        return getProfessionalById.getById(id)
                .orElseThrow(() -> new ProfessionalNotFoundException(id));
    }

    public Optional<Professional> findById(Long id) {
        return getProfessionalById.getById(id);
    }
}

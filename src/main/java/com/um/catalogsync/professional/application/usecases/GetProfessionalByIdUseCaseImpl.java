package com.um.catalogsync.professional.application.usecases;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.in.GetProfessionalByIdUseCase;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Busca un profesional local por el id de la catedra. */
@Component
@RequiredArgsConstructor
public class GetProfessionalByIdUseCaseImpl implements GetProfessionalByIdUseCase {

    private final ProfessionalRepository professionalRepository;

    @Override
    public Optional<Professional> getById(Long id) {
        return professionalRepository.findById(id);
    }
}

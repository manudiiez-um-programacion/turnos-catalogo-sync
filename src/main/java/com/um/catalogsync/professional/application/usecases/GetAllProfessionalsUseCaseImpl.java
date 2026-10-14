package com.um.catalogsync.professional.application.usecases;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.in.GetAllProfessionalsUseCase;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Lista los profesionales locales. Nunca consulta al servicio central. */
@Component
@RequiredArgsConstructor
public class GetAllProfessionalsUseCaseImpl implements GetAllProfessionalsUseCase {

    private final ProfessionalRepository professionalRepository;

    @Override
    public List<Professional> getAll() {
        return professionalRepository.findAll();
    }
}

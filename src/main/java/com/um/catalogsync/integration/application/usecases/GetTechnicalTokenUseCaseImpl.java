package com.um.catalogsync.integration.application.usecases;

import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import com.um.catalogsync.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.um.catalogsync.shared.domain.model.Secret;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Entrega el JWT tecnico de la sesion vigente. */
@Component
@RequiredArgsConstructor
public class GetTechnicalTokenUseCaseImpl implements GetTechnicalTokenUseCase {

    private final TechnicalSessionCache sesion;

    @Override
    public Secret get() {
        return sesion.current().getTechnicalToken();
    }
}

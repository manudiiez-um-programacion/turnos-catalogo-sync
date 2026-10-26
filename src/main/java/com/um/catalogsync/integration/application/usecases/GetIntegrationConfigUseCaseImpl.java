package com.um.catalogsync.integration.application.usecases;

import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Entrega la configuracion de la sesion vigente, sin ir a la catedra. */
@Component
@RequiredArgsConstructor
public class GetIntegrationConfigUseCaseImpl implements GetIntegrationConfigUseCase {

    private final TechnicalSessionCache sesion;

    @Override
    public IntegrationConfig get() {
        return sesion.current().getIntegration();
    }
}

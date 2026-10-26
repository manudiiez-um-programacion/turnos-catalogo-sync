package com.um.catalogsync.integration.application.usecases;

import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.in.RefreshIntegrationConfigUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Fuerza la relectura de la configuracion contra la catedra. */
@Component
@RequiredArgsConstructor
public class RefreshIntegrationConfigUseCaseImpl implements RefreshIntegrationConfigUseCase {

    private final TechnicalSessionCache sesion;

    @Override
    public IntegrationConfig refresh() {
        return sesion.refreshConfig();
    }
}

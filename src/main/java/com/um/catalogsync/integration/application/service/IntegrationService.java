package com.um.catalogsync.integration.application.service;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.um.catalogsync.integration.domain.ports.in.RefreshIntegrationConfigUseCase;
import com.um.catalogsync.shared.domain.model.Secret;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fachada del slice {@code integration}.
 *
 * <p>Es lo que usan la verificacion de arranque y, mas adelante, los
 * adaptadores que hablan con la catedra. Los otros slices pueden depender de
 * los puertos de entrada directamente —es lo que anuncia el ADR para el JWT
 * tecnico— o de esta fachada; lo que ninguno hace es depender de
 * {@code TechnicalSessionCache} ni de los adaptadores REST.
 *
 * <p>No expone nada por HTTP y el slice no tiene capa {@code web}: la
 * configuracion de integracion y el JWT tecnico no se publican. El estado de
 * sincronizacion, que si es observable, lo expone el slice {@code sync}
 * (#28), y nunca incluye estos valores.
 */
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final GetTechnicalTokenUseCase getTechnicalToken;
    private final GetIntegrationConfigUseCase getIntegrationConfig;
    private final RefreshIntegrationConfigUseCase refreshIntegrationConfig;

    /** El JWT tecnico vigente, para la cabecera {@code Authorization}. */
    public Secret technicalToken() {
        return getTechnicalToken.get();
    }

    /** La configuracion vigente, de la copia en memoria. */
    public IntegrationConfig current() {
        return getIntegrationConfig.get();
    }

    /** Relee la configuracion contra la catedra y reemplaza la copia. */
    public IntegrationConfig refresh() {
        return refreshIntegrationConfig.refresh();
    }
}

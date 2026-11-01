package com.um.catalogsync.integration.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import com.um.catalogsync.shared.domain.model.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetTechnicalTokenUseCaseImplTest {

    @Test
    @DisplayName("Entrega el token de la sesion vigente")
    void entregaElTokenDeLaSesion() {
        TechnicalSessionCache cache = new TechnicalSessionCache(
                IntegrationFixtures::sesion, token -> IntegrationFixtures.provisionada());

        GetTechnicalTokenUseCaseImpl casoDeUso = new GetTechnicalTokenUseCaseImpl(cache);

        assertThat(casoDeUso.get()).isEqualTo(Secret.of(IntegrationFixtures.TOKEN_FALSO));
    }
}

package com.um.catalogsync.integration.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.shared.domain.model.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegrationServiceTest {

    private static final Secret TOKEN = Secret.of(IntegrationFixtures.TOKEN_FALSO);
    private static final IntegrationConfig CONFIG = IntegrationFixtures.provisionada();
    private static final IntegrationConfig REFRESCADA =
            IntegrationFixtures.provisionada();

    private final IntegrationService service =
            new IntegrationService(() -> TOKEN, () -> CONFIG, () -> REFRESCADA);

    @Test
    @DisplayName("Entrega el JWT tecnico del caso de uso")
    void entregaElToken() {
        assertThat(service.technicalToken()).isEqualTo(TOKEN);
    }

    @Test
    @DisplayName("Entrega la configuracion vigente")
    void entregaLaConfiguracion() {
        assertThat(service.current()).isSameAs(CONFIG);
    }

    @Test
    @DisplayName("El refresco usa su propio caso de uso y no el de lectura")
    void elRefrescoEsOtroCamino() {
        assertThat(service.refresh()).isSameAs(REFRESCADA);
    }
}

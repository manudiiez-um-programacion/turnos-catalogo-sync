package com.um.catalogsync.integration.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshIntegrationConfigUseCaseImplTest {

    @Test
    @DisplayName("El refresco si vuelve a leer la configuracion de la catedra")
    void elRefrescoRelee() {
        AtomicInteger lecturas = new AtomicInteger();
        TechnicalSessionCache cache = new TechnicalSessionCache(
                IntegrationFixtures::sesion,
                token -> {
                    lecturas.incrementAndGet();
                    return IntegrationFixtures.provisionada();
                });
        cache.current();

        RefreshIntegrationConfigUseCaseImpl casoDeUso =
                new RefreshIntegrationConfigUseCaseImpl(cache);

        assertThat(casoDeUso.refresh().isProvisioned()).isTrue();
        assertThat(lecturas).hasValue(1);
    }
}

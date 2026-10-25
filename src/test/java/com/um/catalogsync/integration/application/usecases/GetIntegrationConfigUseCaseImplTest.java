package com.um.catalogsync.integration.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.session.TechnicalSessionCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetIntegrationConfigUseCaseImplTest {

    @Test
    @DisplayName("Entrega la configuracion de la copia en memoria, sin ir a la catedra")
    void entregaLaConfiguracionCacheada() {
        AtomicInteger lecturas = new AtomicInteger();
        TechnicalSessionCache cache = new TechnicalSessionCache(
                IntegrationFixtures::sesion,
                token -> {
                    lecturas.incrementAndGet();
                    return IntegrationFixtures.provisionada();
                });

        GetIntegrationConfigUseCaseImpl casoDeUso = new GetIntegrationConfigUseCaseImpl(cache);
        casoDeUso.get();
        casoDeUso.get();

        assertThat(casoDeUso.get().getGroupId()).isEqualTo(IntegrationFixtures.GROUP_ID);
        // La lectura normal no refresca: ese es el otro caso de uso.
        assertThat(lecturas).hasValue(0);
    }
}

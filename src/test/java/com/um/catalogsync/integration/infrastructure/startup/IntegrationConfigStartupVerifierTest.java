package com.um.catalogsync.integration.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.exception.IntegrationNotProvisionedException;
import com.um.catalogsync.integration.application.service.IntegrationService;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.shared.domain.model.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El verificador de arranque.
 *
 * <p>Se prueba a mano y no levantando el contexto porque el perfil
 * {@code test} lo deja apagado a proposito: las pruebas de integracion no
 * tienen la red privada de la catedra. Lo que hay que fijar igual es la
 * unica cosa que esta clase promete —que una integracion inusable hace
 * fracasar el arranque en lugar de dejarlo seguir—, y eso se observa en que
 * la excepcion sale del {@code run} sin ser atrapada: es lo que hace que
 * {@code SpringApplication.run} propague y el proceso termine con error.
 */
class IntegrationConfigStartupVerifierTest {

    private static IntegrationService servicio(GetIntegrationConfigUseCase lectura) {
        return new IntegrationService(
                () -> Secret.of(IntegrationFixtures.TOKEN_FALSO),
                lectura,
                IntegrationFixtures::provisionada);
    }

    @Test
    @DisplayName("Con la cuenta habilitada, el arranque sigue")
    void conLaCuentaHabilitadaNoMolesta() {
        IntegrationConfigStartupVerifier verificador =
                new IntegrationConfigStartupVerifier(servicio(IntegrationFixtures::provisionada));

        assertThatCode(() -> verificador.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Con la cuenta sin habilitar, la excepcion sale y detiene el arranque")
    void conLaCuentaSinHabilitarFallaElArranque() {
        IntegrationConfigStartupVerifier verificador = new IntegrationConfigStartupVerifier(
                servicio(() -> {
                    throw new IntegrationNotProvisionedException(
                            ProvisioningStatus.PENDING, IntegrationFixtures.GROUP_ID);
                }));

        assertThatThrownBy(() -> verificador.run(null))
                .isInstanceOf(IntegrationNotProvisionedException.class)
                .hasMessageContaining("PENDING");
    }
}

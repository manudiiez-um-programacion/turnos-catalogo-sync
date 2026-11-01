package com.um.catalogsync.integration.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProvisioningStatusTest {

    @ParameterizedTest
    @CsvSource({"PENDING,PENDING", "PROVISIONED,PROVISIONED", "FAILED,FAILED", "REVOKED,REVOKED"})
    @DisplayName("Traduce los cuatro estados del contrato")
    void traduceLosCuatroEstados(String delContrato, ProvisioningStatus esperado) {
        assertThat(ProvisioningStatus.fromContract(delContrato)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Un estado desconocido es un error, no un estado nuevo")
    void unEstadoDesconocidoFalla() {
        assertThatThrownBy(() -> ProvisioningStatus.fromContract("ACTIVADA"))
                .isInstanceOf(UnknownProvisioningStatusException.class)
                .hasMessageContaining("\"ACTIVADA\"")
                .hasMessageContaining("PENDING, PROVISIONED, FAILED, REVOKED");
    }

    @Test
    @DisplayName("Las minusculas no pasan: el contrato define el valor exacto")
    void esSensibleAMayusculas() {
        assertThatThrownBy(() -> ProvisioningStatus.fromContract("provisioned"))
                .isInstanceOf(UnknownProvisioningStatusException.class);
    }

    @Test
    @DisplayName("Un estado ausente tambien falla, y guarda el valor recibido")
    void unEstadoNuloFalla() {
        assertThatThrownBy(() -> ProvisioningStatus.fromContract(null))
                .isInstanceOf(UnknownProvisioningStatusException.class)
                .hasMessageContaining("null")
                .extracting(e -> ((UnknownProvisioningStatusException) e).getValorRecibido())
                .isNull();
    }
}

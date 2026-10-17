package com.um.catalogsync.sync.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogVersionServiceTest {

    private static final CatalogVersion VERSION_7 = CatalogVersion.builder()
            .version(7L)
            .appliedAt(Instant.parse("2026-09-10T13:40:00Z"))
            .source(CatalogVersionSource.SNAPSHOT)
            .build();

    @Test
    @DisplayName("getCurrent delega en el caso de uso")
    void delegaEnElCasoDeUso() {
        CatalogVersionService service = new CatalogVersionService(() -> Optional.of(VERSION_7));

        assertThat(service.getCurrent()).contains(VERSION_7);
    }

    @Test
    @DisplayName("El vacio pasa tal cual: una base sin version no es un error")
    void elVacioNoSeTraduceAExcepcion() {
        CatalogVersionService service = new CatalogVersionService(Optional::empty);

        assertThat(service.getCurrent()).isEmpty();
    }
}

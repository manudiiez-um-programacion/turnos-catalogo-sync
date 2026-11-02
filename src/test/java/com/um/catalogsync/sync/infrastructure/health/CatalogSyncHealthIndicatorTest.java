package com.um.catalogsync.sync.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.application.service.CatalogVersionService;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/** Lo que /actuator/health cuenta sobre la copia local. */
class CatalogSyncHealthIndicatorTest {

    private static CatalogSyncHealthIndicator indicador(CatalogVersion aplicada) {
        return new CatalogSyncHealthIndicator(
                new CatalogVersionService(() -> Optional.ofNullable(aplicada)));
    }

    @Test
    @DisplayName("Sin catalogo local informa el estado degradado, pero no reporta caido")
    void sinCatalogoInformaDegradadoSinReportarCaido() {
        Health salud = indicador(null).health();

        assertThat(salud.getDetails()).containsEntry("estado", CatalogSyncHealthIndicator.SIN_CATALOGO);
        // El UP es la mitad que importa del criterio. Un DOWN dejaria el
        // healthcheck del compose en unhealthy para siempre, porque dentro
        // del contenedor la catedra es inalcanzable por diseño (D6), y
        // ademas seria falso: el servicio atiende, valida tokens y puede
        // sincronizar cuando la catedra vuelva.
        assertThat(salud.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("Con catalogo aplicado informa la version con la que se responde")
    void conCatalogoInformaLaVersion() {
        Health salud = indicador(CatalogVersion.builder()
                        .version(42L)
                        .appliedAt(Instant.parse("2026-10-30T12:00:00Z"))
                        .source(CatalogVersionSource.SNAPSHOT)
                        .build())
                .health();

        assertThat(salud.getStatus()).isEqualTo(Status.UP);
        assertThat(salud.getDetails())
                .containsEntry("estado", CatalogSyncHealthIndicator.SINCRONIZADO)
                .containsEntry("catalogVersion", 42L)
                .containsEntry("source", CatalogVersionSource.SNAPSHOT);
    }
}

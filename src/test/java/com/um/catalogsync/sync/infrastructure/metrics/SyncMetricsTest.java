package com.um.catalogsync.sync.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los nombres y las etiquetas de los contadores, que son contrato.
 *
 * <p>No es una prueba de Micrometer: lo que se fija acá es que los nombres y
 * las etiquetas no cambien por descuido. Una metrica renombrada rompe en
 * silencio cualquier tablero o consulta que la use, y a diferencia de un
 * endpoint, nada falla al compilar.
 */
class SyncMetricsTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final SyncMetrics metricas = new SyncMetrics(registro);

    @Test
    @DisplayName("Una corrida exitosa se cuenta por modo, disparador y resultado")
    void cuentaLaCorridaExitosa() {
        metricas.corridaTerminada(corrida(SyncStatus.SUCCEEDED));

        assertThat(registro.get("catalog.sync.runs")
                        .tag("mode", "FULL")
                        .tag("trigger", "STARTUP")
                        .tag("status", "SUCCEEDED")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Una corrida fallida suma en los dos contadores")
    void cuentaLaCorridaFallida() {
        metricas.corridaTerminada(corrida(SyncStatus.FAILED));
        metricas.corridaFallida(error());

        // Son las dos cosas: una corrida que termino y un error. Nadie los
        // suma entre si: uno responde "cuantas sincronizaciones hubo" y el
        // otro "por que fallan".
        assertThat(registro.get("catalog.sync.runs").tag("status", "FAILED").counter().count())
                .isEqualTo(1);
        assertThat(registro.get("catalog.sync.errors")
                        .tag("code", "SNAPSHOT_UNAVAILABLE")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("El codigo del error es una etiqueta aparte y no del contador de corridas")
    void elCodigoNoEtiquetaLasCorridas() {
        metricas.corridaTerminada(corrida(SyncStatus.SUCCEEDED));

        // Un contador lleva siempre el mismo juego de etiquetas: un code en
        // catalog.sync.runs obligaria a inventar un valor para las exitosas.
        assertThat(registro.get("catalog.sync.runs").counter().getId().getTag("code")).isNull();
    }

    @Test
    @DisplayName("Los duplicados se cuentan aparte de los procesados (§11)")
    void cuentaLosDuplicados() {
        metricas.notificacionResuelta(CatalogUpdatedOutcome.PROCESSED);
        metricas.notificacionResuelta(CatalogUpdatedOutcome.DUPLICATE);
        metricas.notificacionResuelta(CatalogUpdatedOutcome.DUPLICATE);

        // catalog.sync.events{outcome=DUPLICATE} es la evidencia de
        // idempotencia que §11 pide: un mensaje repetido que no repitio
        // ningun efecto, contado.
        assertThat(eventos("PROCESSED")).isEqualTo(1);
        assertThat(eventos("DUPLICATE")).isEqualTo(2);
    }

    @Test
    @DisplayName("Dos corridas iguales suman en el mismo contador")
    void noDuplicaContadores() {
        metricas.corridaTerminada(corrida(SyncStatus.SUCCEEDED));
        metricas.corridaTerminada(corrida(SyncStatus.SUCCEEDED));

        assertThat(registro.find("catalog.sync.runs").counters()).hasSize(1);
        assertThat(registro.get("catalog.sync.runs").counter().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un valor que no deberia faltar se etiqueta, no rompe la escritura")
    void elValorImposibleNoRompe() {
        // Los contadores se incrementan dentro de la transaccion que registra
        // la corrida: un NPE acá no perderia una metrica, perderia el
        // registro del intento. Una medicion no puede ser el motivo de que
        // falle lo que mide.
        metricas.corridaTerminada(SyncRun.builder().id(1L).build());

        assertThat(registro.get("catalog.sync.runs")
                        .tag("mode", SyncMetrics.SIN_DATO)
                        .tag("trigger", SyncMetrics.SIN_DATO)
                        .tag("status", SyncMetrics.SIN_DATO)
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un objeto nulo no se cuenta y no rompe")
    void elNuloNoSeCuenta() {
        // No hay nada que medir, y no hay nada que valga romper para
        // avisarlo: estos contadores corren dentro de la transaccion que
        // registra la corrida.
        metricas.corridaTerminada(null);
        metricas.corridaFallida(null);

        assertThat(registro.find("catalog.sync.runs").counters()).isEmpty();
        assertThat(registro.find("catalog.sync.errors").counters()).isEmpty();
    }

    private double eventos(String outcome) {
        return registro.get("catalog.sync.events").tag("outcome", outcome).counter().count();
    }

    private static SyncRun corrida(SyncStatus estado) {
        return SyncRun.builder()
                .id(1L)
                .mode(SyncMode.FULL)
                .triggeredBy(SyncTrigger.STARTUP)
                .status(estado)
                .startedAt(Instant.parse("2026-11-03T18:30:00Z"))
                .build();
    }

    private static SyncError error() {
        return SyncError.builder()
                .syncRunId(1L)
                .occurredAt(Instant.parse("2026-11-03T18:30:01Z"))
                .code(SyncErrorCode.SNAPSHOT_UNAVAILABLE)
                .detail("la catedra no respondio")
                .build();
    }
}

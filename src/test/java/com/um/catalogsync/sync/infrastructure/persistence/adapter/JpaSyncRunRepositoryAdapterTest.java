package com.um.catalogsync.sync.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El historial contra un PostgreSQL real.
 *
 * <p>Lo que no se puede probar con dobles: que los CHECK de la tabla acepten
 * lo que el adaptador escribe, y que las tres operaciones sobrevivan al
 * rollback de la transaccion de quien llama.
 */
class JpaSyncRunRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SyncRunRepository syncRuns;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    private List<Map<String, Object>> filas() {
        return jdbc.queryForList("SELECT * FROM sync_run ORDER BY id");
    }

    private List<Map<String, Object>> errores() {
        return jdbc.queryForList("SELECT * FROM sync_error ORDER BY id");
    }

    @Test
    @DisplayName("El motivo de la decision se guarda y se lee de vuelta")
    void guardaElMotivoDeLaDecision() {
        // Es lo que distingue las tres discontinuidades entre si: con
        // mode = FULL se sabe que se reconstruyo, y con esto se sabe si fue
        // porque la version local salio de la ventana retenida, porque
        // faltaba una version intermedia o porque no se pudo verificar el
        // estado del origen (§6.2, §18.2).
        SyncRun corrida = syncRuns.start(SyncMode.FULL, SyncTrigger.EVENT,
                SyncDecisionReason.VERSION_OUT_OF_WINDOW, 3L);

        assertThat(corrida.getDecisionReason())
                .isEqualTo(SyncDecisionReason.VERSION_OUT_OF_WINDOW);
        assertThat(filas()).singleElement().satisfies(fila ->
                assertThat(fila.get("decision_reason")).isEqualTo("VERSION_OUT_OF_WINDOW"));
    }

    @Test
    @DisplayName("Los seis motivos pasan el CHECK de la migracion")
    void todosLosMotivosSonValidos() {
        // El CHECK de V3 y el enum tienen que decir lo mismo. Si alguien
        // agrega un valor al enum y se olvida de la migracion, el INSERT
        // falla recien en produccion; acá falla en la prueba.
        for (SyncDecisionReason motivo : SyncDecisionReason.values()) {
            syncRuns.start(SyncMode.FULL, SyncTrigger.MANUAL, motivo, null);
        }

        assertThat(filas()).hasSize(SyncDecisionReason.values().length);
    }

    @Test
    @DisplayName("Una corrida sin motivo se acepta: las anteriores a V3 no lo tienen")
    void elMotivoPuedeSerNulo() {
        syncRuns.start(SyncMode.FULL, SyncTrigger.STARTUP, null, null);

        assertThat(filas()).singleElement().satisfies(fila ->
                assertThat(fila.get("decision_reason")).isNull());
    }

    @Test
    @DisplayName("start deja la corrida en RUNNING, sin fin y sin to_version")
    void arranca() {
        SyncRun corrida = syncRuns.start(SyncMode.FULL, SyncTrigger.STARTUP, SyncDecisionReason.REQUESTED, 5L);

        // El CHECK ck_sync_run_finished_at exige RUNNING sin fin, y es lo
        // unico que esta firma permite expresar.
        assertThat(corrida.getId()).isNotNull();
        assertThat(corrida.getStatus()).isEqualTo(SyncStatus.RUNNING);
        assertThat(corrida.getFinishedAt()).isNull();
        assertThat(corrida.getToVersion()).isNull();
        assertThat(corrida.getFromVersion()).isEqualTo(5L);
        assertThat(corrida.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Con base vacia, from_version queda nulo")
    void arrancaSinVersionPrevia() {
        syncRuns.start(SyncMode.FULL, SyncTrigger.STARTUP, SyncDecisionReason.REQUESTED, null);

        assertThat(filas()).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("from_version", null));
    }

    @Test
    @DisplayName("succeed escribe estado, fin y to_version juntos")
    void termina() {
        SyncRun abierta = syncRuns.start(SyncMode.FULL, SyncTrigger.EVENT, SyncDecisionReason.REQUESTED, 5L);

        SyncRun cerrada = syncRuns.succeed(abierta.getId(), 7L);

        assertThat(cerrada.getStatus()).isEqualTo(SyncStatus.SUCCEEDED);
        assertThat(cerrada.getToVersion()).isEqualTo(7L);
        assertThat(cerrada.getFinishedAt()).isNotNull();
        assertThat(cerrada.isFinished()).isTrue();
        // Una sola fila: succeed cierra la que abrio start, no crea otra.
        assertThat(filas()).hasSize(1);
        assertThat(errores()).isEmpty();
    }

    @Test
    @DisplayName("fail cierra la corrida y escribe el motivo en la misma transaccion")
    void falla() {
        SyncRun abierta = syncRuns.start(SyncMode.FULL, SyncTrigger.DISCONTINUITY, SyncDecisionReason.REQUESTED, 5L);

        SyncRun cerrada = syncRuns.fail(
                abierta.getId(), SyncErrorCode.SNAPSHOT_UNAVAILABLE, "la catedra no responde");

        assertThat(cerrada.getStatus()).isEqualTo(SyncStatus.FAILED);
        // Sin to_version: no se llego a ninguna version nueva, que es la
        // regla del proyecto vista desde el historial.
        assertThat(cerrada.getToVersion()).isNull();
        assertThat(cerrada.getFinishedAt()).isNotNull();
        assertThat(errores()).singleElement().satisfies(fila -> {
            assertThat(fila).containsEntry("sync_run_id", abierta.getId());
            assertThat(fila).containsEntry("code", "SNAPSHOT_UNAVAILABLE");
            assertThat(fila).containsEntry("detail", "la catedra no responde");
        });
    }

    @Test
    @DisplayName("Un detalle nulo se acepta: la columna es nulable")
    void fallaSinDetalle() {
        SyncRun abierta = syncRuns.start(SyncMode.FULL, SyncTrigger.MANUAL, SyncDecisionReason.REQUESTED, null);

        syncRuns.fail(abierta.getId(), SyncErrorCode.UNEXPECTED_ERROR, null);

        assertThat(errores()).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("detail", null));
    }

    @Test
    @DisplayName("Los cinco motivos y los tres modos pasan los CHECK de la tabla")
    void todosLosValoresSonValidos() {
        for (SyncTrigger trigger : SyncTrigger.values()) {
            for (SyncMode mode : SyncMode.values()) {
                syncRuns.start(mode, trigger, SyncDecisionReason.REQUESTED, null);
            }
        }

        // Si un enum del dominio y el CHECK de la migracion se separaran, el
        // sintoma seria una violacion de restriccion en produccion y nada
        // antes. Esta prueba los ata.
        assertThat(filas()).hasSize(SyncTrigger.values().length * SyncMode.values().length);
    }

    @Test
    @DisplayName("Los nueve codigos de error pasan el largo de la columna")
    void todosLosCodigosEntran() {
        SyncRun abierta = syncRuns.start(SyncMode.FULL, SyncTrigger.MANUAL, SyncDecisionReason.REQUESTED, null);

        for (SyncErrorCode code : SyncErrorCode.values()) {
            syncRuns.fail(abierta.getId(), code, "detalle de " + code);
        }

        assertThat(errores()).hasSize(SyncErrorCode.values().length);
    }

    @Test
    @DisplayName("El registro SOBREVIVE al rollback de la transaccion de quien llama")
    void sobreviveAlRollback() {
        var tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(estado -> {
            SyncRun abierta = syncRuns.start(SyncMode.FULL, SyncTrigger.STARTUP, SyncDecisionReason.REQUESTED, null);
            syncRuns.fail(abierta.getId(), SyncErrorCode.CATALOG_INCONSISTENT, "se contradice");
            throw new IllegalStateException("la unidad de trabajo falla despues");
        })).isInstanceOf(IllegalStateException.class);

        // Es el punto de REQUIRES_NEW, y lo contrario de lo que hace el
        // avance de version: si el historial se fuera con el rollback,
        // perderia justamente las corridas fallidas.
        assertThat(filas()).singleElement()
                .satisfies(fila -> assertThat(fila).containsEntry("status", "FAILED"));
        assertThat(errores()).hasSize(1);
    }

    @Test
    @DisplayName("Cerrar una corrida que no existe es un error de programacion")
    void cerrarUnaCorridaInexistente() {
        assertThatThrownBy(() -> syncRuns.succeed(404L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("404");
    }
}

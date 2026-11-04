package com.um.catalogsync.sync.infrastructure.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La ventana de retencion del historial y la tolerancia a fallas de la purga.
 *
 * <p>No hace falta base: lo que decide esta clase es <b>que instante</b> le
 * pasa al puerto y <b>que hace si falla</b>. Que el borrado respete el limite y
 * conserve la corrida mas reciente lo prueba
 * {@code JpaSyncRunRepositoryAdapterTest} contra PostgreSQL.
 */
class SyncHistoryRetentionJobTest {

    private final PurgasRegistradas corridas = new PurgasRegistradas();

    @Test
    @DisplayName("Purga con el limite corrido por la ventana de retencion")
    void elLimiteEsAhoraMenosLaRetencion() {
        Instant antes = Instant.now();
        job(Duration.ofDays(90)).purgar();

        assertThat(corridas.limites).hasSize(1);
        assertThat(corridas.limites.getFirst())
                .isBetween(antes.minus(Duration.ofDays(90)).minusSeconds(5),
                        Instant.now().minus(Duration.ofDays(90)));
    }

    @Test
    @DisplayName("Si la purga falla, el trabajo no propaga la excepcion")
    void laFallaNoSePropaga() {
        // La invoca el planificador y no hay a quien avisarle. Lo peor que
        // pasa es que la tabla quede un ciclo mas grande.
        SyncHistoryRetentionJob conBaseCaida = new SyncHistoryRetentionJob(
                new PurgaQueFalla(),
                new SyncHistoryRetentionProperties(Duration.ofDays(90), Duration.ofHours(6)));

        assertThatCode(conBaseCaida::purgar).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una retencion no positiva no se puede configurar")
    void retencionInvalida() {
        // En cero borraria el historial terminado entero en cada corrida, y
        // el reporte de estado se quedaria sin errores que informar justo
        // cuando mas valen. La propiedad lo rechaza al construir, asi que el
        // arranque falla nombrandola.
        assertThatThrownBy(() -> new SyncHistoryRetentionProperties(
                        Duration.ZERO, Duration.ofHours(6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sync.history.retention");
    }

    @Test
    @DisplayName("Un intervalo de purga no positivo no se puede configurar")
    void intervaloInvalido() {
        assertThatThrownBy(() -> new SyncHistoryRetentionProperties(
                        Duration.ofDays(90), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sync.history.purge-interval");
    }

    private SyncHistoryRetentionJob job(Duration retencion) {
        return new SyncHistoryRetentionJob(
                corridas, new SyncHistoryRetentionProperties(retencion, Duration.ofHours(6)));
    }

    /** Doble que solo anota con que limite se lo llamo. */
    private static class PurgasRegistradas implements SyncRunRepository {

        private final List<Instant> limites = new ArrayList<>();

        @Override
        public int purgeRunsFinishedBefore(Instant limite) {
            limites.add(limite);
            return 0;
        }

        @Override
        public SyncRun start(SyncMode mode, SyncTrigger trigger, SyncDecisionReason reason,
                Long fromVersion) {
            throw new AssertionError("La purga no abre corridas");
        }

        @Override
        public SyncRun succeed(long runId, long toVersion) {
            throw new AssertionError("La purga no cierra corridas");
        }

        @Override
        public SyncRun fail(long runId, SyncErrorCode code, String detail) {
            throw new AssertionError("La purga no registra errores");
        }

        @Override
        public Optional<SyncRun> findLast() {
            throw new AssertionError("La purga no lee el historial");
        }

        @Override
        public List<SyncError> findRecentErrors(int limit) {
            throw new AssertionError("La purga no lee los errores");
        }
    }

    private static final class PurgaQueFalla extends PurgasRegistradas {

        @Override
        public int purgeRunsFinishedBefore(Instant limite) {
            throw new IllegalStateException("la base no responde");
        }
    }
}

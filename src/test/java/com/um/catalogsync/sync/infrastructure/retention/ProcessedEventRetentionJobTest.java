package com.um.catalogsync.sync.infrastructure.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.ports.out.ProcessedEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La ventana de retencion y la tolerancia a fallas del trabajo de purga.
 *
 * <p>No hace falta base: lo que decide esta clase es <b>que instante</b> le
 * pasa al puerto y <b>que hace si falla</b>. Que el borrado respete el
 * limite lo prueba {@code JpaProcessedEventRepositoryAdapterTest} contra
 * PostgreSQL.
 */
class ProcessedEventRetentionJobTest {

    private final PurgasRegistradas eventos = new PurgasRegistradas();

    @Test
    @DisplayName("Purga con el limite corrido por la ventana de retencion")
    void elLimiteEsAhoraMenosLaRetencion() {
        Instant antes = Instant.now();
        job(Duration.ofDays(30)).purgar();

        assertThat(eventos.limites).hasSize(1);
        // El limite tiene que caer treinta dias atras, no en "ahora": con el
        // limite en el presente se borraria la memoria de idempotencia
        // entera en cada corrida y un duplicado se volveria a procesar.
        assertThat(eventos.limites.getFirst())
                .isBetween(antes.minus(Duration.ofDays(30)).minusSeconds(5),
                        Instant.now().minus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("Una ventana mas corta corre el limite hacia el presente")
    void laVentanaDefineElLimite() {
        job(Duration.ofHours(1)).purgar();

        assertThat(eventos.limites.getFirst())
                .isAfter(Instant.now().minus(Duration.ofHours(1)).minusSeconds(5))
                .isBefore(Instant.now());
    }

    @Test
    @DisplayName("Si la purga falla, el trabajo no propaga la excepcion")
    void laFallaNoSePropaga() {
        // La invoca el planificador y no hay a quien avisarle. Una purga que
        // no corrio no rompe nada: el evento sigue siendo deduplicable, que
        // es lo unico de lo que depende la correccion.
        ProcessedEventRetentionJob conBaseCaida = new ProcessedEventRetentionJob(
                new PurgaQueFalla(),
                new ProcessedEventRetentionProperties(Duration.ofDays(30), Duration.ofHours(6)));

        assertThatCode(conBaseCaida::purgar).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una retencion no positiva no se puede configurar")
    void retencionInvalida() {
        // En cero borraria todo en cada corrida. La propiedad lo rechaza al
        // construir, asi que el arranque falla nombrandola en lugar de
        // dejar un servicio que pierde la deduplicacion en silencio.
        assertThatThrownBy(() -> new ProcessedEventRetentionProperties(
                        Duration.ZERO, Duration.ofHours(6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sync.processed-events.retention");
    }

    @Test
    @DisplayName("Un intervalo de purga no positivo no se puede configurar")
    void intervaloInvalido() {
        assertThatThrownBy(() -> new ProcessedEventRetentionProperties(
                        Duration.ofDays(30), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sync.processed-events.purge-interval");
    }

    private ProcessedEventRetentionJob job(Duration retencion) {
        return new ProcessedEventRetentionJob(
                eventos,
                new ProcessedEventRetentionProperties(retencion, Duration.ofHours(6)));
    }

    private static final class PurgasRegistradas implements ProcessedEventRepository {

        private final List<Instant> limites = new ArrayList<>();

        @Override
        public boolean recordIfNew(CatalogUpdatedEvent evento) {
            return true;
        }

        @Override
        public int purgeProcessedBefore(Instant limite) {
            limites.add(limite);
            return 0;
        }
    }

    private static final class PurgaQueFalla implements ProcessedEventRepository {

        @Override
        public boolean recordIfNew(CatalogUpdatedEvent evento) {
            return true;
        }

        @Override
        public int purgeProcessedBefore(Instant limite) {
            throw new IllegalStateException("la base no responde");
        }
    }
}

package com.um.catalogsync.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;
import com.um.catalogsync.sync.domain.ports.out.ProcessedEventRepository;
import com.um.catalogsync.sync.infrastructure.messaging.CatalogUpdatedFixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La deduplicacion, con un doble del unico puerto que el caso de uso tiene.
 *
 * <p>El doble se comporta como la tabla: la primera vez que ve un
 * {@code eventId} lo registra y devuelve {@code true}, y despues
 * {@code false}. Eso alcanza para probar la decision, que es de este caso de
 * uso; que la tabla de verdad se comporte asi lo prueba
 * {@code JpaProcessedEventRepositoryAdapterTest} contra PostgreSQL.
 */
class HandleCatalogUpdatedUseCaseImplTest {

    private final EventosEnMemoria eventos = new EventosEnMemoria();
    private final HandleCatalogUpdatedUseCaseImpl useCase =
            new HandleCatalogUpdatedUseCaseImpl(eventos);

    @Test
    @DisplayName("Un evento nuevo se procesa y queda registrado")
    void eventoNuevo() {
        CatalogUpdatedOutcome resultado = useCase.handle(CatalogUpdatedFixtures.evento());

        assertThat(resultado).isEqualTo(CatalogUpdatedOutcome.PROCESSED);
        assertThat(eventos.registrados).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
    }

    @Test
    @DisplayName("El mismo eventId dos veces no repite ningun efecto (§18.1)")
    void eventoDuplicado() {
        CatalogUpdatedEvent evento = CatalogUpdatedFixtures.evento();

        assertThat(useCase.handle(evento)).isEqualTo(CatalogUpdatedOutcome.PROCESSED);
        assertThat(useCase.handle(evento)).isEqualTo(CatalogUpdatedOutcome.DUPLICATE);

        // El criterio de §18.1 es "el estado final permanece igual": una sola
        // registracion, no dos.
        assertThat(eventos.registrados).containsExactly(CatalogUpdatedFixtures.EVENT_ID);
    }

    @Test
    @DisplayName("Dos eventos distintos de la misma version se procesan los dos")
    void dosEventosDeLaMismaVersion() {
        // La idempotencia es por eventId y no por version (§15.1): la
        // catedra puede publicar un aviso manual y otro programado para la
        // misma version. Deduplicar por version descartaria una
        // notificacion legitima.
        useCase.handle(CatalogUpdatedFixtures.evento("primero", 7L));
        CatalogUpdatedOutcome segundo = useCase.handle(CatalogUpdatedFixtures.evento("segundo", 7L));

        assertThat(segundo).isEqualTo(CatalogUpdatedOutcome.PROCESSED);
        assertThat(eventos.registrados).containsExactly("primero", "segundo");
    }

    @Test
    @DisplayName("Si el registro falla, la excepcion sale: el offset no se puede confirmar")
    void siElRegistroFallaNoSeTragaLaFalla() {
        // Es la mitad del contrato de §16 que depende de este lado: una
        // falla no puede volver como un resultado, porque el listener
        // confirmaria el offset y la notificacion se perderia.
        HandleCatalogUpdatedUseCaseImpl conBaseCaida =
                new HandleCatalogUpdatedUseCaseImpl(new EventosQueFallan());

        assertThatThrownBy(() -> conBaseCaida.handle(CatalogUpdatedFixtures.evento()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Se comporta como la clave primaria de {@code processed_event}. */
    private static final class EventosEnMemoria implements ProcessedEventRepository {

        private final Set<String> vistos = new LinkedHashSet<>();
        private final List<String> registrados = new ArrayList<>();

        @Override
        public boolean recordIfNew(CatalogUpdatedEvent evento) {
            boolean nuevo = vistos.add(evento.getEventId());
            if (nuevo) {
                registrados.add(evento.getEventId());
            }
            return nuevo;
        }

        @Override
        public int purgeProcessedBefore(Instant limite) {
            return 0;
        }
    }

    private static final class EventosQueFallan implements ProcessedEventRepository {

        @Override
        public boolean recordIfNew(CatalogUpdatedEvent evento) {
            throw new IllegalStateException("la base no responde");
        }

        @Override
        public int purgeProcessedBefore(Instant limite) {
            return 0;
        }
    }
}

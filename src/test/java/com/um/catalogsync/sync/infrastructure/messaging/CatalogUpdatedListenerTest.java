package com.um.catalogsync.sync.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;
import com.um.catalogsync.sync.domain.ports.in.HandleCatalogUpdatedUseCase;
import com.um.catalogsync.sync.infrastructure.messaging.mapper.CatalogUpdatedMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

/**
 * El orden de "procesar, persistir, confirmar" y el salteo del mensaje
 * envenenado, sin broker.
 *
 * <p>Lo que se prueba acá son decisiones del listener, no del protocolo:
 * cuando confirma el offset y cuando no. Se puede verificar con un
 * {@code ConsumerRecord} armado a mano y un doble de
 * {@link Acknowledgment} que registra las llamadas, y eso lo deja a la vista
 * mejor que un broker real: la prueba con Kafka de verdad
 * —{@code CatalogUpdatedConsumerIntegrationTest}— verifica lo otro, que el
 * offset confirmado sobreviva a un consumidor nuevo.
 */
class CatalogUpdatedListenerTest {

    private final OffsetQueRegistra offset = new OffsetQueRegistra();
    private final CasoDeUsoQueRegistra casoDeUso = new CasoDeUsoQueRegistra();

    private final CatalogUpdatedListener listener = new CatalogUpdatedListener(
            new CatalogUpdatedMapper(new ObjectMapper()), casoDeUso);

    @Test
    @DisplayName("Procesa el mensaje y despues confirma el offset")
    void procesaYConfirma() {
        listener.onMessage(mensaje(CatalogUpdatedFixtures.mensaje()), offset);

        // El orden es el requisito de §16: el caso de uso es transaccional,
        // asi que cuando se confirma el offset lo que el mensaje provoco ya
        // esta commiteado.
        assertThat(casoDeUso.recibidos).hasSize(1);
        assertThat(offset.confirmaciones).isEqualTo(1);
        assertThat(casoDeUso.recibidos.getFirst().getEventId())
                .isEqualTo(CatalogUpdatedFixtures.EVENT_ID);
    }

    @Test
    @DisplayName("Un duplicado tambien confirma el offset (§18.1)")
    void elDuplicadoConfirma() {
        casoDeUso.resultado = CatalogUpdatedOutcome.DUPLICATE;

        listener.onMessage(mensaje(CatalogUpdatedFixtures.mensaje()), offset);

        // §18.1 lo dice explicitamente: el consumidor reconoce que ya fue
        // procesado y puede confirmar. No confirmarlo dejaria el mismo
        // mensaje reentregandose para siempre.
        assertThat(offset.confirmaciones).isEqualTo(1);
    }

    @Test
    @DisplayName("Un mensaje que no se entiende se saltea confirmando el offset")
    void elMensajeEnvenenadoSeSaltea() {
        listener.onMessage(mensaje("{esto no es json"), offset);

        // Reintentarlo no lo va a arreglar: la particion quedaria trabada en
        // el para siempre, y con ella todas las notificaciones posteriores.
        // Se confirma para avanzar, y el ERROR del log es lo que queda.
        assertThat(offset.confirmaciones).isEqualTo(1);
        assertThat(casoDeUso.recibidos).isEmpty();
    }

    @Test
    @DisplayName("Una falla transitoria no confirma el offset y se propaga")
    void laFallaTransitoriaNoConfirma() {
        casoDeUso.falla = new IllegalStateException("la base no responde");

        assertThatThrownBy(() -> listener.onMessage(mensaje(CatalogUpdatedFixtures.mensaje()),
                        offset))
                .isInstanceOf(IllegalStateException.class);

        // Es la mitad que importa de §16: sin confirmacion, Kafka reentrega
        // el mensaje. Confirmar acá perderia la notificacion en silencio.
        assertThat(offset.confirmaciones).isZero();
    }

    private ConsumerRecord<String, String> mensaje(String valor) {
        return new ConsumerRecord<>(CatalogUpdatedFixtures.TOPIC, 0, 42L, "7", valor);
    }

    private static final class OffsetQueRegistra implements Acknowledgment {

        private int confirmaciones;

        @Override
        public void acknowledge() {
            confirmaciones++;
        }
    }

    private static final class CasoDeUsoQueRegistra implements HandleCatalogUpdatedUseCase {

        private final List<CatalogUpdatedEvent> recibidos = new ArrayList<>();
        private CatalogUpdatedOutcome resultado = CatalogUpdatedOutcome.PROCESSED;
        private RuntimeException falla;

        @Override
        public CatalogUpdatedOutcome handle(CatalogUpdatedEvent evento) {
            if (falla != null) {
                throw falla;
            }
            recibidos.add(evento);
            return resultado;
        }
    }
}

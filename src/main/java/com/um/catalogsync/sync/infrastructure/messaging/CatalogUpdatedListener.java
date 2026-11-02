package com.um.catalogsync.sync.infrastructure.messaging;

import com.um.catalogsync.sync.application.exception.MalformedCatalogEventException;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;
import com.um.catalogsync.sync.domain.ports.in.HandleCatalogUpdatedUseCase;
import com.um.catalogsync.sync.infrastructure.messaging.mapper.CatalogUpdatedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Lo que pasa con cada mensaje del topic de catalogo.
 *
 * <p><b>Traduce y delega: no sincroniza nada.</b> Las tres lineas del camino
 * feliz son mapear, llamar al caso de uso y confirmar el offset. Decidir que
 * hacer con la version nueva es de {@code sync} y no de este adaptador, que
 * es lo que permite probar el reproceso sin broker y cambiar el transporte
 * sin tocar la sincronizacion.
 *
 * <p><b>El orden es el del contrato: procesar, persistir, confirmar.</b> El
 * {@code acknowledge()} va despues de que el caso de uso volvio, y el caso
 * de uso es transaccional: cuando se confirma el offset, lo que el mensaje
 * provoco ya esta commiteado (§16). Si el servicio cae entre las dos cosas,
 * el mensaje se vuelve a entregar y la deduplicacion lo absorbe (§18.1). La
 * ventana existe a proposito y es la unica que el contrato permite: la
 * contraria —confirmar antes de persistir— perderia notificaciones sin
 * dejar rastro.
 *
 * <p><b>Un duplicado tambien confirma el offset.</b> Es lo que pide §18.1
 * explicitamente: el consumidor reconoce que ya fue procesado, no repite
 * efectos y <i>puede confirmar</i>. No confirmarlo dejaria el mismo mensaje
 * reentregandose para siempre.
 *
 * <p><b>Las dos clases de falla se tratan distinto, y es la decision mas
 * importante de esta clase.</b> Un mensaje que no cumple §15.3 no se va a
 * arreglar en el proximo intento: reintentarlo dejaria la particion trabada
 * en el para siempre, y con ella todas las notificaciones que vengan
 * despues. Se lo saltea dejando constancia con su ubicacion exacta. Lo
 * demas —la base caida, Redis inalcanzable— es transitorio: la excepcion se
 * propaga, el offset no se confirma y el contenedor lo vuelve a intentar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogUpdatedListener implements AcknowledgingMessageListener<String, String> {

    private final CatalogUpdatedMapper mapper;
    private final HandleCatalogUpdatedUseCase handleCatalogUpdated;

    @Override
    public void onMessage(ConsumerRecord<String, String> mensaje, Acknowledgment offset) {
        String ubicacion = ubicacionDe(mensaje);
        CatalogUpdatedEvent evento;
        try {
            evento = mapper.toEvent(ubicacion, mensaje.value());
        } catch (MalformedCatalogEventException noSeEntiende) {
            // Mensaje envenenado: se saltea confirmando el offset, porque
            // reintentarlo bloquearia la particion indefinidamente. Queda en
            // ERROR con la ubicacion exacta, que es lo que hace falta para
            // ir a buscarlo al topic.
            log.error("Se saltea el mensaje en {}: {}", ubicacion, noSeEntiende.getMessage());
            offset.acknowledge();
            return;
        }

        // Si esto falla, la excepcion se propaga sin confirmar: el mensaje
        // se vuelve a entregar, que es lo correcto para una falla
        // transitoria.
        CatalogUpdatedOutcome resultado = handleCatalogUpdated.handle(evento);

        offset.acknowledge();
        log.debug("Offset confirmado para {} con resultado {}", ubicacion, resultado);
    }

    /**
     * Topic, particion y offset: las tres coordenadas con las que un mensaje
     * se encuentra del otro lado.
     *
     * <p>La message key tambien va, porque en este topic es la version como
     * texto (§15.1) y suele ser lo primero que se quiere saber.
     */
    private static String ubicacionDe(ConsumerRecord<String, String> mensaje) {
        return mensaje.topic() + "-" + mensaje.partition() + "@" + mensaje.offset()
                + " (key=" + mensaje.key() + ")";
    }
}

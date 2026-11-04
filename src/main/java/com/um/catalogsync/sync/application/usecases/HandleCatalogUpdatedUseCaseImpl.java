package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.HandleCatalogUpdatedUseCase;
import com.um.catalogsync.sync.domain.ports.in.SynchronizeCatalogUseCase;
import com.um.catalogsync.sync.domain.ports.out.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Deduplica la notificacion y, si es nueva, pone la copia local al dia.
 *
 * <p><b>El duplicado no es un error y no se registra como tal.</b> La entrega
 * es al menos una vez (§15.1): va a pasar cada vez que el servicio caiga
 * entre persistir y confirmar el offset. Loguearlo como error llenaria el log
 * de alarmas por el comportamiento esperado.
 *
 * <p><b>El registro del evento commitea antes de sincronizar, y no puede ser
 * de otra manera.</b> Sincronizar no es una escritura: es leer Redis, leer
 * entidades y aplicar una transaccion <i>por version</i> (#25), o pedir el
 * snapshot por REST. Nada de eso cabe en la transaccion del evento —serian
 * minutos de locks sobre las tres tablas— y envolverlo convertiria un atraso
 * de diez versiones en una sola unidad de trabajo, que es justo lo que #25
 * evita.
 *
 * <p>La consecuencia hay que decirla: si sincronizar falla, el
 * {@code eventId} ya quedo registrado y una reentrega de ese mensaje se va a
 * reconocer como duplicado. La notificacion no se reintenta. <b>Eso es
 * correcto y esta previsto</b>: §16 dice que Kafka es una notificacion y no
 * la fuente del catalogo, y que si una notificacion se pierde la comparacion
 * de versiones tiene que permitir detectar y recuperar el atraso. El que
 * recupera es el chequeo periodico (#27), que no depende de que llegue
 * ningun evento. Lo contrario —no registrar el evento hasta sincronizar—
 * dejaria el mensaje reentregandose contra una catedra caida, y el atraso
 * igual lo tendria que recuperar la comparacion de versiones.
 *
 * <p><b>Por eso la falla de sincronizacion no se propaga.</b> Si se
 * propagara, el consumidor no confirmaria el offset y reentregaria un mensaje
 * que ya esta registrado: el unico efecto seria reconocerlo como duplicado y
 * confirmar en el segundo intento. Queda en el log, la corrida fallida queda
 * en {@code sync_run} y el estado lo informa #28.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandleCatalogUpdatedUseCaseImpl implements HandleCatalogUpdatedUseCase {

    private final ProcessedEventRepository eventosProcesados;
    private final SynchronizeCatalogUseCase sincronizacion;

    @Override
    public CatalogUpdatedOutcome handle(CatalogUpdatedEvent evento) {
        // recordIfNew abre y cierra su propia transaccion: el evento queda
        // registrado antes de que empiece la sincronizacion.
        if (!eventosProcesados.recordIfNew(evento)) {
            log.info("El evento \"{}\" ya estaba procesado: no se repite ningun efecto (§18.1)",
                    evento.getEventId());
            return CatalogUpdatedOutcome.DUPLICATE;
        }

        log.info("La catedra publico la version {} (evento \"{}\", origen {})",
                evento.getNewVersion(), evento.getEventId(), evento.getSource());
        sincronizar(evento);
        return CatalogUpdatedOutcome.PROCESSED;
    }

    /**
     * Sincroniza sin dejar que la falla se escape.
     *
     * <p>No se le pasa la {@code newVersion} del evento: el destino lo
     * decide la comparacion contra {@code current-version} de Redis, que es
     * el dato autoritativo. El aviso puede venir atrasado respecto de lo que
     * la catedra ya publico —o adelantado, si la publicacion en Redis todavia
     * no termino— y en los dos casos lo que importa es el estado del origen,
     * no el numero del mensaje (§15.3: el evento notifica, los datos se leen
     * de Redis o del snapshot).
     */
    private void sincronizar(CatalogUpdatedEvent evento) {
        try {
            sincronizacion.synchronize(SyncTrigger.EVENT)
                    .ifPresentOrElse(
                            corrida -> log.info("El evento \"{}\" dejo la copia local en la"
                                            + " corrida {} ({})",
                                    evento.getEventId(), corrida.getId(), corrida.getMode()),
                            () -> log.info("El evento \"{}\" no requirio trabajo: la copia"
                                            + " local ya estaba al dia",
                                    evento.getEventId()));
        } catch (RuntimeException falla) {
            log.error("El evento \"{}\" quedo registrado pero la sincronizacion fallo: {}."
                            + " El atraso lo recupera el chequeo periodico (§16)",
                    evento.getEventId(), falla.getMessage(), falla);
        }
    }
}

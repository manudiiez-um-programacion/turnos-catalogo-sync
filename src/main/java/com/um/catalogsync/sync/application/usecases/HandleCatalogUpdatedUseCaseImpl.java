package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;
import com.um.catalogsync.sync.domain.ports.in.HandleCatalogUpdatedUseCase;
import com.um.catalogsync.sync.domain.ports.out.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deduplica la notificacion y deja asentado que se la proceso.
 *
 * <p><b>La transaccion es el punto de todo.</b> El registro del evento y los
 * efectos de procesarlo tienen que ser la misma unidad de trabajo: si lo que
 * viene despues falla, el {@code eventId} no puede quedar registrado, porque
 * el reintento lo descartaria como duplicado y los efectos no ocurririan
 * nunca. Por eso {@code @Transactional} esta acá, en el caso de uso, y no en
 * el adaptador de mensajeria: el adaptador confirma el offset <b>despues</b>
 * de que esta transaccion commiteo (§16).
 *
 * <p><b>Lo que hace hoy y lo que va a hacer.</b> Hoy registra el evento y
 * nada mas: aplicar la version es #25 y decidir si hace falta un snapshot es
 * #26. Que el aviso quede registrado ya tiene valor propio —es lo que
 * permite reconocer el duplicado de §18.1— y el orden de los issues no es
 * caprichoso: si la aplicacion de versiones se escribiera antes que la
 * deduplicacion, la primera reentrega la aplicaria dos veces.
 *
 * <p><b>El duplicado no es un error y no se registra como tal.</b> La
 * entrega es al menos una vez (§15.1): un duplicado es operacion normal y va
 * a pasar cada vez que el servicio caiga entre persistir y confirmar el
 * offset. Loguearlo como error llenaria el log de alarmas por el
 * comportamiento esperado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandleCatalogUpdatedUseCaseImpl implements HandleCatalogUpdatedUseCase {

    private final ProcessedEventRepository eventosProcesados;

    @Override
    @Transactional
    public CatalogUpdatedOutcome handle(CatalogUpdatedEvent evento) {
        if (!eventosProcesados.recordIfNew(evento)) {
            log.info("El evento \"{}\" ya estaba procesado: no se repite ningun efecto (§18.1)",
                    evento.getEventId());
            return CatalogUpdatedOutcome.DUPLICATE;
        }

        // El aviso dice que hay una version nueva; los datos se leen de Redis
        // o del snapshot (§15.3). Aplicarla es #25: acá solo queda asentado
        // que el evento se vio, que es lo que hace idempotente al consumidor.
        log.info("La catedra publico la version {} (evento \"{}\", origen {})",
                evento.getNewVersion(), evento.getEventId(), evento.getSource());
        return CatalogUpdatedOutcome.PROCESSED;
    }
}

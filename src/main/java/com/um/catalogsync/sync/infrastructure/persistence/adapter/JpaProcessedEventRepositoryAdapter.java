package com.um.catalogsync.sync.infrastructure.persistence.adapter;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.ports.out.ProcessedEventRepository;
import com.um.catalogsync.sync.infrastructure.persistence.repository.JpaProcessedEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa la memoria de idempotencia con JPA.
 *
 * <p><b>Insertar es la pregunta.</b> {@code recordIfNew} no consulta y
 * despues escribe: inserta y lee cuantas filas cambiaron. Una es nuevo,
 * cero es duplicado. Hacerlo al reves —un {@code existsById} seguido de un
 * {@code save}— dejaria una ventana entre las dos llamadas por la que dos
 * entregas del mismo mensaje pasarian las dos, y es exactamente el caso que
 * la deduplicacion tiene que cubrir. La unicidad no la sostiene este codigo
 * sino la clave primaria de la tabla.
 *
 * <p><b>La propagacion es {@code REQUIRED}, la de por defecto, y es
 * deliberado.</b> Participa de la transaccion del caso de uso para que el
 * registro del evento y los efectos de procesarlo sean la misma unidad de
 * trabajo. Con {@code REQUIRES_NEW} el evento quedaria marcado como
 * procesado aunque lo que vino despues fallara, y el reintento lo
 * descartaria como duplicado: los efectos no ocurririan nunca.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventRepository jpaRepository;

    @Override
    @Transactional
    public boolean recordIfNew(CatalogUpdatedEvent evento) {
        boolean nuevo = jpaRepository.insertIfAbsent(
                evento.getEventId(),
                evento.getEventType(),
                evento.getNewVersion(),
                evento.getOccurredAt(),
                evento.getSource(),
                ahora()) == 1;

        if (!nuevo) {
            // No se registra como error: la entrega al menos una vez hace
            // que esto sea operacion normal (§15.1, §18.1). El detalle va en
            // debug por si hace falta reconstruir una secuencia de
            // reentregas.
            log.debug("El evento \"{}\" ya estaba registrado", evento.getEventId());
        }
        return nuevo;
    }

    @Override
    @Transactional
    public int purgeProcessedBefore(Instant limite) {
        int borrados = jpaRepository.deleteByProcessedAtBefore(limite);
        if (borrados > 0) {
            log.info("Purga de eventos procesados: {} filas anteriores a {}", borrados, limite);
        }
        return borrados;
    }

    /**
     * Cuando procesamos el evento, truncado a microsegundos.
     *
     * <p>El truncado es por el mismo motivo que en la version local:
     * {@code TIMESTAMPTZ} de PostgreSQL tiene resolucion de microsegundos y
     * {@code Instant.now()} puede traer nanosegundos, asi que sin truncar lo
     * que se escribe y lo que se lee despues no serian el mismo valor. Acá
     * ademas importa para la retencion, que compara contra esta columna.
     */
    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

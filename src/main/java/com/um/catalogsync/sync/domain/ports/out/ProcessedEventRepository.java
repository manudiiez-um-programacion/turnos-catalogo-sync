package com.um.catalogsync.sync.domain.ports.out;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import java.time.Instant;

/**
 * Puerto de salida de los eventos ya procesados.
 *
 * <p>Es la memoria que hace idempotente al consumidor. Vive en la base y no
 * en memoria porque la entrega al menos una vez de Kafka sobrevive a los
 * reinicios: una cache en memoria perderia el registro justo en el caso que
 * tiene que cubrir, que es el servicio cayendose entre persistir y confirmar
 * el offset (§16, §18.1).
 */
public interface ProcessedEventRepository {

    /**
     * Registra el evento si es la primera vez que se lo ve.
     *
     * <p><b>Registrar es la operacion que decide.</b> No hay un
     * {@code existsById} seguido de un {@code save}: entre esas dos
     * llamadas, dos consumidores de la misma partición —o el mismo mensaje
     * reentregado mientras el anterior sigue en vuelo— podrian pasar los
     * dos. La clave primaria de {@code processed_event} es el
     * {@code eventId}, asi que el insert que choca <b>es</b> la respuesta
     * "ya estaba".
     *
     * <p><b>Participa de la transaccion de quien llama</b>, igual que el
     * avance de version. Es lo que hace que el registro del evento y los
     * efectos de procesarlo sean la misma unidad de trabajo: si aplicar la
     * version falla, el evento tampoco queda registrado y el reintento lo
     * vuelve a tomar como nuevo. Un {@code REQUIRES_NEW} acá dejaria eventos
     * marcados como procesados cuyos efectos nunca ocurrieron, que es la
     * peor combinacion posible: el reintento los descartaria.
     *
     * @param evento la notificacion a registrar
     * @return {@code true} si el evento era nuevo, {@code false} si el
     *     {@code eventId} ya estaba registrado
     */
    boolean recordIfNew(CatalogUpdatedEvent evento);

    /**
     * Borra los eventos procesados antes de un instante.
     *
     * <p>La tabla crece con cada notificacion y nada la achica sola: sin
     * esto, la memoria de idempotencia seria eterna y la tabla el unico
     * objeto del esquema que no para de crecer. La ventana de retencion es
     * una decision de configuracion, no del contrato: lo unico que tiene que
     * cumplir es ser mas larga que el atraso maximo con el que un duplicado
     * puede llegar.
     *
     * @param limite se borra lo procesado estrictamente antes de este
     *     instante
     * @return cuantas filas se borraron
     */
    int purgeProcessedBefore(Instant limite);
}

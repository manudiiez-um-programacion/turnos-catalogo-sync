package com.um.catalogsync.sync.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La notificacion de que la catedra publico una version nueva, tal como
 * llega por el topic {@code catedra.catalog.{groupId}} (§15.3).
 *
 * <p><b>Es un aviso, no un dato.</b> El evento dice que hay una version
 * nueva y nada mas: los datos se leen de Redis o del snapshot (§15.3, §16).
 * Por eso no hay ninguna entidad acá y {@link #newVersion} es lo unico que
 * importa para decidir. Si el evento trajera el catalogo, un mensaje perdido
 * seria una perdida de datos; asi, es solo un aviso perdido que la
 * comparacion de versiones recupera (#27).
 *
 * <p><b>{@link #eventId} es la clave de idempotencia funcional</b> (§15.1).
 * La entrega es al menos una vez, asi que el mismo evento puede llegar dos
 * veces y el segundo no puede tener efectos. No se deduplica por
 * {@code newVersion} aunque parezca equivalente: la catedra puede publicar
 * dos eventos distintos para la misma version —uno manual y otro
 * programado—, y tratarlos como el mismo descartaria una notificacion
 * legitima.
 *
 * <p><b>{@link #occurredAt} es el reloj de la catedra</b> y no el nuestro.
 * Sirve para diagnosticar —"el aviso tardo diez minutos en procesarse"— no
 * para ordenar: el orden lo da {@code newVersion}, que es el unico numero
 * que la catedra controla de punta a punta.
 *
 * <p>POJO puro, como el resto de los modelos: sin JPA, sin validacion, sin
 * Jackson. El DTO del contrato vive en {@code infrastructure.messaging.dto}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogUpdatedEvent {

    /**
     * El identificador del evento, unico por notificacion (§15.1).
     *
     * <p>Es lo que se guarda en {@code processed_event} y lo que hace que un
     * reproceso no repita efectos (§18.1).
     */
    private String eventId;

    /**
     * El tipo, que para este topic es siempre {@code CatalogUpdated}.
     *
     * <p>Se conserva en lugar de darlo por sentado porque se persiste con el
     * evento: si manana el topic de catalogo publicara un tipo nuevo, la
     * tabla diria cual se proceso y cual no.
     */
    private String eventType;

    /** La version que la catedra acaba de publicar. */
    private long newVersion;

    /** Cuando la catedra genero el aviso. Su reloj, en UTC (§4). */
    private Instant occurredAt;

    /**
     * De donde salio la version, por ejemplo {@code MANUAL} o
     * {@code SCHEDULED} (§15.3).
     *
     * <p>No es un enum: §15.3 da dos ejemplos y no una lista cerrada, asi
     * que un enum convertiria un valor nuevo de la catedra en un error de
     * deserializacion. Se guarda como vino, para diagnostico.
     */
    private String source;
}

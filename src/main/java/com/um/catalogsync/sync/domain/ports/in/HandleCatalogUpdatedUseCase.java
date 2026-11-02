package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;

/**
 * Procesar una notificacion de catalogo nueva (§15.3).
 *
 * <p><b>Es la frontera entre Kafka y la sincronizacion.</b> El adaptador de
 * mensajeria traduce el mensaje y llama acá; de este lado no se sabe que
 * existe Kafka, ni particiones, ni offsets. Esa separacion es lo que permite
 * que el reproceso de #30 se pruebe sin broker y que el aviso pueda llegar
 * manana por otro camino sin tocar nada de lo que sigue.
 *
 * <p><b>Es idempotente por contrato</b>, no por casualidad: llamar dos veces
 * con el mismo {@code eventId} deja el mismo estado final y la segunda vez
 * devuelve {@link CatalogUpdatedOutcome#DUPLICATE} (§15.1, §18.1). Quien
 * implemente esto tiene que garantizarlo en la base y no en memoria, porque
 * la entrega al menos una vez sobrevive a los reinicios y una cache no.
 */
public interface HandleCatalogUpdatedUseCase {

    /**
     * Procesa la notificacion.
     *
     * <p><b>Que una excepcion salga de acá significa "no confirmes el
     * offset".</b> El adaptador no la traduce a un resultado: el mensaje
     * tiene que volver a entregarse, y la deduplicacion se encarga de que el
     * segundo intento no repita nada (§16, §18.1).
     *
     * @param evento la notificacion ya traducida y validada
     * @return si se proceso o si era un duplicado
     */
    CatalogUpdatedOutcome handle(CatalogUpdatedEvent evento);
}

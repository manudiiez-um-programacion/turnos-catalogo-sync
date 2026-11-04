package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionDelta;

/**
 * Aplicar <b>una</b> version incremental, entera o nada.
 *
 * <p>Es a la sincronizacion incremental lo que
 * {@link ApplyCatalogSnapshotUseCase} es a la completa: la unidad de trabajo.
 * O quedan aplicados los cambios de la version y la version local avanza, o
 * no queda nada (§6.2).
 *
 * <p><b>Por que es un puerto aparte del recorrido.</b> Porque la transaccion
 * es por version y no por recorrido. Si las dos cosas vivieran en la misma
 * clase, el bucle llamaria a un metodo {@code @Transactional} de si mismo y
 * el proxy de Spring no se enteraria: las diez versiones terminarian en una
 * sola transaccion gigante, o en ninguna. Separandolas, cada version es su
 * propia unidad y una falla en la septima deja las seis anteriores
 * aplicadas, que es exactamente lo que el enunciado pide.
 *
 * <p><b>Y por que recibe un delta ya resuelto.</b> Las entidades se leen de
 * Redis antes de abrir la transaccion: un {@code HGET} por id sobre ZeroTier
 * son cientos de milisegundos (D6) y hacerlos adentro mantendria los locks
 * de las tres tablas tomados todo ese tiempo. Es la misma razon por la que
 * el snapshot llega por parametro y no se pide acá.
 */
public interface ApplyCatalogVersionUseCase {

    /**
     * Aplica el delta y avanza la version local, en una sola transaccion.
     *
     * <p><b>Es idempotente.</b> Aplicar dos veces el mismo delta deja el
     * mismo estado final: lo que se escribe es el estado vigente de cada
     * entidad —no un incremento— y lo que se borra ya no esta. Es lo que
     * permite que una reentrega de Kafka (§18.1) o un reintento tras un
     * corte no cambien el resultado.
     *
     * @param delta el estado vigente de lo que cambio en una version
     * @return la version local que quedo asentada
     * @throws com.um.catalogsync.sync.application.exception.VersionRegressionException
     *     si la version del delta es anterior a la vigente: el camino
     *     incremental no retrocede, para eso esta el snapshot
     */
    CatalogVersion apply(CatalogVersionDelta delta);
}

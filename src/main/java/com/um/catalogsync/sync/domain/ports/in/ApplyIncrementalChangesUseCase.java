package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;

/**
 * Ponerse al dia aplicando las versiones que faltan, una por una y en orden.
 *
 * <p>Es el camino que §16 describe cuando el historial esta disponible: para
 * cada version pendiente, leer sus cambios, leer el estado vigente de lo que
 * cambio y aplicarlo avanzando la version local. Lo contrario es el snapshot
 * completo, que reemplaza todo de una vez.
 *
 * <p><b>El orden no es una preferencia.</b> Cada version describe una
 * diferencia contra la anterior, asi que saltear una deja un hueco que nada
 * posterior corrige: la copia local quedaria afirmando una version que no
 * tiene. Por eso el recorrido va de {@code versionLocal + 1} hacia adelante
 * sin saltos, y se detiene en la primera version que no puede aplicar.
 *
 * <p><b>No decide la estrategia.</b> Cuando el camino incremental no
 * alcanza, lo informa en el resultado en lugar de pedir un snapshot por su
 * cuenta: elegir entre incremental y completo es de #26, y el chequeo
 * periodico que lo dispara es de #27. Acá se lee, se aplica y se cuenta que
 * paso.
 */
public interface ApplyIncrementalChangesUseCase {

    /**
     * Aplica las versiones que faltan hasta llegar al destino.
     *
     * <p>Arranca en la version local vigente, que lee por su cuenta: es la
     * autoridad sobre que tiene la copia local, y recibirla por parametro
     * abriria la puerta a que quien llama pase una que ya quedo vieja.
     *
     * <p><b>Nunca retrocede.</b> Si el destino es anterior o igual a la
     * version local, no hay nada que hacer y el resultado es
     * {@code UP_TO_DATE}. Una version local mas adelantada que el origen no
     * es un error: puede venir de un snapshot recien aplicado mientras una
     * notificacion vieja esperaba.
     *
     * @param targetVersion la version a la que hay que llegar, normalmente
     *     la {@code currentVersion} que publica la catedra
     * @return desde donde se partio, hasta donde se llego y por que se
     *     detuvo
     */
    IncrementalSyncResult applyUpTo(long targetVersion);
}

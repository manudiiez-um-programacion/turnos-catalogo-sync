package com.um.catalogsync.sync.domain.model;

/**
 * Como termino un intento de ponerse al dia con cambios incrementales.
 *
 * <p>Los cuatro valores son estados del <b>proceso</b>, no errores. Una falla
 * de verdad —Redis caido, la base sin responder, un dato que no cumple el
 * contrato— sale como excepcion y no como valor de retorno: tiene que cortar
 * el recorrido y no confundirse con "llegue hasta donde pude".
 *
 * <p>Dos de ellos son la entrada de la deteccion de discontinuidad (#26), y
 * por eso se distinguen: {@link #MISSING_VERSION} y
 * {@link #NO_LOCAL_VERSION} son los casos en los que el camino incremental
 * no alcanza y hay que pedir el snapshot completo (§6.2, §18.2). Devolverlos
 * en lugar de decidir acá es lo que mantiene separado "leer y aplicar" de
 * "elegir la estrategia".
 */
public enum IncrementalSyncOutcome {

    /** La version local ya estaba en el destino o mas adelante. */
    UP_TO_DATE,

    /** Se aplicaron todas las versiones hasta el destino. */
    CAUGHT_UP,

    /**
     * Falta la clave de cambios de una version del rango.
     *
     * <p>Las versiones anteriores quedaron aplicadas y la version local esta
     * en la ultima completa. Seguir no es posible: saltear la version
     * faltante dejaria un hueco silencioso en la copia local (§18.2).
     */
    MISSING_VERSION,

    /**
     * No hay version local, asi que no hay desde donde avanzar.
     *
     * <p>Es una base recien inicializada. No es un error ni un atraso: es
     * que el camino incremental no aplica, porque los cambios describen
     * diferencias contra un estado que no existe.
     */
    NO_LOCAL_VERSION
}

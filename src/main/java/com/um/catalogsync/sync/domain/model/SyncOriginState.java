package com.um.catalogsync.sync.domain.model;

/**
 * Que se sabe del origen en el momento en que se armo el reporte de estado.
 *
 * <p>Existe porque "no hay version remota" tiene dos causas que piden
 * reacciones distintas, y un {@code null} en el numero de version las
 * confundiria en una sola. Quien consulta el estado necesita saber cual de
 * las dos es: una se arregla cuando vuelve la red privada y la otra cuando
 * la catedra publique algo.
 *
 * <p>No se persiste: es lo que se vio en una lectura, no un hecho del
 * historial. La lectura siguiente puede dar otro valor y eso no es una
 * inconsistencia.
 */
public enum SyncOriginState {

    /**
     * Se leyo la metadata de la catedra y trae su version publicada.
     *
     * <p>Es el unico valor con el que el atraso se puede calcular: sin la
     * version del origen no hay con que comparar la local.
     */
    KNOWN,

    /**
     * El Redis de la catedra respondio, pero no hay metadata publicada.
     *
     * <p>No es una falla de integracion —la conexion funciono— pero tampoco
     * deja verificar el estado local: no hay version a la que ponerse al dia.
     * Es el mismo caso que {@code SynchronizeCatalogUseCaseImpl} resuelve sin
     * sincronizar nada.
     */
    NOT_PUBLISHED,

    /**
     * No se pudo leer el estado del origen.
     *
     * <p>Redis no respondio, o respondio algo que no cumple §14.3. Es la
     * situacion que §6.2 llama "el estado local no se puede verificar", y la
     * que lleva el estado a {@link SyncHealth#DEGRADED}: la copia local sigue
     * sirviendo, pero no hay forma de saber cuan vieja es.
     */
    UNREACHABLE
}

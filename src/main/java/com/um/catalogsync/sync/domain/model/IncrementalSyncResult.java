package com.um.catalogsync.sync.domain.model;

/**
 * Que paso al intentar ponerse al dia con cambios incrementales.
 *
 * <p>Describe el recorrido completo, no una version: desde donde se partio,
 * hasta donde se llego y por que se detuvo. Es lo que necesitan la
 * reconciliacion (#27) para saber si hace falta otra pasada, la deteccion de
 * discontinuidad (#26) para decidir si cae al snapshot, y el estado de
 * sincronizacion (#28) para informar.
 *
 * <p><b>{@code toVersion} es la ultima version aplicada completa</b>, no la
 * que se pedia. Cuando el recorrido se detiene a mitad de camino, la
 * diferencia entre las dos es exactamente lo que falta, y por eso los dos
 * numeros viajan juntos.
 *
 * @param fromVersion la version local al empezar, o {@code null} si no habia
 * @param toVersion la version local al terminar, o {@code null} si no habia
 *     ninguna y no se aplico nada
 * @param targetVersion hasta donde se queria llegar
 * @param versionsApplied cuantas versiones se aplicaron completas
 * @param outcome por que se detuvo el recorrido
 */
public record IncrementalSyncResult(
        Long fromVersion,
        Long toVersion,
        long targetVersion,
        int versionsApplied,
        IncrementalSyncOutcome outcome) {

    /** Si la copia local quedo en la version que se pedia. */
    public boolean reachedTarget() {
        return outcome == IncrementalSyncOutcome.UP_TO_DATE
                || outcome == IncrementalSyncOutcome.CAUGHT_UP;
    }

    /**
     * Si hace falta reconstruir con un snapshot completo.
     *
     * <p>Vive acá y no en quien lo consulta porque la pregunta se repite en
     * #26 y #27, y la respuesta es la misma: el camino incremental no puede
     * cerrar el hueco. Las dos razones son distintas —falta una version
     * retenida, o no hay version local— pero la salida es la unica que §6.2
     * deja.
     */
    public boolean needsFullSync() {
        return outcome == IncrementalSyncOutcome.MISSING_VERSION
                || outcome == IncrementalSyncOutcome.NO_LOCAL_VERSION;
    }
}

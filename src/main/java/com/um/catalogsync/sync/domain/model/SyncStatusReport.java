package com.um.catalogsync.sync.domain.model;

import java.util.List;

/**
 * El estado de la sincronizacion, armado en el momento en que se pregunta.
 *
 * <p>Es lo que §4.1 pide informar como ultima responsabilidad del servicio, y
 * lo que hace demostrable todo lo demas sin leer un log: que version tiene la
 * copia local, que version publica la catedra, cuanto falta, como salio el
 * ultimo intento y que errores hubo.
 *
 * <p><b>Es una foto, no una entidad.</b> No se persiste y no tiene id: se
 * compone de tres lecturas —la version local, el historial y el origen— que
 * ya viven cada una en su lugar. Dos reportes seguidos pueden no ser iguales
 * y eso no es una inconsistencia, es el estado cambiando.
 *
 * <p><b>El estado derivado se calcula acá y no en quien lo muestra.</b>
 * {@link #health()} y {@link #versionsBehind()} son reglas —cuando una copia
 * local esta al dia, cuanto le falta— y no formato. Si vivieran en el
 * controlador o en el mapper web, el endpoint REST y cualquier otra forma de
 * informar el estado podrian contestar distinto sobre los mismos datos.
 *
 * @param localVersion la version aplicada, o {@code null} si la base esta
 *     recien inicializada y todavia no se aplico ningun catalogo
 * @param lastRun el ultimo intento de sincronizacion, o {@code null} si
 *     nunca se intento ninguno
 * @param originState que se sabe del origen en esta lectura
 * @param origin la metadata del origen, o {@code null} si
 *     {@code originState} no es {@link SyncOriginState#KNOWN}
 * @param recentErrors los ultimos errores registrados, del mas nuevo al mas
 *     viejo. Vacia si no hubo ninguno
 */
public record SyncStatusReport(
        CatalogVersion localVersion,
        SyncRun lastRun,
        SyncOriginState originState,
        PublishedCatalogMetadata origin,
        List<SyncError> recentErrors) {

    public SyncStatusReport {
        recentErrors = recentErrors == null ? List.of() : List.copyOf(recentErrors);
    }

    /**
     * En que situacion esta la sincronizacion.
     *
     * <p><b>El orden de las preguntas es la decision de este metodo</b>, no un
     * detalle de implementacion:
     *
     * <ol>
     *   <li><b>Hay una corrida abierta</b> → {@link SyncHealth#REBUILDING}.
     *       Va primero porque mientras dura, cualquier comparacion de
     *       versiones describe un estado que esta cambiando: decir "atrasado"
     *       de algo que se esta poniendo al dia en este momento seria cierto y
     *       inutil.
     *   <li><b>No se sabe la version del origen</b> →
     *       {@link SyncHealth#DEGRADED}. Es "el estado local no se puede
     *       verificar" de §6.2. No alcanza con que la copia local exista: sin
     *       la version del origen no hay forma de saber cuan vieja es.
     *   <li><b>El ultimo intento fallo</b> → {@link SyncHealth#DEGRADED},
     *       aunque el origen se lea bien. Que Redis conteste no significa que
     *       la sincronizacion funcione: el snapshot va por REST y la version
     *       local puede estar trabada por un 401 de la cuenta tecnica.
     *   <li><b>No hay version local</b> → {@link SyncHealth#BEHIND}. El
     *       origen se leyo, nada fallo todavia y la copia local esta vacia:
     *       falta la primera sincronizacion, y la trae el chequeo periodico
     *       sin que nadie haga nada.
     *   <li><b>La version local cubre la del origen</b> →
     *       {@link SyncHealth#UP_TO_DATE}; si no, {@link SyncHealth#BEHIND}.
     * </ol>
     *
     * <p>Las comparaciones usan {@code >=} y no {@code ==} por lo mismo que
     * {@code SynchronizeCatalogUseCaseImpl}: una version local por encima de
     * la publicada no es un atraso negativo, es que la catedra publico el
     * numero en dos claves y las leimos en momentos distintos (§14.3).
     */
    public SyncHealth health() {
        if (lastRun != null && lastRun.isRunning()) {
            return SyncHealth.REBUILDING;
        }
        if (originState != SyncOriginState.KNOWN) {
            return SyncHealth.DEGRADED;
        }
        if (lastRun != null && lastRun.getStatus() == SyncStatus.FAILED) {
            return SyncHealth.DEGRADED;
        }
        if (localVersion == null) {
            return SyncHealth.BEHIND;
        }
        return localVersion.getVersion() >= origin.getCurrentVersion()
                ? SyncHealth.UP_TO_DATE
                : SyncHealth.BEHIND;
    }

    /**
     * Cuantas versiones le faltan a la copia local para alcanzar al origen.
     *
     * <p><b>{@code null} cuando no se puede calcular</b>, y los dos casos en
     * que eso pasa son informacion y no huecos: sin version del origen no hay
     * con que comparar, y sin version local no hay desde donde contar. Un
     * cero en su lugar seria una mentira comoda —"no falta nada"— sobre una
     * copia local que puede estar vacia.
     *
     * <p>Nunca negativo: ver la nota de {@link #health()} sobre las dos
     * lecturas del mismo numero.
     *
     * @return el atraso en versiones, o {@code null} si no se puede calcular
     */
    public Long versionsBehind() {
        if (originState != SyncOriginState.KNOWN || localVersion == null) {
            return null;
        }
        return Math.max(0, origin.getCurrentVersion() - localVersion.getVersion());
    }
}

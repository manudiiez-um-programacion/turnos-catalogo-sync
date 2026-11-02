package com.um.catalogsync.sync.application.exception;

/**
 * La catedra respondio 403 con {@code STUDENT_INTEGRATION_NOT_FOUND}: el JWT
 * tecnico no tiene una integracion asociada (§13.1).
 *
 * <p>Es una excepcion aparte y no un 403 cualquiera porque lo accionable es
 * concreto y distinto: la cuenta tecnica existe y el token es valido, pero
 * la integracion que deberia colgar de ella no esta. Eso se revisa con
 * {@code GET /api/student/integration} (§5.3) y, si hace falta, rehaciendo
 * el alta manual de §5.1. El mensaje manda ahi en lugar de dejar al lector
 * buscando un permiso que no es el problema.
 *
 * <p><b>Se decide por el {@code code}, no por el texto de {@code detail}</b>,
 * que es lo que §13 exige. El mismo 403 con otro {@code code} —o sin
 * {@code code}— es {@link CatalogSnapshotForbiddenException}.
 *
 * <p>Vive en el slice {@code sync} y no en {@code integration} aunque hable
 * de la cuenta tecnica: la lanza el adaptador del snapshot y es el resultado
 * de una llamada de {@code sync}. {@code integration} tiene su propia
 * excepcion para el 404 del mismo {@code code} en su propio endpoint, que es
 * otra operacion y otro camino.
 */
public class TechnicalIntegrationMissingException extends RuntimeException {

    /** El {@code code} de §13.1 que identifica este caso. */
    public static final String CODE = "STUDENT_INTEGRATION_NOT_FOUND";

    public TechnicalIntegrationMissingException(String ruta) {
        super("La catedra respondio 403 a " + ruta + " con code=" + CODE + ":"
                + " el JWT tecnico no tiene una integracion asociada."
                + " Verificar la cuenta con GET /api/student/integration (§5.3)"
                + " y, si no devuelve una integracion, repetir el alta manual de §5.1");
    }
}

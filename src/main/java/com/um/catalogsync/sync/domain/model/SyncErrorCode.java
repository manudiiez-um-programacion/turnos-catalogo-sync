package com.um.catalogsync.sync.domain.model;

/**
 * Codigo estable con el que se identifica el motivo de una corrida fallida.
 *
 * <p>Existe por lo mismo que §13 pide un {@code code} funcional en los
 * errores REST: para poder decidir —y para que #28 pueda informar— sin
 * parsear el texto de un mensaje. El comentario de la columna
 * {@code sync_error.code} lo dice con esas palabras.
 *
 * <p>Vocabulario propio y por eso es un enum cerrado: a diferencia de
 * {@code processed_event.source}, que guarda valores de la catedra y no
 * lleva CHECK, estos codigos los escribimos nosotros. La traduccion de
 * excepcion a codigo vive en {@code RunFullSyncUseCaseImpl}, en la capa de
 * aplicacion, porque es ahi donde se conocen las excepciones; el dominio
 * solo define el vocabulario.
 *
 * <p>Los nombres entran en el {@code VARCHAR(64)} de la columna con margen.
 */
public enum SyncErrorCode {

    /** No se pudo hablar con la catedra, o respondio un 5xx. Vale reintentar. */
    SNAPSHOT_UNAVAILABLE,

    /** La catedra rechazo el JWT tecnico (401). Reautenticarse puede resolverlo. */
    SNAPSHOT_UNAUTHORIZED,

    /** La catedra prohibio la operacion (403). Reautenticarse no sirve. */
    SNAPSHOT_FORBIDDEN,

    /** La cuenta tecnica no tiene integracion asociada (§13.1). Revisar el alta. */
    TECHNICAL_INTEGRATION_MISSING,

    /** La respuesta fue exitosa pero no cumple el contrato de §7. */
    SNAPSHOT_INCOMPLETE,

    /**
     * El snapshot se contradice a si mismo: un profesional de una categoria
     * que no viene, o un horario de un profesional que no viene. La copia
     * local no se toco.
     */
    CATALOG_INCONSISTENT,

    /** Se intento retroceder la version por un camino que no lo permite. */
    VERSION_REGRESSION,

    /** Otra sincronizacion asento la version al mismo tiempo. */
    CONCURRENT_VERSION_ADVANCE,

    /**
     * Cualquier otra cosa.
     *
     * <p>Que exista no es resignacion: una corrida que falla por un motivo
     * que no previmos tiene que quedar registrada igual, porque la
     * alternativa es que el fallo desaparezca del historial justo cuando es
     * mas raro y mas vale saberlo.
     */
    UNEXPECTED_ERROR
}

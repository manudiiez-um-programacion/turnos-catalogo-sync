package com.um.catalogsync.sync.domain.model;

/**
 * Por que se eligio este modo de sincronizacion.
 *
 * <p>Es el complemento de {@link SyncMode} y {@link SyncTrigger} en el
 * historial: aquellos dicen que se hizo y quien lo pidio, y esto dice que
 * llevo a elegirlo. Los cuatro primeros valores son las cuatro razones por
 * las que §6.2 obliga a una sincronizacion completa, y distinguirlas importa
 * porque significan cosas distintas para quien revisa: una version fuera de
 * la ventana es el funcionamiento normal de la retencion de la catedra,
 * mientras que no poder verificar el estado es un problema de conectividad.
 *
 * <p>Vive en el dominio y no en la infraestructura porque es parte de la
 * decision, no de como se guarda. La migracion V3 lo restringe con un CHECK
 * por el mismo motivo que {@code mode} y {@code status}: es vocabulario
 * propio y no de la catedra.
 */
public enum SyncDecisionReason {

    /**
     * No habia version local: base recien inicializada.
     *
     * <p>El camino incremental no aplica porque los cambios describen
     * diferencias contra un estado que no existe (§6.1).
     */
    NO_LOCAL_VERSION,

    /**
     * La version local quedo por debajo de {@code oldest-available-version}.
     *
     * <p>Es el caso canonico de §18.2: los incrementos que harian falta ya
     * no estan en la ventana que la catedra retiene. No es una anomalia, es
     * la retencion funcionando: pasa cuando el servicio estuvo apagado mas
     * tiempo del que la catedra guarda.
     */
    VERSION_OUT_OF_WINDOW,

    /**
     * Faltaba la clave de cambios de una version del rango necesario.
     *
     * <p>La version local estaba dentro de la ventana, asi que el recorrido
     * incremental arranco, y se detuvo al no encontrar una version
     * intermedia. Saltearla dejaria un hueco silencioso.
     */
    MISSING_VERSION,

    /**
     * No se pudo verificar el estado del origen.
     *
     * <p>El Redis de la catedra no respondio o respondio algo que no cumple
     * el contrato, asi que no hay forma de saber cuan atrasada esta la copia
     * local. §6.2 manda sincronizacion completa: el snapshot va por REST,
     * que es un camino distinto y puede estar sano aunque Redis no lo este.
     */
    STATE_UNVERIFIABLE,

    /**
     * La copia local estaba atras de {@code current-version} y los
     * incrementos estaban disponibles.
     *
     * <p>Es el unico motivo que lleva al camino incremental, y el caso
     * normal: la catedra publico versiones nuevas y se aplican en orden.
     */
    BEHIND_CURRENT_VERSION,

    /**
     * Alguien la pidio explicitamente, sin que haya una discontinuidad.
     *
     * <p>Una sincronizacion completa manual o de arranque forzada no nace de
     * una comparacion de versiones, asi que ninguno de los motivos de arriba
     * la describe.
     */
    REQUESTED
}

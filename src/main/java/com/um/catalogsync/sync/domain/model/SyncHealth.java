package com.um.catalogsync.sync.domain.model;

/**
 * En que situacion esta la sincronizacion, vista de afuera.
 *
 * <p>Los cuatro valores no son grados de una misma escala: cada uno pide una
 * reaccion distinta de quien consulta, y por eso se distinguen en lugar de
 * resumirse en "sano / no sano".
 *
 * <p><b>No se confunde con {@link SyncStatus}</b>, que describe una corrida
 * —{@code RUNNING}, {@code SUCCEEDED}, {@code FAILED}— y no el servicio. Una
 * corrida fallida no implica que el servicio este degradado si la siguiente
 * salio bien, y una corrida exitosa no implica estar al dia si la catedra
 * publico otra version despues.
 */
public enum SyncHealth {

    /**
     * La copia local esta en la misma version que publica la catedra.
     *
     * <p>Es el estado normal. Las busquedas devuelven el catalogo vigente y
     * no hay nada que esperar.
     */
    UP_TO_DATE,

    /**
     * La catedra publico versiones que la copia local todavia no aplico.
     *
     * <p><b>Se recupera solo.</b> Lo que falta es que termine la
     * sincronizacion en curso o que corra la proxima —la dispara una
     * notificacion o el chequeo periodico (#27)— asi que quien consulta no
     * tiene que hacer nada. Las busquedas devuelven datos consistentes, solo
     * no los ultimos.
     *
     * <p><b>Una copia local vacia tambien es "atrasada"</b>, no degradada,
     * mientras nada haya fallado: falta la primera sincronizacion y la trae
     * el chequeo periodico. La diferencia se nota en el reporte, donde el
     * atraso en versiones viene nulo porque no hay desde donde contar.
     */
    BEHIND,

    /**
     * Hay una sincronizacion corriendo ahora mismo.
     *
     * <p>Se llama asi y no "sincronizando" porque el caso que importa es el
     * de una reconstruccion completa: mientras dura, la copia local se esta
     * reemplazando entera. Las busquedas siguen respondiendo —la transaccion
     * no es visible hasta que commitea— pero el resultado puede cambiar de un
     * momento al otro.
     *
     * <p>Una corrida que quedo en {@code RUNNING} porque el servicio se cayo
     * sincronizando tambien se ve asi, y es informacion correcta: lo ultimo
     * que se sabe es que estaba reconstruyendo. En cuanto el arranque
     * reconcilia (#27), la corrida nueva pasa a ser la ultima.
     */
    REBUILDING,

    /**
     * No se pudo verificar el estado del origen, o el ultimo intento fallo.
     *
     * <p><b>No se recupera solo.</b> Es la diferencia con {@link #BEHIND} y
     * la razon por la que los dos existen: acá hace falta que vuelva la
     * catedra —la red privada, el Redis, la API— y hasta entonces los
     * reintentos van a seguir fallando. La copia local sigue sirviendo con
     * los datos que tenga, pero no hay forma de saber cuan vieja es.
     */
    DEGRADED
}

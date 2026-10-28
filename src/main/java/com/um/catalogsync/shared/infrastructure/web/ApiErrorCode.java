package com.um.catalogsync.shared.infrastructure.web;

/**
 * Los {@code code} estables que devuelve la API de este servicio.
 *
 * <p>§13 de la referencia de integracion fija la regla del contrato: los
 * clientes deciden por {@code status} y {@code code}, <b>nunca</b> por el
 * texto de {@code detail}. Por eso el {@code code} es parte del contrato y el
 * {@code detail} no: renombrar un valor de este enum rompe a la app KMP y al
 * servicio de turnos, cambiar un mensaje no.
 *
 * <p>Son un enum y no cadenas sueltas para que el catalogo completo se pueda
 * leer de un lugar y para que el compilador encuentre los usos cuando haya que
 * documentarlos en el contrato (#20).
 *
 * <p>La referencia admite que los 401 y 403 "producidos directamente por
 * Spring Security pueden no incluir {@code code}". Este servicio es mas
 * estricto a proposito: tambien esos llevan uno, porque son los errores que
 * mas se consultan desde el otro lado de la integracion y "401 sin
 * explicacion" es lo que convierte un problema de configuracion en una tarde
 * perdida. Lo exige el criterio de aceptacion de #17.
 */
public enum ApiErrorCode {

    /** 401: no vino cabecera {@code Authorization} con un Bearer. */
    AUTH_TOKEN_MISSING,

    /**
     * 401: vino un token y no se pudo aceptar.
     *
     * <p>Un solo codigo para vencido, firmado con otra clave, con otro emisor
     * o mal formado: distinguirlos le diria a quien prueba credenciales cual
     * de los tres obstaculos superó. Quien integra de buena fe tiene el
     * {@code detail} y los logs del servidor.
     */
    AUTH_TOKEN_INVALID,

    /**
     * 403: el token es valido pero no habilita esta ruta.
     *
     * <p>El caso tipico es un token de usuario final —o cualquier token sin la
     * autoridad {@code SERVICE_TURNOS}— contra la API interna.
     */
    AUTH_FORBIDDEN
}

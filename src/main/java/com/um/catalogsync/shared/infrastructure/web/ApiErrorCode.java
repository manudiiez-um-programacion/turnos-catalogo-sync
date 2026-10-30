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

    /**
     * 400: los parametros de la peticion no son aceptables.
     *
     * <p>Un solo codigo para todas las violaciones de forma —una pagina
     * negativa, un tamaño fuera de rango, un {@code categoryId} que no es un
     * numero—, y el {@code detail} dice cual. Un codigo por campo haria que
     * agregar un filtro fuera un cambio de contrato, cuando lo que el cliente
     * tiene que decidir es siempre lo mismo: corregir el pedido antes de
     * reintentar.
     */
    VALIDATION_ERROR,

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
    AUTH_FORBIDDEN,

    /**
     * 404: el profesional no esta en la copia local.
     *
     * <p>Mismo nombre que usa la catedra en §13.1 para la misma condicion: el
     * servicio de turnos ya tiene codigo que lo interpreta y no necesita un
     * caso especial por venir de aca.
     */
    PROFESSIONAL_NOT_FOUND,

    /**
     * 409: el profesional existe pero esta deshabilitado.
     *
     * <p>Distinguirlo de {@link #PROFESSIONAL_NOT_FOUND} es un criterio de
     * aceptacion de #18: turnos tiene que rechazar una operacion nueva, pero
     * puede seguir mostrando las reservas ya hechas con los datos historicos
     * que guardo (§4.2).
     *
     * <p>409 y no 400, aunque §13.1 use 400 en la catedra: alla la condicion
     * aparece al <b>crear un hold</b>, donde el profesional deshabilitado hace
     * invalido el pedido. Aca es una consulta correcta sobre un recurso que
     * existe y cuyo estado no permite la respuesta, que es lo que significa
     * 409.
     */
    PROFESSIONAL_DISABLED,

    /**
     * 503: todavia no hay una version de catalogo aplicada.
     *
     * <p>La copia local no se puede considerar vigente, asi que no hay agenda
     * que responder. 5xx y no 4xx porque el pedido del cliente es correcto: lo
     * que falta es un estado de este servicio y se resuelve solo. §13.1 reserva
     * 503 para "dependencia requerida no disponible".
     */
    CATALOG_NOT_SYNCHRONIZED
}

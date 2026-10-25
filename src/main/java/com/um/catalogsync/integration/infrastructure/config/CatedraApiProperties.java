package com.um.catalogsync.integration.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de acceso a la API de la catedra, toda externalizada.
 *
 * <p>Ningun valor tiene default sensible: {@code application.yaml} los
 * resuelve con {@code ${VAR}} sin alternativa, asi que si falta una
 * variable la aplicacion no arranca. Es deliberado —§3 exige externalizar
 * estos datos y no publicarlos— y es mejor que un default que funcione a
 * medias y esconda el problema.
 *
 * <p><b>Por que no hay {@code @NotBlank} en las tres obligatorias.</b> Seria
 * lo natural, y rompe un requisito del proyecto: Docker Compose pasa estas
 * variables vacias cuando todavia no hay cuenta tecnica —el alta es manual y
 * se hace una sola vez (§5.1)—, y con validacion de binding el contenedor no
 * arrancaria. "Todo levanta con Docker Compose" es una regla no negociable,
 * asi que el chequeo se hace donde se usa el dato y no al construir el
 * objeto: ver {@link #isConfigured()} y la verificacion de arranque, que es
 * la que falla temprano y con un mensaje que dice que variable falta.
 *
 * <p><b>Por que un {@code record} con {@code toString()} propio.</b> El
 * record da binding por constructor e inmutabilidad, pero su
 * {@code toString()} generado imprime todos los componentes, contraseña
 * incluida. Un objeto de propiedades es de los que acaban en un log de
 * diagnostico, asi que se sobrescribe. Es el mismo motivo por el que
 * {@code management.endpoints} solo expone {@code health} e {@code info}:
 * {@code /actuator/configprops} publicaria esto entero.
 *
 * @param baseUrl solo el origen de la API, <b>sin</b> {@code /api} al final
 * @param username login de la cuenta tecnica creada con Postman (§5.1)
 * @param password contraseña de esa cuenta
 * @param connectTimeout cuanto esperar para establecer la conexion
 * @param readTimeout cuanto esperar la respuesta
 * @param verifyOnStartup si el arranque verifica la integracion contra la
 *     catedra
 */
@ConfigurationProperties(prefix = "catedra.api")
public record CatedraApiProperties(
        String baseUrl,
        String username,
        String password,
        Duration connectTimeout,
        Duration readTimeout,
        boolean verifyOnStartup) {

    /**
     * Normaliza la URL base y rechaza el error que ya se cometio una vez.
     *
     * <p><b>El {@code /api} sobrante.</b> Todas las rutas de la referencia
     * lo incluyen —{@code POST /api/authenticate},
     * {@code GET /api/student/integration}—, asi que una base que termine en
     * {@code /api} produce {@code /api/api/...}. Eso no da un error claro:
     * el servidor de la catedra tiene un catch-all de frontend que responde
     * 200 con HTML a cualquier ruta que no exista, asi que la llamada
     * "funciona" y lo que falla es el parseo del JSON, lejos de la causa.
     * Verificado contra el servidor real el 2026-10-14 (issue #11).
     *
     * <p>Por eso se rechaza al construir y con un mensaje que dice que
     * sacar, en lugar de arreglarlo en silencio: si la variable esta mal, el
     * que la escribio tiene que saberlo.
     */
    public CatedraApiProperties {
        if (baseUrl != null) {
            baseUrl = baseUrl.strip();
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (baseUrl.endsWith("/api")) {
                throw new IllegalArgumentException(
                        "CATEDRA_API_BASE_URL no debe terminar en /api: las rutas de la"
                                + " referencia ya lo incluyen y quedaria /api/api/...."
                                + " Dejar solo el origen, por ejemplo http://IP:PUERTO");
            }
        }
    }

    /**
     * Si estan los tres datos que hacen falta para hablar con la catedra.
     *
     * <p>Devuelve {@code false} con la configuracion vacia, que es el estado
     * normal mientras el alta de la cuenta tecnica no este hecha. Lo consulta
     * el adaptador de login para fallar con un mensaje que nombra las
     * variables, en lugar de intentar la llamada y devolver un 401 o un error
     * de conexion que no explican que lo que falta es configuracion.
     */
    public boolean isConfigured() {
        return !estaEnBlanco(baseUrl) && !estaEnBlanco(username) && !estaEnBlanco(password);
    }

    /** Los nombres de las variables de entorno que faltan completar. */
    public String missingVariables() {
        StringBuilder faltantes = new StringBuilder();
        if (estaEnBlanco(baseUrl)) {
            faltantes.append("CATEDRA_API_BASE_URL ");
        }
        if (estaEnBlanco(username)) {
            faltantes.append("CATEDRA_TECHNICAL_USERNAME ");
        }
        if (estaEnBlanco(password)) {
            faltantes.append("CATEDRA_TECHNICAL_PASSWORD ");
        }
        return faltantes.toString().trim();
    }

    private static boolean estaEnBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    /** No imprime la contraseña ni la URL privada del servidor (D6). */
    @Override
    public String toString() {
        return "CatedraApiProperties(username=" + username
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", verifyOnStartup=" + verifyOnStartup
                + ")";
    }
}

package com.um.catalogsync.integration.domain.model;

import com.um.catalogsync.shared.domain.model.Secret;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Lo que devuelve un login tecnico: el JWT y la configuracion vigente que
 * vino con el (§5.2).
 *
 * <p><b>Por que las dos cosas juntas y no dos llamadas.</b> El contrato
 * entrega {@code id_token} e {@code integration} en la misma respuesta. Si
 * el modelo las separara, habria que autenticarse y despues pedir
 * {@code GET /api/student/integration} para tener lo que ya habia llegado:
 * dos viajes donde el contrato ofrece uno. Sobre un enlace con ~228 ms de
 * ida y vuelta (D6) eso es medio segundo regalado en cada arranque.
 *
 * <p>El puerto de {@code GET /api/student/integration} sigue existiendo y
 * tiene su propio uso: refrescar la configuracion sin volver a loguearse,
 * cuando el token todavia sirve y lo que puede haber cambiado es la
 * configuracion.
 *
 * <p>Inmutable, como {@link IntegrationConfig}, y sin {@code toString()}
 * propio: hereda el de {@code Object}, que no muestra campos. Interpolar
 * esta clase en un log no filtra nada.
 */
@Getter
@AllArgsConstructor
public class TechnicalSession {

    /**
     * El JWT tecnico, vigente un año (§4). Nunca llega a la app KMP ni se
     * acepta como credencial de entrada a este servicio (§2).
     */
    private final Secret technicalToken;

    private final IntegrationConfig integration;

    /**
     * La misma sesion con la configuracion reemplazada.
     *
     * <p>Lo necesita el refresco de configuracion: el token sigue siendo
     * valido y solo cambia lo otro. Devuelve una instancia nueva porque la
     * clase es inmutable.
     */
    public TechnicalSession withIntegration(IntegrationConfig nueva) {
        return new TechnicalSession(technicalToken, nueva);
    }
}

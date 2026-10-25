package com.um.catalogsync.integration.domain.ports.out;

import com.um.catalogsync.integration.domain.model.TechnicalSession;

/**
 * Puerto de salida del login tecnico: {@code POST /api/authenticate} con
 * {@code rememberMe: true} (§5.2).
 *
 * <p><b>No recibe las credenciales.</b> Podria tomar usuario y contraseña
 * por parametro, y seria peor: harian falta dos tipos nuevos en el dominio
 * para no pasar {@code String} sueltos, y sobre todo el usuario y la
 * contraseña tecnica tendrian que viajar por la capa {@code application}
 * para llegar hasta aca. Las credenciales son configuracion externalizada:
 * nacen en una variable de entorno y no tienen por que salir del adaptador
 * que las usa. Asi, el unico lugar del codigo que las conoce es su
 * adaptador.
 *
 * <p>{@code rememberMe} va fijo en {@code true} y no es un parametro: sin
 * el, el contrato no entrega la vigencia larga de un año (§5.2), y no hay
 * ningun caso en este servicio donde convenga un token corto.
 */
public interface TechnicalAuthPort {

    /**
     * Se autentica y devuelve el JWT con la configuracion que vino con el.
     *
     * @return la sesion tecnica recien obtenida
     * @throws com.um.catalogsync.integration.application.exception.TechnicalAuthenticationFailedException
     *     si la catedra rechaza las credenciales con 401
     * @throws com.um.catalogsync.integration.application.exception.CatedraIntegrationUnavailableException
     *     si no se pudo hablar con la catedra o respondio algo inesperado
     */
    TechnicalSession authenticate();
}

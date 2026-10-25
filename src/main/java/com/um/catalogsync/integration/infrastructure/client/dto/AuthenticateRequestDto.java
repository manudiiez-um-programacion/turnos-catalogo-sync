package com.um.catalogsync.integration.infrastructure.client.dto;

/**
 * Cuerpo de {@code POST /api/authenticate} (§5.2).
 *
 * <p>{@code rememberMe} va siempre en {@code true}: es lo que hace que el
 * token tenga la vigencia larga de un año. El campo existe en el record y no
 * se fija al serializar porque el contrato lo pide explicito en el cuerpo.
 *
 * <p>El {@code toString()} generado por el record imprimiria la contraseña,
 * asi que se sobrescribe. Es el DTO mas peligroso del slice en ese sentido:
 * es lo que se arma justo antes de una llamada que puede fallar, o sea el
 * objeto que uno tiene mas a mano para loguear mientras diagnostica.
 */
public record AuthenticateRequestDto(String username, String password, boolean rememberMe) {

    /** Arma el pedido con la vigencia larga, que es la unica que se usa. */
    public static AuthenticateRequestDto conVigenciaLarga(String username, String password) {
        return new AuthenticateRequestDto(username, password, true);
    }

    @Override
    public String toString() {
        return "AuthenticateRequestDto(username=" + username + ", password=***, rememberMe="
                + rememberMe + ")";
    }
}

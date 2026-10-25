package com.um.catalogsync.integration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de {@code POST /api/authenticate} y de
 * {@code POST /api/student/register} (§5.1, §5.2).
 *
 * <p>{@code id_token} es el unico nombre del contrato que no esta en
 * {@code camelCase} —§4 dice que los JSON usan {@code camelCase}, y este
 * campo es la excepcion, heredada del formato de JHipster—, asi que necesita
 * {@code @JsonProperty} explicito. Sin eso el token llegaria {@code null} y
 * el sintoma seria un 401 en la llamada siguiente, no un error de parseo.
 *
 * @param idToken el JWT tecnico, vigente un año (§4)
 * @param integration la configuracion que vino con el login
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TechnicalLoginResponseDto(
        @JsonProperty("id_token") String idToken,
        IntegrationDto integration) {

    /** Dice si el token llego, nunca su valor. */
    @Override
    public String toString() {
        return "TechnicalLoginResponseDto(idToken=" + (idToken == null ? "ausente" : "***")
                + ", integration=" + integration + ")";
    }
}

package com.um.catalogsync.integration.infrastructure.client.adapter;

import com.um.catalogsync.integration.application.exception.CatedraIntegrationUnavailableException;
import com.um.catalogsync.integration.application.exception.TechnicalTokenRejectedException;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.out.IntegrationConfigPort;
import com.um.catalogsync.integration.infrastructure.client.dto.IntegrationDto;
import com.um.catalogsync.integration.infrastructure.client.mapper.IntegrationConfigMapper;
import com.um.catalogsync.shared.domain.model.Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Lectura de la configuracion vigente:
 * {@code GET /api/student/integration} (§5.3).
 *
 * <p>La respuesta es el objeto {@code integration} <b>sin envoltorio</b>, a
 * diferencia del login, que lo trae dentro de otro objeto junto al token.
 * Por eso deserializa {@link IntegrationDto} directo.
 *
 * <p>Es el unico lugar donde el JWT sale del {@link Secret} para armar la
 * cabecera {@code Authorization} (§4). Sale en el valor de la cabecera y en
 * ningun log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestIntegrationConfigAdapter implements IntegrationConfigPort {

    static final String RUTA = "/api/student/integration";

    private final RestClient catedraRestClient;
    private final IntegrationConfigMapper mapper;

    @Override
    public IntegrationConfig fetchCurrent(Secret technicalToken) {
        log.debug("Releyendo la configuracion de integracion desde la catedra");

        IntegrationDto respuesta = pedirConfiguracion(technicalToken);

        IntegrationConfig configuracion = mapper.toDomain(respuesta);
        if (configuracion == null) {
            throw new CatedraIntegrationUnavailableException(
                    "La catedra respondio 200 a " + RUTA + " con un cuerpo vacio:"
                            + " la respuesta no cumple el contrato (§5.3)");
        }
        return configuracion;
    }

    private IntegrationDto pedirConfiguracion(Secret technicalToken) {
        try {
            return catedraRestClient
                    .get()
                    .uri(RUTA)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicalToken.value())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (pedido, respuesta) -> {
                        throw traducir(respuesta.getStatusCode());
                    })
                    .body(IntegrationDto.class);
        } catch (RestClientException falla) {
            throw new CatedraIntegrationUnavailableException(RUTA, falla);
        }
    }

    /**
     * El 401 de aca significa otra cosa que el del login: lo rechazado es el
     * token, no las credenciales, y eso si vale reintentarlo una vez
     * reautenticandose. La distincion la aprovecha
     * {@code TechnicalSessionCache}.
     *
     * <p>Tampoco se lee el cuerpo: el pedido llevaba el JWT en una cabecera
     * y el eco de un servidor no tiene por que terminar en una traza.
     */
    private RuntimeException traducir(HttpStatusCode estado) {
        if (estado.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return new TechnicalTokenRejectedException(RUTA);
        }
        return new CatedraIntegrationUnavailableException(
                "La catedra respondio " + estado.value() + " a " + RUTA);
    }
}

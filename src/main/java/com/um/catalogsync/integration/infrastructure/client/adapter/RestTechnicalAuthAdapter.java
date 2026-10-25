package com.um.catalogsync.integration.infrastructure.client.adapter;

import com.um.catalogsync.integration.application.exception.CatedraIntegrationUnavailableException;
import com.um.catalogsync.integration.application.exception.TechnicalAuthenticationFailedException;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.TechnicalSession;
import com.um.catalogsync.integration.domain.ports.out.TechnicalAuthPort;
import com.um.catalogsync.integration.infrastructure.client.dto.AuthenticateRequestDto;
import com.um.catalogsync.integration.infrastructure.client.dto.TechnicalLoginResponseDto;
import com.um.catalogsync.integration.infrastructure.client.mapper.IntegrationConfigMapper;
import com.um.catalogsync.integration.infrastructure.config.CatedraApiProperties;
import com.um.catalogsync.shared.domain.model.Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Login tecnico contra {@code POST /api/authenticate} (§5.2).
 *
 * <p>Es el unico lugar del codigo que conoce el usuario y la contraseña
 * tecnica: las lee de {@link CatedraApiProperties} y no las devuelve ni las
 * pasa a nadie. El puerto que implementa no las recibe por parametro
 * justamente para que no tengan que atravesar la capa de aplicacion.
 *
 * <p><b>Que se loguea.</b> Que se intento el login y con que usuario, nunca
 * la contraseña ni el token. El usuario no es secreto —esta en
 * {@code .env.example} como nombre de variable y lo eligio el alumno— y
 * saber con cual se intento es lo primero que hace falta cuando el login
 * falla.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestTechnicalAuthAdapter implements TechnicalAuthPort {

    /** La ruta ya incluye {@code /api}: la URL base es solo el origen. */
    static final String RUTA = "/api/authenticate";

    /**
     * El cliente configurado para la catedra. El nombre del campo coincide
     * con el del bean, asi que la resolucion es correcta tambien si mas
     * adelante aparece otro {@code RestClient} en el contexto.
     */
    private final RestClient catedraRestClient;

    private final CatedraApiProperties propiedades;
    private final IntegrationConfigMapper mapper;

    @Override
    public TechnicalSession authenticate() {
        if (!propiedades.isConfigured()) {
            // Antes de salir a la red: si falta configuracion, el sintoma
            // seria un 401 o un error de conexion, y los dos mandan a buscar
            // el problema al lugar equivocado.
            throw new CatedraIntegrationUnavailableException(
                    "Falta configurar la cuenta tecnica de la catedra en .env: "
                            + propiedades.missingVariables()
                            + ". El alta de la cuenta es un paso manual que se hace una sola vez"
                            + " con Postman contra POST /api/student/register (§5.1);"
                            + " el procedimiento esta en el README");
        }
        log.debug("Autenticando la cuenta tecnica \"{}\" contra la catedra", propiedades.username());

        TechnicalLoginResponseDto respuesta = pedirToken();

        if (respuesta == null || respuesta.idToken() == null || respuesta.idToken().isBlank()) {
            throw new CatedraIntegrationUnavailableException(
                    "La catedra respondio 200 a " + RUTA + " pero sin id_token:"
                            + " la respuesta no cumple el contrato (§5.2)");
        }
        IntegrationConfig integration = mapper.toDomain(respuesta.integration());
        if (integration == null) {
            throw new CatedraIntegrationUnavailableException(
                    "La catedra respondio 200 a " + RUTA + " pero sin el objeto integration:"
                            + " la respuesta no cumple el contrato (§5.2)");
        }
        log.info("Login tecnico correcto para la integracion \"{}\"", integration.getGroupId());
        return new TechnicalSession(Secret.of(respuesta.idToken()), integration);
    }

    /**
     * Hace la llamada y traduce las fallas.
     *
     * <p>El {@code catch} de {@code RestClientException} cubre lo que no es
     * una respuesta HTTP: no haber llegado al servidor, un timeout, un
     * cuerpo que no se pudo deserializar. Las excepciones que lanza
     * {@code onStatus} son propias del proyecto y no descienden de
     * {@code RestClientException}, asi que pasan de largo y no quedan
     * envueltas en el error de infraestructura.
     */
    private TechnicalLoginResponseDto pedirToken() {
        try {
            return catedraRestClient
                    .post()
                    .uri(RUTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(AuthenticateRequestDto.conVigenciaLarga(
                            propiedades.username(), propiedades.password()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (pedido, respuesta) -> {
                        throw traducir(respuesta.getStatusCode());
                    })
                    .body(TechnicalLoginResponseDto.class);
        } catch (RestClientException falla) {
            throw new CatedraIntegrationUnavailableException(RUTA, falla);
        }
    }

    /**
     * Traduce el codigo de estado, <b>sin leer el cuerpo de la respuesta</b>.
     *
     * <p>Esa omision es a proposito. El cuerpo de un error de este endpoint
     * puede hacer eco del pedido, y el pedido llevaba la contraseña tecnica.
     * Incluirlo en el mensaje —o encadenar la excepcion HTTP, que lo
     * incluye— pondria el secreto en la traza. El codigo de estado alcanza
     * para decidir que hacer, que es lo que esta funcion tiene que resolver.
     */
    private RuntimeException traducir(HttpStatusCode estado) {
        if (estado.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return new TechnicalAuthenticationFailedException(propiedades.username());
        }
        return new CatedraIntegrationUnavailableException(
                "La catedra respondio " + estado.value() + " a " + RUTA);
    }
}

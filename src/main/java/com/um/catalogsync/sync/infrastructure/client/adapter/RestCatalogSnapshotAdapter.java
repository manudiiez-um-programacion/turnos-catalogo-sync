package com.um.catalogsync.sync.infrastructure.client.adapter;

import com.um.catalogsync.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.um.catalogsync.shared.domain.model.Secret;
import com.um.catalogsync.shared.infrastructure.client.ProblemReader;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotForbiddenException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnauthorizedException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException;
import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.application.exception.TechnicalIntegrationMissingException;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.ports.out.CatalogSnapshotPort;
import com.um.catalogsync.sync.infrastructure.client.dto.CatalogSnapshotDto;
import com.um.catalogsync.sync.infrastructure.client.mapper.CatalogSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Trae el snapshot completo de {@code GET /api/synchronization/snapshot}
 * (§7) con el JWT tecnico.
 *
 * <p><b>Donde consigue el token.</b> Por el puerto de entrada
 * {@link GetTechnicalTokenUseCase} del slice {@code integration}, que es
 * exactamente lo que el ADR anuncia: "el JWT tecnico lo provee el slice
 * {@code integration} a traves de un puerto que el cliente REST consume".
 * Este adaptador no sabe de donde sale el token, si hubo que loguearse ni
 * donde se guarda, y no conoce el usuario ni la contraseña tecnica.
 *
 * <p><b>Donde consigue el cliente HTTP.</b> El bean {@code catedraRestClient},
 * que configura el slice {@code integration} —dueño de la "configuracion de
 * acceso" a la catedra segun el ADR— con la URL base y los timeouts del
 * enlace por ZeroTier (D6). Se lo recibe inyectado por tipo y nombre de
 * campo, sin importar ninguna clase de
 * {@code integration.infrastructure}: la regla de que un slice no importa el
 * {@code infrastructure} de otro queda intacta, y los timeouts del snapshot
 * son los mismos que los del resto de las llamadas a la catedra, explicitos
 * y configurables en {@code application.yaml}. El ajuste fino junto con los
 * reintentos es del issue #31.
 *
 * <p><b>Por que {@code exchange} y no {@code retrieve().onStatus(...)}</b>,
 * que es lo que hacen los dos adaptadores de {@code integration}. Acá hay
 * que leer el cuerpo del error para sacar el {@code code} de §13, y
 * {@code exchange} da la respuesta con los conversores del propio cliente
 * ya aplicados —{@code bodyTo}—, asi que el problem+json se deserializa con
 * el mismo Jackson que el resto y sin un {@code ObjectMapper} aparte.
 *
 * <p>Que esos dos adaptadores <b>no</b> lean el cuerpo del error tampoco es
 * una inconsistencia: sus pedidos llevan la contraseña o el token en el
 * body, y el eco de un servidor podria devolverlos. El del snapshot no
 * lleva body, y del cuerpo del error se usa unicamente {@code code}, nunca
 * {@code detail}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestCatalogSnapshotAdapter implements CatalogSnapshotPort {

    /** La ruta ya incluye {@code /api}: la URL base es solo el origen. */
    static final String RUTA = "/api/synchronization/snapshot";

    /**
     * El cliente configurado para la catedra. El nombre del campo coincide
     * con el del bean, asi que la resolucion es correcta tambien si mas
     * adelante aparece otro {@code RestClient} en el contexto.
     */
    private final RestClient catedraRestClient;

    private final GetTechnicalTokenUseCase getTechnicalToken;
    private final CatalogSnapshotMapper mapper;

    @Override
    public CatalogSnapshot fetch() {
        log.debug("Pidiendo el snapshot completo del catalogo a la catedra");

        CatalogSnapshotDto respuesta = pedirSnapshot(getTechnicalToken.get());

        CatalogSnapshot snapshot = mapper.toDomain(respuesta);
        if (snapshot == null) {
            throw new IncompleteCatalogSnapshotException(
                    "la catedra respondio 200 a " + RUTA + " con un cuerpo vacio");
        }
        log.info("Snapshot {} recibido ({}): {} categorias, {} profesionales, {} horarios",
                snapshot.getSnapshotVersion(),
                snapshot.getGeneratedAt(),
                snapshot.getCategories().size(),
                snapshot.getProfessionals().size(),
                snapshot.getSchedules().size());
        return snapshot;
    }

    /**
     * Hace la llamada y traduce las fallas.
     *
     * <p>El {@code catch} de {@code RestClientException} cubre lo que no es
     * una respuesta de error HTTP: no haber llegado al servidor, un timeout,
     * o un cuerpo que no se pudo deserializar —el caso del catch-all de
     * frontend de la catedra, que responde 200 con HTML a una ruta que no
     * existe (verificado en #11)—. Las excepciones que lanza
     * {@link #traducir} son propias del proyecto y no descienden de
     * {@code RestClientException}, asi que pasan de largo y no quedan
     * envueltas en el error de infraestructura.
     */
    private CatalogSnapshotDto pedirSnapshot(Secret technicalToken) {
        try {
            return catedraRestClient
                    .get()
                    .uri(RUTA)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicalToken.value())
                    .exchange((pedido, respuesta) -> {
                        HttpStatusCode estado = respuesta.getStatusCode();
                        if (estado.isError()) {
                            throw traducir(estado, ProblemReader.codeOf(respuesta));
                        }
                        return respuesta.bodyTo(CatalogSnapshotDto.class);
                    });
        } catch (RestClientException falla) {
            throw new CatalogSnapshotUnavailableException(RUTA, falla);
        }
    }

    /**
     * Elige la excepcion por {@code status} y {@code code}, nunca por
     * {@code detail} (§13).
     *
     * <p>Las cuatro ramas se corresponden con cuatro acciones distintas, que
     * es el unico motivo por el que son cuatro excepciones y no una con un
     * codigo adentro:
     *
     * <ul>
     *   <li><b>401</b> — el token no sirve: reautenticarse puede resolverlo
     *       (lo decide #14, no este adaptador).</li>
     *   <li><b>403 con {@code STUDENT_INTEGRATION_NOT_FOUND}</b> — la cuenta
     *       tecnica quedo sin integracion asociada: hay que revisar el alta
     *       (§5.1). Es el unico caso donde el {@code code} decide.</li>
     *   <li><b>403 con cualquier otro {@code code}, o sin ninguno</b> — falta
     *       un permiso: reautenticarse da lo mismo.</li>
     *   <li><b>el resto</b> — 5xx, 503 por una dependencia caida, o un estado
     *       que el contrato no prevee: vale reintentar mas tarde.</li>
     * </ul>
     *
     * <p>El {@code code} puede ser {@code null} y las dos primeras ramas lo
     * toleran: §13 avisa que los rechazos {@code 401} y {@code 403} de Spring
     * Security pueden venir sin el. Por eso la comparacion es
     * {@code CODE.equals(code)} y no al reves.
     */
    private RuntimeException traducir(HttpStatusCode estado, String code) {
        if (estado.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return new CatalogSnapshotUnauthorizedException(RUTA, code);
        }
        if (estado.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return TechnicalIntegrationMissingException.CODE.equals(code)
                    ? new TechnicalIntegrationMissingException(RUTA)
                    : new CatalogSnapshotForbiddenException(RUTA, code);
        }
        return new CatalogSnapshotUnavailableException(
                "La catedra respondio " + estado.value() + " a " + RUTA
                        + " (code=" + (code == null ? "sin code" : code) + ")");
    }
}

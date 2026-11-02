package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.professional.application.exception.UnknownCategoryException;
import com.um.catalogsync.schedule.application.exception.UnknownProfessionalException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotForbiddenException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnauthorizedException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException;
import com.um.catalogsync.sync.application.exception.ConcurrentVersionAdvanceException;
import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.application.exception.TechnicalIntegrationMissingException;
import com.um.catalogsync.sync.application.exception.VersionRegressionException;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.in.GetLocalCatalogVersionUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogSnapshotPort;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Orquesta la sincronizacion completa: registra, trae, aplica y cierra.
 *
 * <p><b>Esta clase no es transaccional, y eso es deliberado.</b> Es la otra
 * mitad de la decision que explica {@link ApplyCatalogSnapshotUseCaseImpl}:
 * ahi vive la unidad de trabajo, acá lo que tiene que sobrevivirla. Lo que
 * hace queda fuera de la transaccion de datos a proposito:
 *
 * <ul>
 *   <li><b>abrir la corrida</b>, antes de todo, para que la fila exista
 *       aunque el proceso se caiga en medio;</li>
 *   <li><b>traer el snapshot</b>, porque es una llamada REST de cientos de
 *       milisegundos por ZeroTier (D6) y tenerla adentro mantendria abiertos
 *       los locks de las tres tablas todo ese tiempo;</li>
 *   <li><b>cerrarla</b>, despues, en su propia transaccion, para que el
 *       rollback de una unidad de trabajo fallida <i>no</i> se lleve el
 *       registro de que fallo. Un historial que se borra solo cuando hay un
 *       error es peor que no tener historial.</li>
 * </ul>
 *
 * <p><b>Por que la corrida se abre antes de traer el snapshot.</b> Al reves
 * —traer primero y registrar despues— el historial quedaria mas limpio: una
 * catedra inalcanzable no dejaria filas. Justamente por eso se descarta. No
 * poder alcanzar la catedra es el fallo <i>mas frecuente</i>, porque el
 * servidor solo existe dentro de la red privada (D6), y es exactamente el
 * que este servicio tiene que poder informar. Si esas corridas no se
 * registraran, el endpoint de estado (#28) diria "ninguna sincronizacion
 * fallo" mientras ninguna consigue arrancar. Son filas, no ruido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunFullSyncUseCaseImpl implements RunFullSyncUseCase {

    private final CatalogSnapshotPort snapshots;
    private final ApplyCatalogSnapshotUseCase applySnapshot;
    private final GetLocalCatalogVersionUseCase localVersion;
    private final SyncRunRepository runs;

    @Override
    public SyncRun run(SyncTrigger trigger) {
        log.info("Sincronizacion completa pedida por {}", trigger);

        Long desde = localVersion.getCurrent().map(CatalogVersion::getVersion).orElse(null);
        SyncRun corrida = runs.start(SyncMode.FULL, trigger, desde);

        try {
            CatalogSnapshot snapshot = snapshots.fetch();
            CatalogVersion aplicada = applySnapshot.apply(snapshot);
            // Despues del commit, nunca antes: si se cerrara primero, un
            // fallo al commitear dejaria una corrida declarada exitosa sobre
            // datos que no se guardaron.
            SyncRun cerrada = runs.succeed(corrida.getId(), aplicada.getVersion());
            log.info("Sincronizacion completa {} terminada: {} → {}",
                    cerrada.getId(), desde, aplicada.getVersion());
            return cerrada;
        } catch (RuntimeException falla) {
            SyncErrorCode codigo = codigoDe(falla);
            runs.fail(corrida.getId(), codigo, falla.getMessage());
            log.warn("Sincronizacion completa {} fallida ({}): la version local sigue en {}",
                    corrida.getId(), codigo, desde, falla);
            // Se propaga la falla original y no una excepcion nueva:
            // registrar no es manejar, y quien pidio la sincronizacion tiene
            // que poder enterarse de que no se hizo.
            throw falla;
        }
    }

    /**
     * Traduce la excepcion al codigo estable que va a {@code sync_error}.
     *
     * <p><b>Vive acá y no en el dominio</b> porque es donde se conocen las
     * excepciones: {@link SyncErrorCode} solo define el vocabulario, y el
     * dominio no puede depender de la capa de aplicacion.
     *
     * <p>Decide por el <b>tipo</b> de la excepcion y nunca por su mensaje,
     * por el mismo motivo por el que §13 obliga a decidir por {@code code} y
     * no por {@code detail}: un texto se reescribe sin que nadie piense que
     * esta cambiando una decision.
     *
     * <p>Las dos excepciones de FK de los otros slices se mapean al mismo
     * codigo, {@code CATALOG_INCONSISTENT}, porque para quien lee el
     * historial son el mismo problema —el snapshot se contradice a si
     * mismo— y lo que distingue a una de la otra ya esta en el detalle.
     *
     * <p>El orden de las ramas importa en un solo punto:
     * {@code TechnicalIntegrationMissingException} y
     * {@code CatalogSnapshotForbiddenException} son las dos un 403 pero
     * clases independientes, asi que el {@code switch} las distingue sin
     * depender del orden. Si alguna vez una heredara de la otra, la mas
     * especifica tendria que ir primero.
     */
    private static SyncErrorCode codigoDe(RuntimeException falla) {
        return switch (falla) {
            case CatalogSnapshotUnauthorizedException ignored ->
                    SyncErrorCode.SNAPSHOT_UNAUTHORIZED;
            case TechnicalIntegrationMissingException ignored ->
                    SyncErrorCode.TECHNICAL_INTEGRATION_MISSING;
            case CatalogSnapshotForbiddenException ignored -> SyncErrorCode.SNAPSHOT_FORBIDDEN;
            case CatalogSnapshotUnavailableException ignored ->
                    SyncErrorCode.SNAPSHOT_UNAVAILABLE;
            case IncompleteCatalogSnapshotException ignored ->
                    SyncErrorCode.SNAPSHOT_INCOMPLETE;
            case UnknownCategoryException ignored -> SyncErrorCode.CATALOG_INCONSISTENT;
            case UnknownProfessionalException ignored -> SyncErrorCode.CATALOG_INCONSISTENT;
            case VersionRegressionException ignored -> SyncErrorCode.VERSION_REGRESSION;
            case ConcurrentVersionAdvanceException ignored ->
                    SyncErrorCode.CONCURRENT_VERSION_ADVANCE;
            default -> SyncErrorCode.UNEXPECTED_ERROR;
        };
    }
}

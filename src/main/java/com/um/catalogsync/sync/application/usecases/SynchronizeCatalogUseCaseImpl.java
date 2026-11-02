package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import com.um.catalogsync.sync.domain.ports.in.SynchronizeCatalogUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.PublishedCatalogPort;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Compara, decide y sincroniza.
 *
 * <p>Implementa el {@code alt} del diagrama de §16. La decision se toma con
 * tres datos y nada mas: la version local, {@code current-version} y
 * {@code oldest-available-version}. Los tres se leen antes de empezar y en
 * ese orden de importancia.
 *
 * <p><b>El arbol de decision, y por que cada rama.</b>
 *
 * <ol>
 *   <li><b>No se pudo leer el estado del origen</b> → completa. §6.2 lo dice
 *       explicitamente: si el estado no se puede verificar, sincronizacion
 *       completa. El snapshot va por REST, que es un camino distinto de
 *       Redis y puede estar sano aunque Redis no lo este.
 *   <li><b>No hay version local</b> → completa. Los cambios incrementales
 *       describen diferencias contra un estado que no existe (§6.1).
 *   <li><b>La catedra no publico ninguna version</b> → nada. No es un error
 *       ni un atraso: no hay destino al que llegar. Pedir un snapshot acá
 *       seria adivinar, y en el chequeo periodico de #27 significaria
 *       reconstruir la copia local cada pocos minutos contra un origen que
 *       no dice nada.
 *   <li><b>La version local quedo por debajo de la ventana retenida</b> →
 *       completa. Es el caso canonico de §18.2 y la razon por la que la
 *       comparacion vive en el modelo: {@code canCatchUpFrom} es la regla
 *       del contrato y no de este proceso.
 *   <li><b>La version local ya cubre la publicada</b> → nada.
 *   <li><b>Si no</b> → incremental, y si el recorrido se detiene por una
 *       version faltante, completa a continuacion.
 * </ol>
 *
 * <p><b>Por que el orden de las ramas es ese.</b> Verificar el origen va
 * primero porque sin ese dato ninguna comparacion posterior tiene sentido;
 * si se dejara para despues, una base recien inicializada con Redis caido
 * elegiria la rama equivocada por el motivo equivocado. Y "no hay version
 * local" va antes que la ventana porque {@code canCatchUpFrom} necesita una
 * version local con la que comparar.
 *
 * <p><b>No hay transaccion acá.</b> Cada versión incremental abre la suya
 * y el snapshot abre la propia; esta clase orquesta y registra. Una
 * transaccion que las envolviera convertiria un atraso de diez versiones en
 * una unica unidad de trabajo, que es justo lo que #25 evita.
 *
 * <p><b>Una sola sincronizacion a la vez, y el lock vive acá.</b> Tres cosas
 * pueden pedir sincronizar: una notificacion de Kafka, el chequeo periodico
 * de reconciliacion y una operacion manual. Si dos se cruzaran, las dos
 * leerian la misma version local, las dos decidirian avanzar y la segunda
 * recibiria un {@code ConcurrentVersionAdvanceException} o un
 * {@code VersionRegressionException} al llegar a {@code advanceTo} —o, peor,
 * las dos pedirian el snapshot completo y la catedra atenderia dos veces el
 * mismo trabajo sobre un enlace de ~228 ms (D6)—. El lock esta en este
 * metodo y no en cada llamador porque es el unico lugar por donde pasan los
 * tres.
 *
 * <p><b>El que no consigue el lock se va, no espera.</b> Lo que estaba por
 * hacer lo esta haciendo el otro: encolarse solo lograria repetir la misma
 * comparacion de versiones un rato despues, y la segunda pasada no
 * encontraria nada que hacer. El unico caso que se pierde es una version
 * publicada <i>mientras</i> la sincronizacion en curso ya habia leido
 * {@code current-version}, y ese lo recupera el chequeo periodico (#27), que
 * es justamente para lo que existe.
 *
 * <p>Con una sola instancia del servicio un lock en proceso alcanza. Si
 * alguna vez hubiera mas de una, habria que moverlo a la base —un
 * {@code SELECT ... FOR UPDATE} sobre {@code catalog_version}, por ejemplo—
 * y esta clase seria el unico lugar a tocar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SynchronizeCatalogUseCaseImpl implements SynchronizeCatalogUseCase {

    private final CatalogVersionRepository versions;
    private final PublishedCatalogPort publishedCatalog;
    private final ApplyIncrementalChangesUseCase incremental;
    private final RunFullSyncUseCase fullSync;
    private final SyncRunRepository runs;

    /** Garantiza una sola sincronizacion a la vez. Ver el javadoc de la clase. */
    private final ReentrantLock enCurso = new ReentrantLock();

    @Override
    public Optional<SyncRun> synchronize(SyncTrigger trigger) {
        if (!enCurso.tryLock()) {
            log.info("Ya hay una sincronizacion en curso: se omite la que pidio {}", trigger);
            return Optional.empty();
        }
        try {
            return decidirYSincronizar(trigger);
        } finally {
            enCurso.unlock();
        }
    }

    /**
     * El arbol de decision, con el lock ya tomado.
     *
     * <p>Separado del metodo publico para que el {@code tryLock} y el
     * {@code unlock} queden en un bloque chico y no haya ningun camino de
     * salida —ni los {@code return} tempranos ni una excepcion— que se lleve
     * el lock puesto.
     */
    private Optional<SyncRun> decidirYSincronizar(SyncTrigger trigger) {
        Optional<Long> localVersion =
                versions.findCurrent().map(CatalogVersion::getVersion);

        Optional<PublishedCatalogMetadata> origen;
        try {
            origen = publishedCatalog.metadata();
        } catch (PublishedCatalogUnavailableException | MalformedPublishedCatalogException
                noSePudoVerificar) {
            // §6.2: si el estado no se puede verificar, completa. El mensaje
            // va en WARN con la causa, porque lo normal es que sea la red
            // privada y no un problema del catalogo.
            log.warn("No se pudo verificar el estado del origen ({}): se reconstruye con"
                            + " el snapshot completo",
                    noSePudoVerificar.getMessage());
            return Optional.of(reconstruir(trigger, SyncDecisionReason.STATE_UNVERIFIABLE));
        }

        if (localVersion.isEmpty()) {
            log.info("Sin version local: se pide el catalogo completo");
            return Optional.of(reconstruir(trigger, SyncDecisionReason.NO_LOCAL_VERSION));
        }
        if (origen.isEmpty()) {
            // La catedra no publico nada todavia. No hay destino, asi que no
            // hay atraso que recuperar.
            log.warn("La catedra no tiene metadata publicada: no hay version a la que"
                    + " ponerse al dia");
            return Optional.empty();
        }

        long local = localVersion.get();
        PublishedCatalogMetadata metadata = origen.get();

        if (!metadata.canCatchUpFrom(local)) {
            // El caso de §18.2: local en 3, oldest-available en 4. Falta
            // justamente la version que conecta el estado local con la
            // ventana, asi que no se intenta empezar en changes:4.
            log.info("La version local {} quedo por debajo de la mas antigua disponible {}:"
                            + " se reconstruye con el snapshot completo (§18.2)",
                    local, metadata.getOldestAvailableVersion());
            return Optional.of(reconstruir(trigger, SyncDecisionReason.VERSION_OUT_OF_WINDOW));
        }
        if (local >= metadata.getCurrentVersion()) {
            // El camino mas frecuente del chequeo periodico. Sin corrida y
            // en debug: #27 pide que no genere carga ni logs cuando no hay
            // nada que hacer.
            log.debug("La copia local esta al dia en la version {}", local);
            return Optional.empty();
        }

        return Optional.of(ponerseAlDia(trigger, local, metadata.getCurrentVersion()));
    }

    /**
     * Recorre las versiones que faltan y, si no alcanza, reconstruye.
     *
     * <p>La corrida incremental se cierra como exitosa aunque no haya
     * llegado al destino, y es a proposito: lo que hizo —avanzar la copia
     * local hasta la ultima version completa— lo hizo bien. La corrida que
     * explica por que no se llego al destino es la de la reconstruccion que
     * viene despues, con su motivo {@code MISSING_VERSION}. Marcarla como
     * fallida diria que algo se rompio, y no se rompio nada: la ventana de
     * la catedra no tenia una version.
     */
    private SyncRun ponerseAlDia(SyncTrigger trigger, long local, long destino) {
        SyncRun corrida = runs.start(SyncMode.INCREMENTAL, trigger,
                SyncDecisionReason.BEHIND_CURRENT_VERSION, local);
        IncrementalSyncResult resultado;
        try {
            resultado = incremental.applyUpTo(destino);
        } catch (RuntimeException falla) {
            runs.fail(corrida.getId(), SyncErrorCode.UNEXPECTED_ERROR, falla.getMessage());
            log.warn("Sincronizacion incremental {} fallida: la version local sigue en la"
                            + " ultima aplicada completa",
                    corrida.getId(), falla);
            throw falla;
        }

        long alcanzada = resultado.toVersion() == null ? local : resultado.toVersion();
        SyncRun cerrada = runs.succeed(corrida.getId(), alcanzada);

        if (resultado.needsFullSync()) {
            log.info("El recorrido incremental se detuvo en la version {} ({}): se"
                            + " reconstruye con el snapshot completo",
                    alcanzada, resultado.outcome());
            return reconstruir(trigger, SyncDecisionReason.MISSING_VERSION);
        }
        log.info("Sincronizacion incremental {} terminada: {} → {}",
                cerrada.getId(), local, alcanzada);
        return cerrada;
    }

    /**
     * Pide el snapshot completo y lo aplica.
     *
     * <p>Delega en {@link RunFullSyncUseCase}, que ya registra su propia
     * corrida, trae el snapshot fuera de la transaccion y traduce las fallas
     * a codigos estables. Lo unico que agrega esta clase es el motivo.
     *
     * <p>La version local queda en la {@code snapshotVersion} del snapshot y
     * no en la que vino en la notificacion. No es lo mismo: el snapshot se
     * genera cuando se lo pide, asi que puede traer una version distinta —y
     * mas nueva— que la que disparo el aviso. La version local tiene que
     * describir los datos que la copia local <i>tiene</i>.
     */
    private SyncRun reconstruir(SyncTrigger trigger, SyncDecisionReason motivo) {
        return fullSync.run(trigger, motivo);
    }
}

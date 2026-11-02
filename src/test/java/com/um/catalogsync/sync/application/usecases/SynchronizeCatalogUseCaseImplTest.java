package com.um.catalogsync.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.IncrementalSyncOutcome;
import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.in.RunFullSyncUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.PublishedCatalogPort;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El arbol de decision de §6.2, con dobles de los cinco puertos.
 *
 * <p>Lo que se prueba es qué camino se elige y con qué motivo queda
 * registrado. No hace falta Redis ni base: la decision se toma con tres
 * numeros —la version local, {@code current-version} y
 * {@code oldest-available-version}— y lo que importa es la comparacion, no de
 * donde vienen.
 *
 * <p>Los dobles registran las llamadas en {@link #pasos} para que cada
 * prueba pueda afirmar las dos cosas juntas: que se eligio el camino
 * correcto y que el motivo que quedo en {@code sync_run} es el que
 * corresponde.
 */
class SynchronizeCatalogUseCaseImplTest {

    private final List<String> pasos = new ArrayList<>();

    private final VersionLocal versionLocal = new VersionLocal();
    private final Origen origen = new Origen();
    private final Incremental incremental = new Incremental();
    private final Completa completa = new Completa();
    private final Corridas corridas = new Corridas();

    private SynchronizeCatalogUseCaseImpl useCase() {
        return new SynchronizeCatalogUseCaseImpl(
                versionLocal, origen, incremental, completa, corridas);
    }

    // --- El caso canonico de §18.2 ----------------------------------------

    @Test
    @DisplayName("Local en 3, current 7, oldest 4: snapshot y no changes:4 (§18.2)")
    void elCasoCanonicoDeLaReferencia() {
        versionLocal.version = 3L;
        origen.publicar(7L, 4L);

        Optional<SyncRun> corrida = useCase().synchronize(SyncTrigger.EVENT);

        // Con la version local en 3 y la mas antigua disponible en 4, falta
        // justamente la version que conecta el estado local con la ventana.
        // Empezar en changes:4 dejaria un hueco silencioso.
        assertThat(incremental.destinos).isEmpty();
        assertThat(pasos).containsExactly("completa(EVENT, VERSION_OUT_OF_WINDOW)");
        assertThat(corrida).isPresent();
    }

    @Test
    @DisplayName("Local igual a oldest todavia sirve para avanzar con incrementos")
    void elBordeDeLaVentanaSirve() {
        // §14.2 y §18.2: lo que hace falta son los cambios POSTERIORES a la
        // version local, y esos estan. Pedir snapshot acá seria reconstruir
        // el catalogo entero por nada.
        versionLocal.version = 4L;
        origen.publicar(7L, 4L);

        useCase().synchronize(SyncTrigger.EVENT);

        assertThat(incremental.destinos).containsExactly(7L);
        assertThat(pasos).containsExactly(
                "corridas.start(INCREMENTAL, EVENT, BEHIND_CURRENT_VERSION, 4)",
                "corridas.succeed(1, 7)");
    }

    // --- Las otras tres razones para reconstruir ---------------------------

    @Test
    @DisplayName("Sin version local, snapshot")
    void sinVersionLocal() {
        versionLocal.version = null;
        origen.publicar(7L, 4L);

        useCase().synchronize(SyncTrigger.STARTUP);

        assertThat(pasos).containsExactly("completa(STARTUP, NO_LOCAL_VERSION)");
    }

    @Test
    @DisplayName("Si Redis no responde, no se puede verificar el estado: snapshot")
    void siNoSePuedeVerificarElEstado() {
        // §6.2 lo dice explicitamente. El snapshot va por REST, que es un
        // camino distinto de Redis y puede estar sano aunque Redis no lo
        // este.
        versionLocal.version = 5L;
        origen.falla = new PublishedCatalogUnavailableException("la metadata",
                new IllegalStateException("conexion rechazada"));

        useCase().synchronize(SyncTrigger.RECONCILIATION);

        assertThat(pasos).containsExactly("completa(RECONCILIATION, STATE_UNVERIFIABLE)");
    }

    @Test
    @DisplayName("Si la metadata no cumple el contrato, tampoco se puede verificar")
    void siLaMetadataEstaMalformada() {
        versionLocal.version = 5L;
        origen.falla = new MalformedPublishedCatalogException("falta currentVersion");

        useCase().synchronize(SyncTrigger.EVENT);

        assertThat(pasos).containsExactly("completa(EVENT, STATE_UNVERIFIABLE)");
    }

    @Test
    @DisplayName("Si el recorrido se detiene por una version faltante, snapshot despues")
    void siFaltaUnaVersionDelRango() {
        versionLocal.version = 5L;
        origen.publicar(9L, 4L);
        incremental.resultado = new IncrementalSyncResult(
                5L, 6L, 9L, 1, IncrementalSyncOutcome.MISSING_VERSION);

        Optional<SyncRun> corrida = useCase().synchronize(SyncTrigger.EVENT);

        // La corrida incremental se cierra como exitosa con la version a la
        // que llego —lo que hizo, lo hizo bien— y la que explica por que no
        // se llego al destino es la de la reconstruccion.
        assertThat(pasos).containsExactly(
                "corridas.start(INCREMENTAL, EVENT, BEHIND_CURRENT_VERSION, 5)",
                "corridas.succeed(1, 6)",
                "completa(EVENT, MISSING_VERSION)");
        assertThat(corrida).isPresent();
        assertThat(corrida.get().getMode()).isEqualTo(SyncMode.FULL);
    }

    // --- Cuando no hay nada que hacer -------------------------------------

    @Test
    @DisplayName("Si la copia local esta al dia, no se hace nada ni se registra corrida")
    void yaEstaAlDia() {
        // #27 pide que el chequeo periodico no genere carga ni logs cuando no
        // hay novedades, y un historial con una fila por chequeo no deja ver
        // las corridas que si hicieron algo.
        versionLocal.version = 7L;
        origen.publicar(7L, 4L);

        assertThat(useCase().synchronize(SyncTrigger.RECONCILIATION)).isEmpty();
        assertThat(pasos).isEmpty();
    }

    @Test
    @DisplayName("Una version local mas nueva que la publicada no dispara nada")
    void laVersionLocalMasNueva() {
        // Pasa cuando un snapshot recien aplicado adelanto la copia local y
        // una notificacion vieja llega despues.
        versionLocal.version = 9L;
        origen.publicar(7L, 4L);

        assertThat(useCase().synchronize(SyncTrigger.EVENT)).isEmpty();
        assertThat(pasos).isEmpty();
    }

    @Test
    @DisplayName("Si la catedra no publico metadata, no hay destino: no se hace nada")
    void sinMetadataPublicada() {
        // Redis respondio y no tiene nada: es un estado verificado, no un
        // estado que no se pudo verificar. Pedir snapshot acá significaria
        // reconstruir la copia local en cada chequeo contra un origen que no
        // dice nada.
        versionLocal.version = 5L;

        assertThat(useCase().synchronize(SyncTrigger.RECONCILIATION)).isEmpty();
        assertThat(pasos).isEmpty();
    }

    // --- Cuando algo falla ------------------------------------------------

    @Test
    @DisplayName("Si el recorrido incremental falla, la corrida queda FAILED y se propaga")
    void siElIncrementalFalla() {
        versionLocal.version = 5L;
        origen.publicar(7L, 4L);
        incremental.falla = new IllegalStateException("la base no responde");

        assertThatThrownBy(() -> useCase().synchronize(SyncTrigger.EVENT))
                .isInstanceOf(IllegalStateException.class);

        // Registrar no es manejar: quien pidio sincronizar tiene que poder
        // enterarse de que no se hizo.
        assertThat(pasos).containsExactly(
                "corridas.start(INCREMENTAL, EVENT, BEHIND_CURRENT_VERSION, 5)",
                "corridas.fail(1, UNEXPECTED_ERROR)");
    }

    @Test
    @DisplayName("Si el snapshot falla, la falla sale sin inventar una corrida nueva")
    void siLaCompletaFalla() {
        // RunFullSyncUseCase ya registra su propia corrida fallida: esta
        // clase no la duplica.
        versionLocal.version = null;
        origen.publicar(7L, 4L);
        completa.falla = new IllegalStateException("la catedra no responde");

        assertThatThrownBy(() -> useCase().synchronize(SyncTrigger.STARTUP))
                .isInstanceOf(IllegalStateException.class);

        assertThat(pasos).containsExactly("completa(STARTUP, NO_LOCAL_VERSION)");
    }

    // --- Dobles -----------------------------------------------------------

    private static final class VersionLocal implements CatalogVersionRepository {

        private Long version;

        @Override
        public Optional<CatalogVersion> findCurrent() {
            return Optional.ofNullable(version).map(v -> CatalogVersion.builder()
                    .version(v)
                    .source(CatalogVersionSource.INCREMENTAL)
                    .appliedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .build());
        }

        @Override
        public CatalogVersion advanceTo(long version, CatalogVersionSource source) {
            throw new AssertionError("La decision no avanza la version por su cuenta");
        }
    }

    private static final class Origen implements PublishedCatalogPort {

        private PublishedCatalogMetadata metadata;
        private RuntimeException falla;

        private void publicar(long actual, long masAntigua) {
            metadata = PublishedCatalogMetadata.builder()
                    .currentVersion(actual)
                    .oldestAvailableVersion(masAntigua)
                    .publishedAt(Instant.parse("2026-09-10T13:44:59Z"))
                    .build();
        }

        @Override
        public Optional<PublishedCatalogMetadata> metadata() {
            if (falla != null) {
                throw falla;
            }
            return Optional.ofNullable(metadata);
        }

        @Override
        public Optional<Long> currentVersion() {
            throw new AssertionError("La decision lee la metadata, que trae los dos"
                    + " numeros en una sola lectura");
        }

        @Override
        public Optional<Long> oldestAvailableVersion() {
            throw new AssertionError("idem currentVersion");
        }

        @Override
        public Optional<ProfessionalCategory> findCategory(long id) {
            throw new AssertionError("La decision no lee entidades");
        }

        @Override
        public Optional<Professional> findProfessional(long id) {
            throw new AssertionError("La decision no lee entidades");
        }

        @Override
        public Optional<WeeklySchedule> findSchedule(long id) {
            throw new AssertionError("La decision no lee entidades");
        }
    }

    private final class Incremental implements ApplyIncrementalChangesUseCase {

        private final List<Long> destinos = new ArrayList<>();
        private IncrementalSyncResult resultado;
        private RuntimeException falla;

        @Override
        public IncrementalSyncResult applyUpTo(long targetVersion) {
            if (falla != null) {
                throw falla;
            }
            destinos.add(targetVersion);
            return resultado != null ? resultado : new IncrementalSyncResult(
                    versionLocal.version, targetVersion, targetVersion, 1,
                    IncrementalSyncOutcome.CAUGHT_UP);
        }
    }

    private final class Completa implements RunFullSyncUseCase {

        private RuntimeException falla;

        @Override
        public SyncRun run(SyncTrigger trigger, SyncDecisionReason reason) {
            pasos.add("completa(" + trigger + ", " + reason + ")");
            if (falla != null) {
                throw falla;
            }
            return SyncRun.builder()
                    .id(2L)
                    .mode(SyncMode.FULL)
                    .triggeredBy(trigger)
                    .decisionReason(reason)
                    .status(SyncStatus.SUCCEEDED)
                    .toVersion(7L)
                    .startedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .finishedAt(Instant.parse("2026-11-02T12:00:05Z"))
                    .build();
        }
    }

    private final class Corridas implements SyncRunRepository {

        @Override
        public SyncRun start(SyncMode mode, SyncTrigger trigger, SyncDecisionReason reason,
                Long fromVersion) {
            pasos.add("corridas.start(" + mode + ", " + trigger + ", " + reason + ", "
                    + fromVersion + ")");
            return SyncRun.builder()
                    .id(1L)
                    .mode(mode)
                    .triggeredBy(trigger)
                    .decisionReason(reason)
                    .status(SyncStatus.RUNNING)
                    .fromVersion(fromVersion)
                    .startedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .build();
        }

        @Override
        public SyncRun succeed(long runId, long toVersion) {
            pasos.add("corridas.succeed(" + runId + ", " + toVersion + ")");
            return SyncRun.builder()
                    .id(runId)
                    .mode(SyncMode.INCREMENTAL)
                    .status(SyncStatus.SUCCEEDED)
                    .toVersion(toVersion)
                    .startedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .finishedAt(Instant.parse("2026-11-02T12:00:01Z"))
                    .build();
        }

        @Override
        public SyncRun fail(long runId, SyncErrorCode code, String detail) {
            pasos.add("corridas.fail(" + runId + ", " + code + ")");
            return SyncRun.builder()
                    .id(runId)
                    .mode(SyncMode.INCREMENTAL)
                    .status(SyncStatus.FAILED)
                    .startedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .finishedAt(Instant.parse("2026-11-02T12:00:01Z"))
                    .build();
        }
    }
}

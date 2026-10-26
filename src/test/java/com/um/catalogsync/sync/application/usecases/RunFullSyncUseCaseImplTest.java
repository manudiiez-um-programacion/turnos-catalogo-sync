package com.um.catalogsync.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.professional.application.exception.UnknownCategoryException;
import com.um.catalogsync.schedule.application.exception.UnknownProfessionalException;
import com.um.catalogsync.sync.CatalogSnapshotFixtures;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotForbiddenException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnauthorizedException;
import com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException;
import com.um.catalogsync.sync.application.exception.ConcurrentVersionAdvanceException;
import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.application.exception.TechnicalIntegrationMissingException;
import com.um.catalogsync.sync.application.exception.VersionRegressionException;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * La orquestacion: que se registra, en que orden y que se propaga.
 *
 * <p>Con dobles de los cuatro colaboradores, sin base ni red. Lo que esta
 * clase decide es puro orden y traduccion de errores, y eso se prueba mejor
 * asi: contra PostgreSQL habria que provocar cada falla de verdad.
 */
class RunFullSyncUseCaseImplTest {

    private final List<String> pasos = new ArrayList<>();
    private final CorridasQueRegistran corridas = new CorridasQueRegistran();

    private RunFullSyncUseCaseImpl useCase(
            Supplier<CatalogSnapshot> snapshot, Supplier<CatalogVersion> aplicar, Long desde) {
        return new RunFullSyncUseCaseImpl(
                () -> {
                    pasos.add("snapshots.fetch");
                    return snapshot.get();
                },
                s -> {
                    pasos.add("apply(" + s.getSnapshotVersion() + ")");
                    return aplicar.get();
                },
                () -> Optional.ofNullable(desde).map(v -> CatalogVersion.builder()
                        .version(v)
                        .appliedAt(Instant.parse("2026-09-01T00:00:00Z"))
                        .source(CatalogVersionSource.SNAPSHOT)
                        .build()),
                corridas);
    }

    private RunFullSyncUseCaseImpl useCaseFeliz(Long desde) {
        return useCase(
                () -> CatalogSnapshotFixtures.version(7L),
                () -> version(7L),
                desde);
    }

    private static CatalogVersion version(long numero) {
        return CatalogVersion.builder()
                .version(numero)
                .appliedAt(Instant.parse("2026-09-10T14:00:00Z"))
                .source(CatalogVersionSource.SNAPSHOT)
                .build();
    }

    @Test
    @DisplayName("Registra inicio, trae, aplica y cierra, en ese orden")
    void ordenDeLaOrquestacion() {
        SyncRun corrida = useCaseFeliz(5L).run(SyncTrigger.STARTUP);

        assertThat(pasos).containsExactly(
                "runs.start(FULL, STARTUP, 5)",
                "snapshots.fetch",
                "apply(7)",
                "runs.succeed(7)");
        assertThat(corrida.getStatus()).isEqualTo(SyncStatus.SUCCEEDED);
        assertThat(corrida.getToVersion()).isEqualTo(7L);
        assertThat(corrida.getFromVersion()).isEqualTo(5L);
        assertThat(corrida.getMode()).isEqualTo(SyncMode.FULL);
        assertThat(corrida.getTriggeredBy()).isEqualTo(SyncTrigger.STARTUP);
    }

    @Test
    @DisplayName("La corrida se abre ANTES de traer el snapshot")
    void laCorridaSeAbreAntesDelFetch() {
        var useCase = useCase(
                () -> {
                    throw new CatalogSnapshotUnavailableException("la catedra no responde");
                },
                () -> version(7L),
                null);

        assertThatThrownBy(() -> useCase.run(SyncTrigger.STARTUP))
                .isInstanceOf(CatalogSnapshotUnavailableException.class);

        // Una catedra inalcanzable es el fallo mas frecuente, porque el
        // servidor solo existe dentro de la red privada (D6), y es el que
        // este servicio tiene que poder informar. Si la corrida se abriera
        // despues del fetch, el estado (#28) diria que ninguna
        // sincronizacion fallo mientras ninguna consigue arrancar.
        assertThat(pasos).containsExactly(
                "runs.start(FULL, STARTUP, null)",
                "snapshots.fetch",
                "runs.fail(SNAPSHOT_UNAVAILABLE)");
    }

    @Test
    @DisplayName("Con base vacia, fromVersion es null y no la version 0")
    void baseVaciaRegistraNull() {
        SyncRun corrida = useCaseFeliz(null).run(SyncTrigger.STARTUP);

        // null distingue "no teniamos ninguna version" de "veniamos de la 0",
        // que es la distincion que dispara la primera sincronizacion (#14).
        assertThat(corrida.getFromVersion()).isNull();
        assertThat(pasos).startsWith("runs.start(FULL, STARTUP, null)");
    }

    @Test
    @DisplayName("Si falla la aplicacion, se cierra como fallida y se propaga la falla original")
    void fallaAlAplicar() {
        var falla = new UnknownProfessionalException(999L, null);
        var useCase = useCase(
                () -> CatalogSnapshotFixtures.conHorarioHuerfano(7L),
                () -> {
                    throw falla;
                },
                5L);

        // Se propaga la excepcion original y no una nueva: registrar no es
        // manejar, y quien pidio la sincronizacion tiene que enterarse.
        assertThatThrownBy(() -> useCase.run(SyncTrigger.DISCONTINUITY))
                .isSameAs(falla);

        assertThat(pasos).containsExactly(
                "runs.start(FULL, DISCONTINUITY, 5)",
                "snapshots.fetch",
                "apply(7)",
                "runs.fail(CATALOG_INCONSISTENT)");
    }

    @Test
    @DisplayName("Una corrida fallida no deja toVersion: no se llego a ninguna version")
    void fallidaSinToVersion() {
        var useCase = useCase(
                () -> CatalogSnapshotFixtures.version(7L),
                () -> {
                    throw new IllegalStateException("cualquier cosa");
                },
                5L);

        assertThatThrownBy(() -> useCase.run(SyncTrigger.MANUAL)).isInstanceOf(
                IllegalStateException.class);

        SyncRun ultima = corridas.ultima();
        assertThat(ultima.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(ultima.getToVersion()).isNull();
        assertThat(ultima.getFromVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("El detalle del error es el mensaje de la excepcion")
    void guardaElMensajeComoDetalle() {
        var useCase = useCase(
                () -> CatalogSnapshotFixtures.version(7L),
                () -> {
                    throw new IllegalStateException("el motivo exacto");
                },
                null);

        assertThatThrownBy(() -> useCase.run(SyncTrigger.EVENT)).isInstanceOf(
                IllegalStateException.class);

        assertThat(corridas.ultimoDetalle).isEqualTo("el motivo exacto");
    }

    static List<Arguments> fallasYCodigos() {
        return List.of(
                Arguments.of(new CatalogSnapshotUnauthorizedException("/x", null),
                        SyncErrorCode.SNAPSHOT_UNAUTHORIZED),
                Arguments.of(new TechnicalIntegrationMissingException("/x"),
                        SyncErrorCode.TECHNICAL_INTEGRATION_MISSING),
                Arguments.of(new CatalogSnapshotForbiddenException("/x", "OTRO"),
                        SyncErrorCode.SNAPSHOT_FORBIDDEN),
                Arguments.of(new CatalogSnapshotUnavailableException("caido"),
                        SyncErrorCode.SNAPSHOT_UNAVAILABLE),
                Arguments.of(new IncompleteCatalogSnapshotException("falta algo"),
                        SyncErrorCode.SNAPSHOT_INCOMPLETE),
                Arguments.of(new UnknownCategoryException(1L, null),
                        SyncErrorCode.CATALOG_INCONSISTENT),
                Arguments.of(new UnknownProfessionalException(1L, null),
                        SyncErrorCode.CATALOG_INCONSISTENT),
                Arguments.of(new VersionRegressionException(7L, 3L),
                        SyncErrorCode.VERSION_REGRESSION),
                Arguments.of(new ConcurrentVersionAdvanceException(7L, null),
                        SyncErrorCode.CONCURRENT_VERSION_ADVANCE),
                Arguments.of(new IllegalStateException("algo que no previmos"),
                        SyncErrorCode.UNEXPECTED_ERROR));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("fallasYCodigos")
    @DisplayName("Cada falla se registra con su codigo estable")
    void traduceLaFallaACodigo(RuntimeException falla, SyncErrorCode esperado) {
        var useCase = useCase(
                () -> CatalogSnapshotFixtures.version(7L),
                () -> {
                    throw falla;
                },
                null);

        // Decide por el tipo de la excepcion y nunca por su mensaje, por lo
        // mismo que §13 obliga a decidir por code y no por detail.
        assertThatThrownBy(() -> useCase.run(SyncTrigger.MANUAL)).isSameAs(falla);
        assertThat(corridas.ultimoCodigo).isEqualTo(esperado);
    }

    /** Doble del historial que anota las llamadas y recuerda la ultima fila. */
    private class CorridasQueRegistran implements SyncRunRepository {

        private final List<SyncRun> filas = new ArrayList<>();
        private SyncErrorCode ultimoCodigo;
        private String ultimoDetalle;

        @Override
        public SyncRun start(SyncMode mode, SyncTrigger trigger, Long fromVersion) {
            pasos.add("runs.start(" + mode + ", " + trigger + ", " + fromVersion + ")");
            SyncRun nueva = SyncRun.builder()
                    .id((long) (filas.size() + 1))
                    .mode(mode)
                    .triggeredBy(trigger)
                    .status(SyncStatus.RUNNING)
                    .fromVersion(fromVersion)
                    .startedAt(Instant.parse("2026-09-10T13:44:00Z"))
                    .build();
            filas.add(nueva);
            return nueva;
        }

        @Override
        public SyncRun succeed(long runId, long toVersion) {
            pasos.add("runs.succeed(" + toVersion + ")");
            SyncRun corrida = porId(runId);
            corrida.setStatus(SyncStatus.SUCCEEDED);
            corrida.setToVersion(toVersion);
            corrida.setFinishedAt(Instant.parse("2026-09-10T13:45:00Z"));
            return corrida;
        }

        @Override
        public SyncRun fail(long runId, SyncErrorCode code, String detail) {
            pasos.add("runs.fail(" + code + ")");
            ultimoCodigo = code;
            ultimoDetalle = detail;
            SyncRun corrida = porId(runId);
            corrida.setStatus(SyncStatus.FAILED);
            corrida.setToVersion(null);
            corrida.setFinishedAt(Instant.parse("2026-09-10T13:45:00Z"));
            return corrida;
        }

        private SyncRun porId(long runId) {
            return filas.stream()
                    .filter(c -> c.getId() == runId)
                    .findFirst()
                    .orElseThrow();
        }

        SyncRun ultima() {
            return filas.getLast();
        }
    }
}

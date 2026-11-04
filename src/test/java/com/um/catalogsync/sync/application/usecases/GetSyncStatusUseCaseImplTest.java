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
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncHealth;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncOriginState;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncStatusReport;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
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
 * Lo que decide este caso de uso: que lee y que hace cuando una lectura no se
 * puede hacer.
 *
 * <p>Las reglas que derivan el estado y el atraso son del modelo y se prueban
 * en {@code SyncStatusReportTest}; acá se prueba que el reporte se componga con
 * las tres lecturas y que la catedra caida termine en {@code UNREACHABLE} en
 * lugar de en una excepcion.
 *
 * <p>Sin base y sin contexto: tres dobles alcanzan, porque el caso de uso no
 * hace nada mas que leer.
 */
class GetSyncStatusUseCaseImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T18:30:00Z");

    private final VersionLocal versiones = new VersionLocal();
    private final Historial corridas = new Historial();
    private final Origen origen = new Origen();

    private final GetSyncStatusUseCaseImpl useCase =
            new GetSyncStatusUseCaseImpl(versiones, corridas, origen);

    @Test
    @DisplayName("El reporte junta la version local, la ultima corrida, el origen y los errores")
    void juntaLasTresLecturas() {
        versiones.aplicada = version(128);
        corridas.ultima = corridaExitosa(128);
        corridas.errores = List.of(error(SyncErrorCode.SNAPSHOT_UNAVAILABLE));
        origen.publicar(128, 120);

        SyncStatusReport reporte = useCase.getStatus();

        assertThat(reporte.localVersion().getVersion()).isEqualTo(128);
        assertThat(reporte.lastRun().getId()).isEqualTo(412L);
        assertThat(reporte.originState()).isEqualTo(SyncOriginState.KNOWN);
        assertThat(reporte.origin().getCurrentVersion()).isEqualTo(128);
        assertThat(reporte.recentErrors()).hasSize(1);
        assertThat(reporte.health()).isEqualTo(SyncHealth.UP_TO_DATE);
    }

    @Test
    @DisplayName("Los errores se piden acotados: el reporte no es un visor de historial")
    void pideLosErroresAcotados() {
        origen.publicar(1, 1);

        useCase.getStatus();

        assertThat(corridas.limitesPedidos)
                .containsExactly(GetSyncStatusUseCaseImpl.ERRORES_EN_EL_REPORTE);
    }

    @Test
    @DisplayName("Una base recien inicializada se informa, no falla")
    void sinCatalogoAplicadoResponde() {
        // Es la diferencia con la busqueda y la agenda, que contestan 503
        // mientras no haya version local: acá es cuando el reporte mas sirve.
        origen.publicar(128, 120);

        SyncStatusReport reporte = useCase.getStatus();

        assertThat(reporte.localVersion()).isNull();
        assertThat(reporte.lastRun()).isNull();
        assertThat(reporte.health()).isEqualTo(SyncHealth.BEHIND);
    }

    @Test
    @DisplayName("Si Redis no responde, el reporte dice UNREACHABLE y no explota")
    void elOrigenCaidoNoRompeElReporte() {
        versiones.aplicada = version(128);
        corridas.ultima = corridaExitosa(128);
        origen.falla = new PublishedCatalogUnavailableException(
                "no se pudo conectar con el Redis de la catedra", new RuntimeException());

        SyncStatusReport reporte = useCase.getStatus();

        // "No puedo hablar con la catedra" ES la respuesta a la pregunta que
        // se hizo. Propagar la excepcion dejaria al servicio sin forma de
        // explicar por que esta degradado.
        assertThat(reporte.originState()).isEqualTo(SyncOriginState.UNREACHABLE);
        assertThat(reporte.origin()).isNull();
        assertThat(reporte.health()).isEqualTo(SyncHealth.DEGRADED);
        assertThat(reporte.versionsBehind()).isNull();
    }

    @Test
    @DisplayName("Una metadata que no cumple el contrato tambien deja el origen inalcanzable")
    void laMetadataMalFormadaEsIgualDeCiega() {
        versiones.aplicada = version(128);
        origen.falla = new MalformedPublishedCatalogException(
                "al hash catedra:sync:metadata le falta currentVersion");

        assertThat(useCase.getStatus().originState()).isEqualTo(SyncOriginState.UNREACHABLE);
    }

    @Test
    @DisplayName("Si la catedra no publico metadata, se distingue de no poder leerla")
    void distingueNoPublicadoDeInalcanzable() {
        versiones.aplicada = version(128);

        SyncStatusReport reporte = useCase.getStatus();

        // Las dos dejan el reporte sin version remota, pero una la arregla la
        // catedra publicando y la otra la red privada. Un unico nulo las
        // confundiria.
        assertThat(reporte.originState()).isEqualTo(SyncOriginState.NOT_PUBLISHED);
        assertThat(reporte.origin()).isNull();
    }

    @Test
    @DisplayName("Una excepcion que no es del origen se propaga")
    void loQueNoEsDelOrigenSePropaga() {
        // Taparlo todo haria que el endpoint informara un estado
        // tranquilizador sobre un servicio roto: un bug no es "la catedra
        // esta caida".
        origen.falla = new IllegalStateException("bug de mapeo");

        assertThatThrownBy(useCase::getStatus).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("El reporte no lee el catalogo publicado, solo su metadata")
    void noLeeElCatalogo() {
        // §6.2 prohibe resolver busquedas contra la catedra. Este endpoint no
        // es una busqueda, pero tampoco tiene por que traer entidades: con la
        // metadata alcanza, y los dobles fallan si se pide algo mas.
        origen.publicar(128, 120);

        useCase.getStatus();

        assertThat(origen.lecturasDeMetadata).isEqualTo(1);
    }

    private static CatalogVersion version(long numero) {
        return CatalogVersion.builder()
                .version(numero)
                .appliedAt(AHORA)
                .source(CatalogVersionSource.INCREMENTAL)
                .build();
    }

    private static SyncRun corridaExitosa(long hasta) {
        return SyncRun.builder()
                .id(412L)
                .mode(SyncMode.INCREMENTAL)
                .triggeredBy(SyncTrigger.EVENT)
                .decisionReason(SyncDecisionReason.BEHIND_CURRENT_VERSION)
                .status(SyncStatus.SUCCEEDED)
                .fromVersion(hasta - 1)
                .toVersion(hasta)
                .startedAt(AHORA.minusSeconds(2))
                .finishedAt(AHORA)
                .build();
    }

    private static SyncError error(SyncErrorCode codigo) {
        return SyncError.builder()
                .syncRunId(411L)
                .occurredAt(AHORA.minusSeconds(60))
                .code(codigo)
                .detail("la catedra no respondio")
                .build();
    }

    private static final class VersionLocal implements CatalogVersionRepository {

        private CatalogVersion aplicada;

        @Override
        public Optional<CatalogVersion> findCurrent() {
            return Optional.ofNullable(aplicada);
        }

        @Override
        public CatalogVersion advanceTo(long version, CatalogVersionSource source) {
            throw new AssertionError("El reporte de estado no avanza la version");
        }
    }

    private static final class Historial implements SyncRunRepository {

        private final List<Integer> limitesPedidos = new ArrayList<>();
        private SyncRun ultima;
        private List<SyncError> errores = List.of();

        @Override
        public Optional<SyncRun> findLast() {
            return Optional.ofNullable(ultima);
        }

        @Override
        public List<SyncError> findRecentErrors(int limit) {
            limitesPedidos.add(limit);
            return errores;
        }

        @Override
        public SyncRun start(SyncMode mode, SyncTrigger trigger, SyncDecisionReason reason,
                Long fromVersion) {
            throw new AssertionError("El reporte de estado no abre corridas");
        }

        @Override
        public SyncRun succeed(long runId, long toVersion) {
            throw new AssertionError("El reporte de estado no cierra corridas");
        }

        @Override
        public SyncRun fail(long runId, SyncErrorCode code, String detail) {
            throw new AssertionError("El reporte de estado no registra errores");
        }

        @Override
        public int purgeRunsFinishedBefore(Instant limite) {
            throw new AssertionError("Informar el estado no borra el historial");
        }
    }

    private static final class Origen implements PublishedCatalogPort {

        private PublishedCatalogMetadata metadata;
        private RuntimeException falla;
        private int lecturasDeMetadata;

        private void publicar(long actual, long masAntigua) {
            metadata = PublishedCatalogMetadata.builder()
                    .currentVersion(actual)
                    .oldestAvailableVersion(masAntigua)
                    .publishedAt(AHORA.minusSeconds(10))
                    .build();
        }

        @Override
        public Optional<PublishedCatalogMetadata> metadata() {
            lecturasDeMetadata++;
            if (falla != null) {
                throw falla;
            }
            return Optional.ofNullable(metadata);
        }

        @Override
        public Optional<Long> currentVersion() {
            throw new AssertionError("El reporte lee la metadata, que trae los dos numeros"
                    + " en una sola lectura");
        }

        @Override
        public Optional<Long> oldestAvailableVersion() {
            throw new AssertionError("idem currentVersion");
        }

        @Override
        public Optional<ProfessionalCategory> findCategory(long id) {
            throw new AssertionError("El reporte de estado no lee el catalogo");
        }

        @Override
        public Optional<Professional> findProfessional(long id) {
            throw new AssertionError("El reporte de estado no lee el catalogo");
        }

        @Override
        public Optional<WeeklySchedule> findSchedule(long id) {
            throw new AssertionError("El reporte de estado no lee el catalogo");
        }
    }
}

package com.um.catalogsync.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.model.AvailabilityCriteria;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.sync.CatalogSnapshotFixtures;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionDelta;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El <b>orden</b> de la unidad de trabajo de una version incremental, con
 * dobles de los cuatro puertos.
 *
 * <p>Se prueba sin base por el mismo motivo que la del snapshot: las FK lo
 * harian cumplir de todos modos, pero entonces la prueba demostraria algo de
 * PostgreSQL y no de esta clase. La prueba con base real
 * —{@code IncrementalSyncIntegrationTest}— verifica lo otro: que dos
 * versiones consecutivas dejen el estado y la version que corresponden.
 */
class ApplyCatalogVersionUseCaseImplTest {

    /** Lo que cada puerto fue registrando, en orden de llamada. */
    private final List<String> pasos = new ArrayList<>();

    private final CategoryRepository categorias = new CategoriasQueRegistran();
    private final ProfessionalRepository profesionales = new ProfesionalesQueRegistran();
    private final WeeklyScheduleRepository horarios = new HorariosQueRegistran();
    private final CatalogVersionRepository versiones = new VersionesQueRegistran();

    private final ApplyCatalogVersionUseCaseImpl useCase =
            new ApplyCatalogVersionUseCaseImpl(categorias, profesionales, horarios, versiones);

    @Test
    @DisplayName("Borra de hijo a padre, escribe de padre a hijo y avanza al final")
    void respetaElOrdenDeLasReferencias() {
        useCase.apply(CatalogVersionDelta.builder()
                .version(8L)
                .categories(List.of(CatalogSnapshotFixtures.categoria(1L, "Clinica", true)))
                .professionals(List.of(
                        CatalogSnapshotFixtures.profesional(15L, 1L, "Ana", "Gomez", true)))
                .schedules(List.of(
                        CatalogSnapshotFixtures.horario(21L, 15L, DayOfWeek.MONDAY, true)))
                .removedCategoryIds(List.of(2L))
                .removedProfessionalIds(List.of(16L))
                .removedScheduleIds(List.of(22L))
                .build());

        // Las FK son RESTRICT en los dos sentidos, asi que este orden no es
        // una preferencia: cualquier otro falla contra la base real. Y los
        // borrados van antes de las escrituras, porque una version puede
        // eliminar un profesional y crear otro en la misma categoria.
        assertThat(pasos).containsExactly(
                "horarios.deleteByIdIn([22])",
                "profesionales.deleteByIdIn([16])",
                "categorias.deleteByIdIn([2])",
                "categorias.upsert(1)",
                "profesionales.upsert(15)",
                "horarios.upsert(21)",
                "versiones.advanceTo(8, INCREMENTAL)");
    }

    @Test
    @DisplayName("La categoria se escribe antes que el profesional que la referencia")
    void laCategoriaNuevaLlegaAntesQueSuProfesional() {
        // Es el caso que #7 ya habia resuelto: un profesional cuya categoria
        // llega en la misma version. Sin este orden, el upsert del
        // profesional fallaria por una FK contra una categoria que todavia no
        // existe.
        useCase.apply(delta(8L,
                List.of(CatalogSnapshotFixtures.categoria(9L, "Nueva", true)),
                List.of(CatalogSnapshotFixtures.profesional(30L, 9L, "Luis", "Diaz", true)),
                List.of()));

        assertThat(pasos).containsExactly(
                "categorias.upsert(9)",
                "profesionales.upsert(30)",
                "versiones.advanceTo(8, INCREMENTAL)");
    }

    @Test
    @DisplayName("Una version sin cambios tambien avanza la version local")
    void laVersionVaciaAvanza() {
        // §14.5: una version con los tres arrays vacios es valida. Saltearla
        // dejaria la version local atras del origen para siempre, y el
        // proximo recorrido se encontraria con la misma clave vacia.
        CatalogVersion aplicada = useCase.apply(delta(8L, List.of(), List.of(), List.of()));

        assertThat(aplicada.getVersion()).isEqualTo(8L);
        assertThat(pasos).containsExactly("versiones.advanceTo(8, INCREMENTAL)");
    }

    @Test
    @DisplayName("El origen es INCREMENTAL y no SNAPSHOT")
    void elOrigenEsIncremental() {
        // Importa porque solo el origen SNAPSHOT puede retroceder el numero
        // de version. Una version incremental que retrocediera estaria
        // afirmando un estado que la copia local no tiene.
        useCase.apply(delta(8L, List.of(), List.of(), List.of()));

        assertThat(pasos).containsExactly("versiones.advanceTo(8, INCREMENTAL)");
    }

    @Test
    @DisplayName("Una entidad deshabilitada se escribe, no se borra")
    void laBajaLogicaEsUnaEscritura() {
        // §14.4: las deshabilitadas permanecen en los hashes. Llegan como
        // entidades con enabled en false y se aplican como cualquier otra
        // modificacion. Solo la ausencia del hash es una baja de verdad, y
        // esa viaja en las listas de ids removidos.
        useCase.apply(delta(8L,
                List.of(),
                List.of(CatalogSnapshotFixtures.profesional(15L, 1L, "Ana", "Gomez", false)),
                List.of()));

        assertThat(pasos).containsExactly(
                "profesionales.upsert(15)",
                "versiones.advanceTo(8, INCREMENTAL)");
    }

    private CatalogVersionDelta delta(
            long version,
            List<ProfessionalCategory> categorias,
            List<Professional> profesionales,
            List<WeeklySchedule> horarios) {
        return CatalogVersionDelta.builder()
                .version(version)
                .categories(categorias)
                .professionals(profesionales)
                .schedules(horarios)
                .removedCategoryIds(List.of())
                .removedProfessionalIds(List.of())
                .removedScheduleIds(List.of())
                .build();
    }

    // --- Dobles que solo registran el paso --------------------------------

    private final class CategoriasQueRegistran implements CategoryRepository {

        @Override
        public ProfessionalCategory upsert(ProfessionalCategory category) {
            pasos.add("categorias.upsert(" + category.getId() + ")");
            return category;
        }

        @Override
        public long deleteByIdIn(Collection<Long> ids) {
            if (!ids.isEmpty()) {
                pasos.add("categorias.deleteByIdIn(" + ids + ")");
            }
            return ids.size();
        }

        @Override
        public Optional<ProfessionalCategory> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<ProfessionalCategory> findAll() {
            return List.of();
        }

        @Override
        public List<ProfessionalCategory> replaceAll(Collection<ProfessionalCategory> cs) {
            pasos.add("categorias.replaceAll(" + cs.size() + ")");
            return List.copyOf(cs);
        }
    }

    private final class ProfesionalesQueRegistran implements ProfessionalRepository {

        @Override
        public Professional upsert(Professional professional) {
            pasos.add("profesionales.upsert(" + professional.getId() + ")");
            return professional;
        }

        @Override
        public long deleteByIdIn(Collection<Long> ids) {
            if (!ids.isEmpty()) {
                pasos.add("profesionales.deleteByIdIn(" + ids + ")");
            }
            return ids.size();
        }

        @Override
        public Optional<Professional> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<Professional> findAll() {
            return List.of();
        }

        @Override
        public ProfessionalPage search(ProfessionalSearchCriteria criteria) {
            return mock(ProfessionalPage.class);
        }

        @Override
        public List<Professional> replaceAll(Collection<Professional> ps) {
            pasos.add("profesionales.replaceAll(" + ps.size() + ")");
            return List.copyOf(ps);
        }
    }

    private final class HorariosQueRegistran implements WeeklyScheduleRepository {

        @Override
        public WeeklySchedule upsert(WeeklySchedule schedule) {
            pasos.add("horarios.upsert(" + schedule.getId() + ")");
            return schedule;
        }

        @Override
        public long deleteByIdIn(Collection<Long> ids) {
            if (!ids.isEmpty()) {
                pasos.add("horarios.deleteByIdIn(" + ids + ")");
            }
            return ids.size();
        }

        @Override
        public Optional<WeeklySchedule> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<WeeklySchedule> findByProfessionalId(Long professionalId) {
            return List.of();
        }

        @Override
        public List<WeeklySchedule> findAll() {
            return List.of();
        }

        @Override
        public List<Long> findProfessionalIdsWithAgendaIn(AvailabilityCriteria criteria) {
            return List.of();
        }

        @Override
        public List<WeeklySchedule> replaceAll(Collection<WeeklySchedule> ss) {
            pasos.add("horarios.replaceAll(" + ss.size() + ")");
            return List.copyOf(ss);
        }
    }

    private final class VersionesQueRegistran implements CatalogVersionRepository {

        @Override
        public Optional<CatalogVersion> findCurrent() {
            return Optional.empty();
        }

        @Override
        public CatalogVersion advanceTo(long version, CatalogVersionSource source) {
            pasos.add("versiones.advanceTo(" + version + ", " + source + ")");
            return CatalogVersion.builder()
                    .version(version)
                    .source(source)
                    .appliedAt(Instant.parse("2026-11-02T12:00:00Z"))
                    .build();
        }
    }
}

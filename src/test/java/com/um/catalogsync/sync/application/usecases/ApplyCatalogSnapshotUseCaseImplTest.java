package com.um.catalogsync.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.sync.CatalogSnapshotFixtures;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
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
 * El <b>orden</b> de la unidad de trabajo, con dobles de los cuatro
 * puertos.
 *
 * <p>Se prueba acá y no solo contra PostgreSQL porque el orden es una
 * decision de este caso de uso y tiene que poder verificarse sin base: las
 * FK la harian cumplir de todos modos, pero entonces la prueba demostraria
 * algo de la base y no de esta clase. La prueba con PostgreSQL real
 * —{@code FullSyncIntegrationTest}— verifica lo otro: que el rollback deja
 * la copia anterior intacta.
 */
class ApplyCatalogSnapshotUseCaseImplTest {

    /** Lo que cada puerto fue registrando, en orden de llamada. */
    private final List<String> pasos = new ArrayList<>();

    private final CategoryRepository categorias = new CategoriasQueRegistra();
    private final ProfessionalRepository profesionales = new ProfesionalesQueRegistra();
    private final WeeklyScheduleRepository horarios = new HorariosQueRegistra();
    private final CatalogVersionRepository versiones = new VersionesQueRegistra();

    private ApplyCatalogSnapshotUseCaseImpl useCase() {
        return new ApplyCatalogSnapshotUseCaseImpl(
                categorias, profesionales, horarios, versiones);
    }

    @Test
    @DisplayName("Borra de hijo a padre, escribe de padre a hijo y avanza la version al final")
    void respetaElOrdenDeLasReferencias() {
        useCase().apply(CatalogSnapshotFixtures.version(7L));

        // Las FK son RESTRICT en los dos sentidos, asi que este orden no es
        // una preferencia: cualquier otro falla contra la base real.
        assertThat(pasos).containsExactly(
                "horarios.replaceAll(0)",
                "profesionales.replaceAll(0)",
                "categorias.replaceAll(1)",
                "profesionales.replaceAll(1)",
                "horarios.replaceAll(1)",
                "versiones.advanceTo(7, SNAPSHOT)");
    }

    @Test
    @DisplayName("La version es lo ultimo: si falla un paso anterior, no se avanza")
    void noAvanzaLaVersionSiFallaUnPaso() {
        WeeklyScheduleRepository horariosQueFallan = new HorariosQueRegistra() {
            @Override
            public List<WeeklySchedule> replaceAll(Collection<WeeklySchedule> schedules) {
                super.replaceAll(schedules);
                if (!schedules.isEmpty()) {
                    throw new IllegalStateException("fallo al escribir horarios");
                }
                return List.of();
            }
        };
        var useCase = new ApplyCatalogSnapshotUseCaseImpl(
                categorias, profesionales, horariosQueFallan, versiones);

        assertThatThrownBy(() -> useCase.apply(CatalogSnapshotFixtures.version(7L)))
                .isInstanceOf(IllegalStateException.class);

        // El rollback de las escrituras anteriores lo hace la transaccion,
        // que acá no existe. Lo que esta prueba demuestra es lo otro: que
        // el avance de version nunca se intento.
        assertThat(pasos).doesNotContain("versiones.advanceTo(7, SNAPSHOT)");
    }

    @Test
    @DisplayName("El origen es SNAPSHOT, el unico que puede retroceder el numero")
    void avanzaConOrigenSnapshot() {
        CatalogVersion aplicada = useCase().apply(CatalogSnapshotFixtures.version(3L));

        // La version tiene que describir lo que la copia local tiene: si se
        // reemplazo entera con el snapshot 3, es la 3, aunque antes
        // estuvieramos en la 7 (§18.2).
        assertThat(aplicada.getVersion()).isEqualTo(3L);
        assertThat(aplicada.getSource()).isEqualTo(CatalogVersionSource.SNAPSHOT);
    }

    @Test
    @DisplayName("Un snapshot vacio se aplica igual y avanza la version")
    void snapshotVacio() {
        CatalogSnapshot vacio = CatalogSnapshotFixtures.vacio(0L);

        assertThat(useCase().apply(vacio).getVersion()).isZero();
        assertThat(pasos).endsWith("versiones.advanceTo(0, SNAPSHOT)");
    }

    @Test
    @DisplayName("No filtra las entidades deshabilitadas: las pasa a los puertos")
    void noFiltraLasDeshabilitadas() {
        useCase().apply(CatalogSnapshotFixtures.conDeshabilitadas(7L));

        // Dos de cada coleccion, no una: si filtrara por enabled, la
        // busqueda por habilitado no tendria nada que filtrar y una entidad
        // dada de baja desapareceria en lugar de aparecer deshabilitada.
        assertThat(pasos).contains(
                "categorias.replaceAll(2)",
                "profesionales.replaceAll(2)",
                "horarios.replaceAll(2)");
    }

    private class CategoriasQueRegistra implements CategoryRepository {
        @Override
        public ProfessionalCategory upsert(ProfessionalCategory category) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProfessionalCategory> findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProfessionalCategory> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProfessionalCategory> replaceAll(
                Collection<ProfessionalCategory> categories) {
            pasos.add("categorias.replaceAll(" + categories.size() + ")");
            return List.copyOf(categories);
        }

        @Override
        public long deleteByIdIn(Collection<Long> ids) {
            throw new UnsupportedOperationException();
        }
    }

    private class ProfesionalesQueRegistra implements ProfessionalRepository {
        @Override
        public Professional upsert(Professional professional) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Professional> findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Professional> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Professional> replaceAll(Collection<Professional> professionals) {
            pasos.add("profesionales.replaceAll(" + professionals.size() + ")");
            return List.copyOf(professionals);
        }

        @Override
        public long deleteByIdIn(Collection<Long> ids) {
            throw new UnsupportedOperationException();
        }
    }

    private class HorariosQueRegistra implements WeeklyScheduleRepository {
        @Override
        public WeeklySchedule upsert(WeeklySchedule schedule) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<WeeklySchedule> findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<WeeklySchedule> findByProfessionalId(Long professionalId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<WeeklySchedule> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<WeeklySchedule> replaceAll(Collection<WeeklySchedule> schedules) {
            pasos.add("horarios.replaceAll(" + schedules.size() + ")");
            return List.copyOf(schedules);
        }

        @Override
        public long deleteByIdIn(Collection<Long> ids) {
            throw new UnsupportedOperationException();
        }
    }

    private class VersionesQueRegistra implements CatalogVersionRepository {
        @Override
        public Optional<CatalogVersion> findCurrent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CatalogVersion advanceTo(long version, CatalogVersionSource source) {
            pasos.add("versiones.advanceTo(" + version + ", " + source + ")");
            return CatalogVersion.builder()
                    .version(version)
                    .appliedAt(Instant.parse("2026-09-10T14:00:00Z"))
                    .source(source)
                    .build();
        }
    }
}

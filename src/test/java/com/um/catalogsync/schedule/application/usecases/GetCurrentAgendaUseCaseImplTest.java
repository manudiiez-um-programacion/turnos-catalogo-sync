package com.um.catalogsync.schedule.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.application.exception.CatalogNotSynchronizedException;
import com.um.catalogsync.schedule.application.exception.ProfessionalDisabledException;
import com.um.catalogsync.schedule.domain.model.CurrentAgenda;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El armado de la agenda, sin base de datos.
 *
 * <p>Las tres reglas que agrega sobre los puertos se prueban aca porque no
 * necesitan PostgreSQL: el orden de los chequeos, el filtro de horarios
 * deshabilitados y el orden de la respuesta. Lo que si necesita base —que el
 * JSON salga bien y que la seguridad aplique— esta en
 * {@code CurrentAgendaApiIntegrationTest}.
 */
class GetCurrentAgendaUseCaseImplTest {

    private static final Long ID = 42L;

    private static Professional profesional(boolean habilitado) {
        return Professional.builder()
                .id(ID)
                .categoryId(7L)
                .firstName("Ana")
                .lastName("Gomez")
                .enabled(habilitado)
                .build();
    }

    private static WeeklySchedule horario(Long id, DayOfWeek dia, String desde, boolean habilitado) {
        return WeeklySchedule.builder()
                .id(id)
                .professionalId(ID)
                .dayOfWeek(dia)
                .startTime(LocalTime.parse(desde))
                .endTime(LocalTime.parse(desde).plusHours(2))
                .slotDurationMinutes(30)
                .enabled(habilitado)
                .build();
    }

    private static CatalogVersion version() {
        return CatalogVersion.builder()
                .version(128L)
                .appliedAt(Instant.parse("2026-10-27T18:30:00Z"))
                .source(CatalogVersionSource.SNAPSHOT)
                .build();
    }

    private static GetCurrentAgendaUseCaseImpl casoDeUso(
            Professional profesional, List<WeeklySchedule> horarios, CatalogVersion version) {

        return new GetCurrentAgendaUseCaseImpl(
                profesionalesQueDevuelven(profesional),
                horariosQueDevuelven(horarios),
                versionesQueDevuelven(version));
    }

    @Test
    @DisplayName("devuelve el profesional, sus horarios habilitados y la version aplicada")
    void agendaCompleta() {
        CurrentAgenda agenda =
                casoDeUso(
                                profesional(true),
                                List.of(
                                        horario(1L, DayOfWeek.MONDAY, "09:00", true),
                                        horario(2L, DayOfWeek.WEDNESDAY, "14:00", true)),
                                version())
                        .getByProfessional(ID);

        assertThat(agenda.professional().getId()).isEqualTo(ID);
        assertThat(agenda.schedules()).extracting(WeeklySchedule::getId).containsExactly(1L, 2L);
        assertThat(agenda.catalogVersion().getVersion()).isEqualTo(128L);
    }

    @Test
    @DisplayName("deja afuera los horarios deshabilitados: la copia los guarda, la agenda no los publica")
    void filtraLosDeshabilitados() {
        CurrentAgenda agenda =
                casoDeUso(
                                profesional(true),
                                List.of(
                                        horario(1L, DayOfWeek.MONDAY, "09:00", true),
                                        horario(2L, DayOfWeek.TUESDAY, "09:00", false)),
                                version())
                        .getByProfessional(ID);

        assertThat(agenda.schedules()).extracting(WeeklySchedule::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("ordena por dia y hora, aunque el repositorio los devuelva desordenados")
    void ordenEstable() {
        // El orden es parte del contrato: si cambia entre llamadas, el cliente
        // tiene que reordenar y cualquier comparacion de esquema se rompe.
        CurrentAgenda agenda =
                casoDeUso(
                                profesional(true),
                                List.of(
                                        horario(3L, DayOfWeek.FRIDAY, "08:00", true),
                                        horario(1L, DayOfWeek.MONDAY, "14:00", true),
                                        horario(2L, DayOfWeek.MONDAY, "09:00", true)),
                                version())
                        .getByProfessional(ID);

        assertThat(agenda.schedules()).extracting(WeeklySchedule::getId).containsExactly(2L, 1L, 3L);
    }

    @Test
    @DisplayName("un profesional habilitado sin horarios es una agenda vacia, no un error")
    void sinHorarios() {
        CurrentAgenda agenda =
                casoDeUso(profesional(true), List.of(), version()).getByProfessional(ID);

        assertThat(agenda.schedules()).isEmpty();
        assertThat(agenda.professional().getId()).isEqualTo(ID);
    }

    @Test
    @DisplayName("profesional inexistente: ProfessionalNotFoundException")
    void inexistente() {
        assertThatThrownBy(() -> casoDeUso(null, List.of(), version()).getByProfessional(ID))
                .isInstanceOf(ProfessionalNotFoundException.class);
    }

    @Test
    @DisplayName("profesional deshabilitado: excepcion propia, distinta de inexistente")
    void deshabilitado() {
        assertThatThrownBy(
                        () ->
                                casoDeUso(
                                                profesional(false),
                                                List.of(horario(1L, DayOfWeek.MONDAY, "09:00", true)),
                                                version())
                                        .getByProfessional(ID))
                .isInstanceOf(ProfessionalDisabledException.class);
    }

    @Test
    @DisplayName("sin version aplicada: falla antes de buscar al profesional")
    void sinVersion() {
        // El orden importa: con la base recien creada no hay version ni
        // profesionales, y un 404 diria que el profesional no existe cuando lo
        // que no existe es la copia local.
        assertThatThrownBy(() -> casoDeUso(profesional(true), List.of(), null).getByProfessional(ID))
                .isInstanceOf(CatalogNotSynchronizedException.class);
    }

    /** Devuelve el profesional dado, o vacio si es {@code null}. */
    private static ProfessionalRepository profesionalesQueDevuelven(Professional profesional) {
        return new ProfessionalRepository() {
            @Override
            public Optional<Professional> findById(Long id) {
                return Optional.ofNullable(profesional);
            }

            @Override
            public Professional upsert(Professional p) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Professional> findAll() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Professional> replaceAll(Collection<Professional> ps) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long deleteByIdIn(Collection<Long> ids) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static WeeklyScheduleRepository horariosQueDevuelven(List<WeeklySchedule> horarios) {
        return new WeeklyScheduleRepository() {
            @Override
            public List<WeeklySchedule> findByProfessionalId(Long professionalId) {
                return horarios;
            }

            @Override
            public WeeklySchedule upsert(WeeklySchedule schedule) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<WeeklySchedule> findById(Long id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<WeeklySchedule> findAll() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<WeeklySchedule> replaceAll(Collection<WeeklySchedule> schedules) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long deleteByIdIn(Collection<Long> ids) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static CatalogVersionRepository versionesQueDevuelven(CatalogVersion version) {
        return new CatalogVersionRepository() {
            @Override
            public Optional<CatalogVersion> findCurrent() {
                return Optional.ofNullable(version);
            }

            @Override
            public CatalogVersion advanceTo(long v, CatalogVersionSource source) {
                throw new UnsupportedOperationException();
            }
        };
    }
}

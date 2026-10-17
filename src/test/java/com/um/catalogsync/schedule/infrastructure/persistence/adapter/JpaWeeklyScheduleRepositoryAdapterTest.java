package com.um.catalogsync.schedule.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.schedule.application.exception.UnknownProfessionalException;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.TimeOfDay;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class JpaWeeklyScheduleRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant CREADO = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-10T13:40:00Z");

    private static final long CATEGORIA = 1L;
    private static final long PROFESIONAL_EXISTENTE = 15L;
    private static final long OTRO_PROFESIONAL = 16L;
    private static final long PROFESIONAL_INEXISTENTE = 777L;

    /** Los mismos instantes como OffsetDateTime, para los INSERT a mano. */
    private static final OffsetDateTime CREADO_TZ = CREADO.atOffset(ZoneOffset.UTC);
    private static final OffsetDateTime ACTUALIZADO_TZ = ACTUALIZADO.atOffset(ZoneOffset.UTC);

    @Autowired
    private WeeklyScheduleRepository weeklyScheduleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * La base llega vacia desde {@code AbstractPostgresIntegrationTest}: aca
     * se precarga la cadena entera de FK —categoria y dos profesionales—
     * porque son RESTRICT y sin ellos no entra ningun horario.
     */
    @BeforeEach
    void precargarLaCadenaDeFk() {
        jdbc.update("INSERT INTO professional_category "
                + "(id, name, enabled, created_at, updated_at) VALUES (?, ?, true, ?, ?)",
                CATEGORIA, "Clinica medica", CREADO_TZ, ACTUALIZADO_TZ);
        insertarProfesional(PROFESIONAL_EXISTENTE, "Ana", "Gomez");
        insertarProfesional(OTRO_PROFESIONAL, "Juan", "Perez");
    }

    private void insertarProfesional(long id, String nombre, String apellido) {
        jdbc.update("INSERT INTO professional (id, category_id, first_name, last_name, "
                + "search_name, enabled, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, true, ?, ?)",
                id, CATEGORIA, nombre, apellido,
                (nombre + " " + apellido).toLowerCase(java.util.Locale.ROOT),
                CREADO_TZ, ACTUALIZADO_TZ);
    }

    private static WeeklySchedule horario(Long id, DayOfWeek dia, String desde, String hasta) {
        return horario(id, PROFESIONAL_EXISTENTE, dia, desde, hasta);
    }

    private static WeeklySchedule horario(
            Long id, Long profesional, DayOfWeek dia, String desde, String hasta) {
        return WeeklySchedule.builder()
                .id(id)
                .professionalId(profesional)
                .dayOfWeek(dia)
                // Entran como texto del contrato a proposito: asi el camino
                // completo —parseo, persistencia, lectura— queda cubierto.
                .startTime(TimeOfDay.parse(desde))
                .endTime(TimeOfDay.parse(hasta))
                .slotDurationMinutes(30)
                .enabled(true)
                .createdAt(CREADO)
                .updatedAt(ACTUALIZADO)
                .build();
    }

    @Test
    @DisplayName("upsert inserta y conserva el id de la catedra")
    void upsertInserta() {
        WeeklySchedule guardado = weeklyScheduleRepository.upsert(
                horario(21L, DayOfWeek.MONDAY, "09:00:00", "12:00:00"));

        assertThat(guardado.getId()).isEqualTo(21L);
        assertThat(guardado.getProfessionalId()).isEqualTo(PROFESIONAL_EXISTENTE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM weekly_schedule", Integer.class))
                .isOne();
    }

    @Test
    @DisplayName("upsert sobre un id ya presente actualiza en lugar de duplicar")
    void upsertSobreIdExistenteActualiza() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00:00", "12:00:00"));

        weeklyScheduleRepository.upsert(WeeklySchedule.builder()
                .id(21L)
                .professionalId(PROFESIONAL_EXISTENTE)
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(18, 0))
                .slotDurationMinutes(45)
                .enabled(false)
                .createdAt(CREADO)
                .updatedAt(Instant.parse("2026-10-01T00:00:00Z"))
                .build());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM weekly_schedule", Integer.class))
                .isOne();
        WeeklySchedule leido = weeklyScheduleRepository.findById(21L).orElseThrow();
        assertThat(leido.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(leido.getSlotDurationMinutes()).isEqualTo(45);
        assertThat(leido.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("El dia se guarda en la base como el literal del contrato")
    void guardaElDiaComoLiteral() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.SATURDAY, "09:00", "12:00"));

        assertThat(jdbc.queryForObject(
                "SELECT day_of_week FROM weekly_schedule WHERE id = 21", String.class))
                .isEqualTo("SATURDAY");
    }

    @Test
    @DisplayName("Los dos formatos de hora del contrato terminan en la misma fila")
    void aceptaLosDosFormatosDeHora() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00:00", "12:00:00"));
        weeklyScheduleRepository.upsert(horario(22L, DayOfWeek.TUESDAY, "09:00", "12:00"));

        WeeklySchedule conSegundos = weeklyScheduleRepository.findById(21L).orElseThrow();
        WeeklySchedule sinSegundos = weeklyScheduleRepository.findById(22L).orElseThrow();

        assertThat(sinSegundos.getStartTime()).isEqualTo(conSegundos.getStartTime());
        assertThat(sinSegundos.getEndTime()).isEqualTo(conSegundos.getEndTime());
        assertThat(conSegundos.getStartTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("Un horario cuyo profesional no esta localmente se RECHAZA")
    void rechazaProfesionalDesconocido() {
        WeeklySchedule huerfano = horario(
                21L, PROFESIONAL_INEXISTENTE, DayOfWeek.MONDAY, "09:00", "12:00");

        assertThatThrownBy(() -> weeklyScheduleRepository.upsert(huerfano))
                .isInstanceOf(UnknownProfessionalException.class)
                .hasMessageContaining(String.valueOf(PROFESIONAL_INEXISTENTE));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM weekly_schedule", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("replaceAll tambien rechaza si alguno referencia un profesional que no esta")
    void replaceAllRechazaProfesionalDesconocido() {
        assertThatThrownBy(() -> weeklyScheduleRepository.replaceAll(List.of(
                horario(21L, DayOfWeek.MONDAY, "09:00", "12:00"),
                horario(22L, PROFESIONAL_INEXISTENTE, DayOfWeek.TUESDAY, "09:00", "12:00"))))
                .isInstanceOf(UnknownProfessionalException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM weekly_schedule", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Los CHECK de la tabla se propagan como error de integridad, sin disfrazarse")
    void losCheckNoSeDisfrazanDeFk() {
        WeeklySchedule alReves = horario(21L, DayOfWeek.MONDAY, "12:00", "09:00");

        assertThatThrownBy(() -> weeklyScheduleRepository.upsert(alReves))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(UnknownProfessionalException.class);
    }

    @Test
    @DisplayName("findById devuelve Optional vacio cuando no esta")
    void findByIdVacio() {
        assertThat(weeklyScheduleRepository.findById(999L)).isEmpty();
    }

    @Test
    @DisplayName("findByProfessionalId trae solo los del profesional pedido")
    void findByProfessionalIdFiltra() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00", "12:00"));
        weeklyScheduleRepository.upsert(horario(22L, DayOfWeek.TUESDAY, "09:00", "12:00"));
        weeklyScheduleRepository.upsert(
                horario(23L, OTRO_PROFESIONAL, DayOfWeek.MONDAY, "09:00", "12:00"));

        assertThat(weeklyScheduleRepository.findByProfessionalId(PROFESIONAL_EXISTENTE))
                .extracting(WeeklySchedule::getId)
                .containsExactlyInAnyOrder(21L, 22L);
    }

    @Test
    @DisplayName("findByProfessionalId de alguien sin horarios o inexistente devuelve vacio")
    void findByProfessionalIdSinResultados() {
        assertThat(weeklyScheduleRepository.findByProfessionalId(PROFESIONAL_EXISTENTE)).isEmpty();
        assertThat(weeklyScheduleRepository.findByProfessionalId(PROFESIONAL_INEXISTENTE))
                .isEmpty();
        assertThat(weeklyScheduleRepository.findByProfessionalId(null)).isEmpty();
    }

    @Test
    @DisplayName("findAll devuelve el modelo de dominio, nunca la entidad JPA")
    void findAllDevuelveDominio() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00", "12:00"));
        weeklyScheduleRepository.upsert(horario(22L, DayOfWeek.FRIDAY, "09:00", "12:00"));

        List<WeeklySchedule> todos = weeklyScheduleRepository.findAll();

        assertThat(todos).hasSize(2)
                .allSatisfy(h -> assertThat(h).isExactlyInstanceOf(WeeklySchedule.class))
                .extracting(WeeklySchedule::getDayOfWeek)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("replaceAll deja solo el contenido nuevo")
    void replaceAllReemplazaTodo() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00", "12:00"));
        weeklyScheduleRepository.upsert(horario(22L, DayOfWeek.TUESDAY, "09:00", "12:00"));

        weeklyScheduleRepository.replaceAll(
                List.of(horario(23L, DayOfWeek.WEDNESDAY, "14:00", "18:00")));

        assertThat(weeklyScheduleRepository.findAll())
                .extracting(WeeklySchedule::getId)
                .containsExactly(23L);
    }

    @Test
    @DisplayName("deleteByIdIn borra solo los indicados y devuelve cuantos borro")
    void deleteByIdInBorraLosIndicados() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00", "12:00"));
        weeklyScheduleRepository.upsert(horario(22L, DayOfWeek.TUESDAY, "09:00", "12:00"));

        assertThat(weeklyScheduleRepository.deleteByIdIn(List.of(21L))).isEqualTo(1L);
        assertThat(weeklyScheduleRepository.findAll())
                .extracting(WeeklySchedule::getId)
                .containsExactly(22L);
    }

    @Test
    @DisplayName("deleteByIdIn con vacio o null no borra nada ni falla")
    void deleteByIdInConVacioNoHaceNada() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00", "12:00"));

        assertThat(weeklyScheduleRepository.deleteByIdIn(List.of())).isZero();
        assertThat(weeklyScheduleRepository.deleteByIdIn(null)).isZero();
        assertThat(weeklyScheduleRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Los timestamps de la catedra se guardan y se leen sin corrimiento")
    void conservaLosTimestamps() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00", "12:00"));

        WeeklySchedule leido = weeklyScheduleRepository.findById(21L).orElseThrow();

        assertThat(leido.getCreatedAt()).isEqualTo(CREADO);
        assertThat(leido.getUpdatedAt()).isEqualTo(ACTUALIZADO);
    }

    @Test
    @DisplayName("Las horas se leen sin corrimiento, aunque la JVM no este en UTC")
    void conservaLasHoras() {
        weeklyScheduleRepository.upsert(horario(21L, DayOfWeek.MONDAY, "09:00:00", "12:30:00"));

        WeeklySchedule leido = weeklyScheduleRepository.findById(21L).orElseThrow();

        assertThat(leido.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(leido.getEndTime()).isEqualTo(LocalTime.of(12, 30));
    }
}

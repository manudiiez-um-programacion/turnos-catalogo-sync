package com.um.catalogsync.schedule.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.UnknownDayOfWeekException;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.infrastructure.persistence.entity.WeeklyScheduleEntity;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WeeklyScheduleMapperTest {

    private static final Instant CREADO = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-10T13:40:00Z");

    private final WeeklyScheduleMapper mapper = new WeeklyScheduleMapper();

    /** El horario del ejemplo del contrato (§7). */
    private static WeeklySchedule lunesDeAna() {
        return WeeklySchedule.builder()
                .id(21L)
                .professionalId(15L)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(30)
                .enabled(true)
                .createdAt(CREADO)
                .updatedAt(ACTUALIZADO)
                .build();
    }

    @Test
    @DisplayName("Ida y vuelta conserva los campos del contrato")
    void idaYVueltaConservaLosCampos() {
        WeeklySchedule original = lunesDeAna();

        WeeklySchedule resultado = mapper.toDomain(mapper.toEntity(original));

        assertThat(resultado).usingRecursiveComparison().isEqualTo(original);
    }

    @ParameterizedTest
    @EnumSource(DayOfWeek.class)
    @DisplayName("Los siete dias viajan en los dos sentidos sin perderse")
    void todosLosDiasIdaYVuelta(DayOfWeek dia) {
        WeeklySchedule original = lunesDeAna();
        original.setDayOfWeek(dia);

        WeeklyScheduleEntity entity = mapper.toEntity(original);

        assertThat(entity.getDayOfWeek()).isEqualTo(dia.name());
        assertThat(mapper.toDomain(entity).getDayOfWeek()).isSameAs(dia);
    }

    @Test
    @DisplayName("El dia se guarda como el literal del contrato, no como un ordinal")
    void elDiaSeGuardaComoTexto() {
        assertThat(mapper.toEntity(lunesDeAna()).getDayOfWeek()).isEqualTo("MONDAY");
    }

    @Test
    @DisplayName("Un dia desconocido en la base falla al mapear, no se cuela como null")
    void unDiaDesconocidoEnLaBaseFalla() {
        WeeklyScheduleEntity corrupta = mapper.toEntity(lunesDeAna());
        corrupta.setDayOfWeek("FUNDAY");

        assertThatThrownBy(() -> mapper.toDomain(corrupta))
                .isInstanceOf(UnknownDayOfWeekException.class)
                .hasMessageContaining("FUNDAY");
    }

    @Test
    @DisplayName("Las horas no se tocan: son LocalTime a los dos lados")
    void conservaLasHoras() {
        WeeklyScheduleEntity entity = mapper.toEntity(lunesDeAna());

        assertThat(entity.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entity.getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("Con null devuelve null, y con colecciones null devuelve lista vacia")
    void toleraNull() {
        assertThat(mapper.toDomain((WeeklyScheduleEntity) null)).isNull();
        assertThat(mapper.toEntity((WeeklySchedule) null)).isNull();
        assertThat(mapper.toDomain((List<WeeklyScheduleEntity>) null)).isEmpty();
        assertThat(mapper.toEntity((List<WeeklySchedule>) null)).isEmpty();
    }

    @Test
    @DisplayName("Un horario sin dia va a la entidad con null y lo rechaza la base, no el mapper")
    void unHorarioSinDiaNoRevientaAlIr() {
        WeeklySchedule sinDia = lunesDeAna();
        sinDia.setDayOfWeek(null);

        // El NOT NULL de la columna es quien tiene que quejarse: el mapper no
        // inventa un dia por defecto ni falla antes de llegar a la base.
        assertThat(mapper.toEntity(sinDia).getDayOfWeek()).isNull();
    }
}

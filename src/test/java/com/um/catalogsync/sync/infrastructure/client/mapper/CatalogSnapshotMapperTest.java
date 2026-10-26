package com.um.catalogsync.sync.infrastructure.client.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.InvalidTimeOfDayException;
import com.um.catalogsync.schedule.domain.model.UnknownDayOfWeekException;
import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.infrastructure.client.dto.CatalogSnapshotDto;
import com.um.catalogsync.sync.infrastructure.client.dto.ProfessionalCategoryDto;
import com.um.catalogsync.sync.infrastructure.client.dto.ProfessionalDto;
import com.um.catalogsync.sync.infrastructure.client.dto.WeeklyScheduleDto;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El mapeo del snapshot al dominio, y sobre todo lo que el mapper se niega
 * a mapear.
 *
 * <p>Las pruebas de ausencia son las importantes. El adaptador ya se prueba
 * contra WireMock con el ejemplo de §7; acá lo que se ejerce es cada campo
 * faltante por separado, que contra un doble HTTP seria un archivo de JSON
 * por caso.
 */
class CatalogSnapshotMapperTest {

    private static final Instant CREADO = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-10T13:40:00Z");
    private static final Instant GENERADO = Instant.parse("2026-09-10T13:44:59Z");

    private final CatalogSnapshotMapper mapper = new CatalogSnapshotMapper();

    @Test
    @DisplayName("Sin cuerpo devuelve null y deja decidir al adaptador")
    void sinCuerpo() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("Mapea las tres colecciones del ejemplo de §7")
    void mapeaElEjemploDeLaReferencia() {
        CatalogSnapshot snapshot = mapper.toDomain(snapshotCompleto());

        assertThat(snapshot.getSnapshotVersion()).isEqualTo(7L);
        assertThat(snapshot.getGeneratedAt()).isEqualTo(GENERADO);
        assertThat(snapshot.getCategories()).singleElement().satisfies(categoria -> {
            assertThat(categoria.getId()).isEqualTo(1L);
            assertThat(categoria.getName()).isEqualTo("Clinica medica");
            assertThat(categoria.getDescription()).isEqualTo("Atencion clinica general");
            assertThat(categoria.isEnabled()).isTrue();
            assertThat(categoria.getCreatedAt()).isEqualTo(CREADO);
            assertThat(categoria.getUpdatedAt()).isEqualTo(ACTUALIZADO);
        });
        assertThat(snapshot.getProfessionals()).singleElement().satisfies(profesional -> {
            assertThat(profesional.getId()).isEqualTo(15L);
            assertThat(profesional.getCategoryId()).isEqualTo(1L);
            assertThat(profesional.getFirstName()).isEqualTo("Ana");
            assertThat(profesional.getLastName()).isEqualTo("Gomez");
            assertThat(profesional.isEnabled()).isTrue();
        });
        assertThat(snapshot.getSchedules()).singleElement().satisfies(horario -> {
            assertThat(horario.getId()).isEqualTo(21L);
            assertThat(horario.getProfessionalId()).isEqualTo(15L);
            assertThat(horario.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(horario.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(horario.getEndTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(horario.getSlotDurationMinutes()).isEqualTo(30);
            assertThat(horario.isEnabled()).isTrue();
        });
    }

    @Test
    @DisplayName("Las colecciones vacias son un snapshot valido")
    void coleccionesVacias() {
        CatalogSnapshotDto dto = new CatalogSnapshotDto(
                0L, GENERADO, List.of(), List.of(), List.of());

        CatalogSnapshot snapshot = mapper.toDomain(dto);

        // Una catedra recien instalada no tiene catalogo, y eso no es un
        // error: es la version 0 con tres colecciones vacias.
        assertThat(snapshot.getSnapshotVersion()).isZero();
        assertThat(snapshot.getCategories()).isEmpty();
        assertThat(snapshot.getProfessionals()).isEmpty();
        assertThat(snapshot.getSchedules()).isEmpty();
    }

    @Test
    @DisplayName("Una coleccion ausente NO es una coleccion vacia")
    void coleccionAusente() {
        // La distincion que protege la copia local: si "professionals"
        // ausente se tomara como vacia, #13 borraria todos los
        // profesionales al aplicar el snapshot.
        CatalogSnapshotDto dto = new CatalogSnapshotDto(
                7L, GENERADO, List.of(), null, List.of());

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("professionals")
                .hasMessageContaining("borraria");
    }

    @Test
    @DisplayName("Falta snapshotVersion: no se inventa la version 0")
    void sinVersion() {
        CatalogSnapshotDto dto = new CatalogSnapshotDto(
                null, GENERADO, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("snapshotVersion");
    }

    @Test
    @DisplayName("Falta generatedAt")
    void sinGeneratedAt() {
        CatalogSnapshotDto dto = new CatalogSnapshotDto(
                7L, null, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("generatedAt");
    }

    @Test
    @DisplayName("Un elemento nulo en una coleccion se rechaza")
    void elementoNulo() {
        CatalogSnapshotDto dto = new CatalogSnapshotDto(
                7L, GENERADO, Arrays.asList((ProfessionalCategoryDto) null),
                List.of(), List.of());

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("professionalCategories");
    }

    @Test
    @DisplayName("Falta enabled de un profesional: el mensaje nombra el id de la catedra")
    void sinEnabled() {
        CatalogSnapshotDto dto = conProfesional(new ProfessionalDto(
                15L, 1L, "Ana", "Gomez", null, CREADO, ACTUALIZADO));

        // El id de la catedra y no el indice de la lista: es lo que sirve
        // para ir a mirar el dato del otro lado.
        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("professionals[15].enabled");
    }

    @Test
    @DisplayName("enabled false se mapea; no se confunde con ausente")
    void deshabilitadoSeMapea() {
        CatalogSnapshotDto dto = conProfesional(new ProfessionalDto(
                15L, 1L, "Ana", "Gomez", false, CREADO, ACTUALIZADO));

        assertThat(mapper.toDomain(dto).getProfessionals())
                .singleElement()
                .satisfies(profesional -> assertThat(profesional.isEnabled()).isFalse());
    }

    @Test
    @DisplayName("Un nombre en blanco cuenta como ausente")
    void nombreEnBlanco() {
        // "" pasaria el NOT NULL de la tabla y dejaria un profesional sin
        // nombre en la busqueda: el mismo problema con otra forma.
        CatalogSnapshotDto dto = conProfesional(new ProfessionalDto(
                15L, 1L, "   ", "Gomez", true, CREADO, ACTUALIZADO));

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("professionals[15].firstName");
    }

    @Test
    @DisplayName("Falta createdAt de una categoria")
    void sinCreatedAt() {
        CatalogSnapshotDto dto = new CatalogSnapshotDto(
                7L, GENERADO,
                List.of(new ProfessionalCategoryDto(
                        1L, "Clinica medica", null, true, null, ACTUALIZADO)),
                List.of(), List.of());

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("professionalCategories[1].createdAt");
    }

    @Test
    @DisplayName("description ausente es legitimo: es el unico campo opcional")
    void sinDescripcion() {
        CatalogSnapshotDto dto = new CatalogSnapshotDto(
                7L, GENERADO,
                List.of(new ProfessionalCategoryDto(
                        1L, "Dermatologia", null, false, CREADO, ACTUALIZADO)),
                List.of(), List.of());

        assertThat(mapper.toDomain(dto).getCategories())
                .singleElement()
                .satisfies(categoria -> assertThat(categoria.getDescription()).isNull());
    }

    @Test
    @DisplayName("Una hora sin segundos se acepta, como obliga §4")
    void horaSinSegundos() {
        CatalogSnapshotDto dto = conHorario(new WeeklyScheduleDto(
                21L, 15L, "SATURDAY", "08:30", "11:30", 45, false, CREADO, ACTUALIZADO));

        assertThat(mapper.toDomain(dto).getSchedules())
                .singleElement()
                .satisfies(horario -> {
                    assertThat(horario.getStartTime()).isEqualTo(LocalTime.of(8, 30));
                    assertThat(horario.getEndTime()).isEqualTo(LocalTime.of(11, 30));
                });
    }

    @Test
    @DisplayName("Un dia que el contrato no enumera falla con su propia excepcion")
    void diaDesconocido() {
        CatalogSnapshotDto dto = conHorario(new WeeklyScheduleDto(
                21L, 15L, "LUNES", "09:00:00", "12:00:00", 30, true, CREADO, ACTUALIZADO));

        // No se absorbe como campo faltante: UnknownDayOfWeekException dice
        // que valor llego, que es la senal de que el contrato cambio.
        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(UnknownDayOfWeekException.class)
                .hasMessageContaining("LUNES");
    }

    @Test
    @DisplayName("Una hora con milisegundos falla: no esta en el contrato")
    void horaConMilisegundos() {
        CatalogSnapshotDto dto = conHorario(new WeeklyScheduleDto(
                21L, 15L, "MONDAY", "09:00:00.123", "12:00:00", 30, true, CREADO, ACTUALIZADO));

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(InvalidTimeOfDayException.class);
    }

    @Test
    @DisplayName("Falta slotDurationMinutes de un horario")
    void sinDuracionDeSlot() {
        CatalogSnapshotDto dto = conHorario(new WeeklyScheduleDto(
                21L, 15L, "MONDAY", "09:00:00", "12:00:00", null, true, CREADO, ACTUALIZADO));

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("weeklySchedules[21].slotDurationMinutes");
    }

    @Test
    @DisplayName("Falta el id: se nombra la coleccion, porque no hay id que nombrar")
    void sinId() {
        CatalogSnapshotDto dto = conProfesional(new ProfessionalDto(
                null, 1L, "Ana", "Gomez", true, CREADO, ACTUALIZADO));

        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("professionals.id");
    }

    private static CatalogSnapshotDto snapshotCompleto() {
        return new CatalogSnapshotDto(
                7L,
                GENERADO,
                List.of(new ProfessionalCategoryDto(
                        1L, "Clinica medica", "Atencion clinica general",
                        true, CREADO, ACTUALIZADO)),
                List.of(new ProfessionalDto(
                        15L, 1L, "Ana", "Gomez", true, CREADO, ACTUALIZADO)),
                List.of(new WeeklyScheduleDto(
                        21L, 15L, "MONDAY", "09:00:00", "12:00:00",
                        30, true, CREADO, ACTUALIZADO)));
    }

    private static CatalogSnapshotDto conProfesional(ProfessionalDto profesional) {
        return new CatalogSnapshotDto(
                7L, GENERADO, List.of(), List.of(profesional), List.of());
    }

    private static CatalogSnapshotDto conHorario(WeeklyScheduleDto horario) {
        return new CatalogSnapshotDto(
                7L, GENERADO, List.of(), List.of(), List.of(horario));
    }
}

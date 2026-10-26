package com.um.catalogsync.sync;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * Snapshots de dominio para las pruebas de la sincronizacion completa.
 *
 * <p>Son los valores del ejemplo de §7, igual que
 * {@code SnapshotFixtures} para el adaptador, pero ya como modelo de
 * dominio: acá lo que se prueba es la unidad de trabajo y no el parseo, asi
 * que armar los objetos directo evita que un error de JSON se confunda con
 * un error de aplicacion.
 */
public final class CatalogSnapshotFixtures {

    public static final Instant CREADO = Instant.parse("2026-07-01T12:00:00Z");
    public static final Instant ACTUALIZADO = Instant.parse("2026-09-10T13:40:00Z");
    public static final Instant GENERADO = Instant.parse("2026-09-10T13:44:59Z");

    private CatalogSnapshotFixtures() {
    }

    /**
     * El snapshot del ejemplo de §7: una categoria, un profesional y un
     * horario, los tres habilitados.
     */
    public static CatalogSnapshot version(long snapshotVersion) {
        return CatalogSnapshot.builder()
                .snapshotVersion(snapshotVersion)
                .generatedAt(GENERADO)
                .categories(List.of(categoria(1L, "Clinica medica", true)))
                .professionals(List.of(profesional(15L, 1L, "Ana", "Gomez", true)))
                .schedules(List.of(horario(21L, 15L, DayOfWeek.MONDAY, true)))
                .build();
    }

    /**
     * Un snapshot con una segunda entidad deshabilitada en cada coleccion.
     *
     * <p>El snapshot devuelve habilitadas y deshabilitadas (§7) y la copia
     * local tiene que conservar las dos: una entidad deshabilitada es una
     * baja logica (§14.4), no una fila que se descarte.
     */
    public static CatalogSnapshot conDeshabilitadas(long snapshotVersion) {
        return CatalogSnapshot.builder()
                .snapshotVersion(snapshotVersion)
                .generatedAt(GENERADO)
                .categories(List.of(
                        categoria(1L, "Clinica medica", true),
                        categoria(2L, "Dermatologia", false)))
                .professionals(List.of(
                        profesional(15L, 1L, "Ana", "Gomez", true),
                        profesional(16L, 2L, "Luis", "Diaz", false)))
                .schedules(List.of(
                        horario(21L, 15L, DayOfWeek.MONDAY, true),
                        horario(22L, 16L, DayOfWeek.SATURDAY, false)))
                .build();
    }

    /** Las tres colecciones vacias: una catedra recien instalada. */
    public static CatalogSnapshot vacio(long snapshotVersion) {
        return CatalogSnapshot.builder()
                .snapshotVersion(snapshotVersion)
                .generatedAt(GENERADO)
                .categories(List.of())
                .professionals(List.of())
                .schedules(List.of())
                .build();
    }

    /**
     * Un snapshot que se contradice: el horario apunta a un profesional que
     * el snapshot no trae.
     *
     * <p>Es el escenario que fuerza el fallo en el <b>ultimo</b> paso de la
     * unidad de trabajo, cuando las categorias y los profesionales ya se
     * reemplazaron. Sirve para demostrar que el rollback deja la copia
     * anterior intacta y la version sin tocar.
     */
    public static CatalogSnapshot conHorarioHuerfano(long snapshotVersion) {
        return CatalogSnapshot.builder()
                .snapshotVersion(snapshotVersion)
                .generatedAt(GENERADO)
                .categories(List.of(categoria(1L, "Clinica medica", true)))
                .professionals(List.of(profesional(15L, 1L, "Ana", "Gomez", true)))
                .schedules(List.of(horario(21L, 999L, DayOfWeek.MONDAY, true)))
                .build();
    }

    /**
     * Un snapshot que se contradice un nivel mas arriba: el profesional
     * apunta a una categoria que el snapshot no trae.
     *
     * <p>Falla en el paso 4 de los cinco, asi que las categorias ya se
     * reemplazaron cuando estalla. Igual que el anterior, nada debe quedar.
     */
    public static CatalogSnapshot conProfesionalHuerfano(long snapshotVersion) {
        return CatalogSnapshot.builder()
                .snapshotVersion(snapshotVersion)
                .generatedAt(GENERADO)
                .categories(List.of(categoria(1L, "Clinica medica", true)))
                .professionals(List.of(profesional(15L, 999L, "Ana", "Gomez", true)))
                .schedules(List.of())
                .build();
    }

    public static ProfessionalCategory categoria(Long id, String nombre, boolean habilitada) {
        return ProfessionalCategory.builder()
                .id(id)
                .name(nombre)
                .description("Descripcion de " + nombre)
                .enabled(habilitada)
                .createdAt(CREADO)
                .updatedAt(ACTUALIZADO)
                .build();
    }

    public static Professional profesional(
            Long id, Long categoriaId, String nombre, String apellido, boolean habilitado) {
        return Professional.builder()
                .id(id)
                .categoryId(categoriaId)
                .firstName(nombre)
                .lastName(apellido)
                .enabled(habilitado)
                .createdAt(CREADO)
                .updatedAt(ACTUALIZADO)
                .build();
    }

    public static WeeklySchedule horario(
            Long id, Long profesionalId, DayOfWeek dia, boolean habilitado) {
        return WeeklySchedule.builder()
                .id(id)
                .professionalId(profesionalId)
                .dayOfWeek(dia)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(30)
                .enabled(habilitado)
                .createdAt(CREADO)
                .updatedAt(ACTUALIZADO)
                .build();
    }
}

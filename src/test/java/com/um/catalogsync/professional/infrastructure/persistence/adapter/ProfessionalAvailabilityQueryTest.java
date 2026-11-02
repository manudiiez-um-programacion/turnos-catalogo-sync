package com.um.catalogsync.professional.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.model.AvailabilityCriteria;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El filtro de disponibilidad contra PostgreSQL real.
 *
 * <p>Es la prueba que define el criterio de #16 en terminos verificables. El
 * fixture esta armado para que cada caso borde del issue tenga exactamente un
 * profesional que lo represente, asi que si alguno de los tres descartes deja
 * de funcionar, falla una asercion y no varias:
 *
 * <ul>
 *   <li>Diego no tiene ningun horario;
 *   <li>Elsa tiene un horario el lunes, pero deshabilitado;
 *   <li>Fabio esta deshabilitado y tiene un horario habilitado el lunes.
 * </ul>
 *
 * <p>Los tres cumplirian el filtro si el criterio fuera mas laxo, y ninguno
 * tiene que aparecer. El tercero es el que mas importa: es la mitad del
 * criterio que el slice de horarios no puede aplicar, porque no sabe nada del
 * estado del profesional.
 *
 * <p><b>Lo que esta prueba deliberadamente no verifica</b> es que el filtro
 * sepa si queda cupo. No puede: aca no hay ocupaciones ni reservas. Responde
 * "tiene agenda" y eso es todo lo que puede responder.
 */
class ProfessionalAvailabilityQueryTest extends AbstractPostgresIntegrationTest {

    private static final long CLINICA = 1L;
    private static final long DERMATOLOGIA = 2L;

    private static final Instant CUANDO = Instant.parse("2026-09-10T13:40:00Z");
    private static final OffsetDateTime CUANDO_TZ = CUANDO.atOffset(ZoneOffset.UTC);

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private WeeklyScheduleRepository horarios;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void dadoUnCatalogoConAgenda() {
        categoria(CLINICA, "Clinica medica");
        categoria(DERMATOLOGIA, "Dermatologia");

        // Los que si atienden.
        profesionales.upsert(profesional(10L, "Ana", "Gomez", CLINICA, true));
        horarios.upsert(horario(100L, 10L, DayOfWeek.MONDAY, "09:00", "13:00", true));

        profesionales.upsert(profesional(11L, "Bruno", "Lopez", CLINICA, true));
        horarios.upsert(horario(110L, 11L, DayOfWeek.MONDAY, "14:00", "18:00", true));

        profesionales.upsert(profesional(12L, "Carla", "Mendez", CLINICA, true));
        horarios.upsert(horario(120L, 12L, DayOfWeek.TUESDAY, "09:00", "13:00", true));

        profesionales.upsert(profesional(20L, "Gabi", "Quiroga", DERMATOLOGIA, true));
        horarios.upsert(horario(200L, 20L, DayOfWeek.MONDAY, "09:00", "13:00", true));

        // Los tres casos borde, uno por descarte.
        profesionales.upsert(profesional(13L, "Diego", "Nieto", CLINICA, true));

        profesionales.upsert(profesional(14L, "Elsa", "Ortiz", CLINICA, true));
        horarios.upsert(horario(140L, 14L, DayOfWeek.MONDAY, "09:00", "13:00", false));

        profesionales.upsert(profesional(15L, "Fabio", "Perez", CLINICA, false));
        horarios.upsert(horario(150L, 15L, DayOfWeek.MONDAY, "09:00", "13:00", true));
    }

    @Nested
    @DisplayName("El criterio de disponibilidad")
    class Criterio {

        @Test
        @DisplayName("por dia: trae solo a los habilitados con horario habilitado ese dia")
        void porDia() {
            assertThat(ids(losQueAtienden(DayOfWeek.MONDAY, null, null)))
                    .containsExactlyInAnyOrder(10L, 11L, 20L);
        }

        @Test
        @DisplayName("un profesional sin horarios no esta disponible nunca")
        void sinHorarios() {
            // Diego existe, esta habilitado y no tiene agenda: no atiende
            // ningun dia de la semana.
            for (DayOfWeek dia : DayOfWeek.values()) {
                assertThat(ids(losQueAtienden(dia, null, null))).doesNotContain(13L);
            }
        }

        @Test
        @DisplayName("un horario deshabilitado no cuenta como agenda vigente")
        void conHorarioDeshabilitado() {
            // El horario de Elsa cae justo el lunes a la mañana, y aun asi no
            // aparece: §14.4 dice que la copia conserva las bajas logicas,
            // pero una baja logica no es agenda.
            assertThat(ids(losQueAtienden(DayOfWeek.MONDAY, null, null))).doesNotContain(14L);
        }

        @Test
        @DisplayName("un profesional deshabilitado no esta disponible aunque tenga agenda")
        void profesionalDeshabilitadoConAgenda() {
            // Es la mitad del criterio que el slice de horarios no puede
            // aplicar: su consulta devuelve el id de Fabio, porque el horario
            // esta habilitado, y el filtro de profesionales lo descarta.
            assertThat(ids(losQueAtienden(DayOfWeek.MONDAY, null, null))).doesNotContain(15L);
        }
    }

    @Nested
    @DisplayName("El rango horario")
    class Rango {

        @Test
        @DisplayName("se cruza por solapamiento, no por contencion")
        void porSolapamiento() {
            // 10:00-11:00 esta dentro del horario de Ana (09:00-13:00), que no
            // esta contenido en el rango. Exigir contencion la dejaria afuera
            // y seria responder a una pregunta que nadie hace.
            assertThat(ids(losQueAtienden(DayOfWeek.MONDAY, "10:00", "11:00")))
                    .containsExactlyInAnyOrder(10L, 20L);
        }

        @Test
        @DisplayName("un rango que toca los dos horarios trae los dos")
        void tocaLosDos() {
            // 12:00-15:00 se solapa con la mañana de Ana y con la tarde de
            // Bruno.
            assertThat(ids(losQueAtienden(DayOfWeek.MONDAY, "12:00", "15:00")))
                    .containsExactlyInAnyOrder(10L, 11L, 20L);
        }

        @Test
        @DisplayName("el borde exacto no cuenta: una interseccion de duracion cero no es atencion")
        void elBordeExactoNoCuenta() {
            // El horario de Ana termina 13:00 y el de Bruno empieza 14:00, asi
            // que el hueco del mediodia no tiene a nadie. Con desigualdades no
            // estrictas los dos aparecerian por tocarse en un instante.
            assertThat(losQueAtienden(DayOfWeek.MONDAY, "13:00", "14:00").content()).isEmpty();
        }

        @Test
        @DisplayName("sin dia, el rango vale para cualquier dia de la semana")
        void sinDia() {
            // El unico que atiende de 14 a 18 es Bruno, el lunes. Carla
            // atiende el martes pero a la mañana.
            assertThat(ids(losQueAtienden(null, "14:00", "18:00"))).containsExactly(11L);
            assertThat(ids(losQueAtienden(null, "09:00", "10:00")))
                    .containsExactlyInAnyOrder(10L, 12L, 20L);
        }

        @Test
        @DisplayName("una franja en la que nadie atiende devuelve pagina vacia, no un error")
        void franjaSinNadie() {
            ProfessionalPage pagina = losQueAtienden(DayOfWeek.SUNDAY, "03:00", "04:00");

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.totalElements()).isZero();
            assertThat(pagina.totalPages()).isZero();
        }
    }

    @Nested
    @DisplayName("Combinado con los otros tres filtros")
    class Combinado {

        @Test
        @DisplayName("con categoria")
        void conCategoria() {
            assertThat(ids(buscar(DERMATOLOGIA, null, null, LOS_LUNES, 0, 100)))
                    .containsExactly(20L);
            assertThat(ids(buscar(CLINICA, null, null, LOS_LUNES, 0, 100)))
                    .containsExactlyInAnyOrder(10L, 11L);
        }

        @Test
        @DisplayName("con nombre")
        void conNombre() {
            assertThat(ids(buscar(null, "lopez", null, LOS_LUNES, 0, 100)))
                    .containsExactly(11L);
            // Elsa Ortiz matchea el nombre pero su horario esta deshabilitado.
            assertThat(buscar(null, "ortiz", null, LOS_LUNES, 0, 100).content()).isEmpty();
        }

        @Test
        @DisplayName("con enabled=false da vacio, porque estar disponible incluye estar habilitado")
        void conEstadoFalse() {
            // No es un error: es pedir algo que por definicion no existe,
            // igual que pedir una categoria y un nombre que no se cruzan.
            assertThat(buscar(null, null, false, LOS_LUNES, 0, 100).content()).isEmpty();
        }

        @Test
        @DisplayName("con enabled=true no cambia nada: ya estaba implicito")
        void conEstadoTrue() {
            assertThat(ids(buscar(null, null, true, LOS_LUNES, 0, 100)))
                    .containsExactlyInAnyOrder(10L, 11L, 20L);
        }

        @Test
        @DisplayName("mantiene el orden estable y el paginado")
        void mantieneOrdenYPaginado() {
            // Gomez, Lopez, Quiroga: el mismo orden del contrato, ahora sobre
            // el subconjunto que tiene agenda.
            assertThat(ids(buscar(null, null, null, LOS_LUNES, 0, 100)))
                    .containsExactly(10L, 11L, 20L);

            ProfessionalPage primera = buscar(null, null, null, LOS_LUNES, 0, 2);
            ProfessionalPage segunda = buscar(null, null, null, LOS_LUNES, 1, 2);

            // El total es el de los disponibles, no el del catalogo: es lo que
            // le permite al cliente saber cuanto le falta.
            assertThat(primera.totalElements()).isEqualTo(3L);
            assertThat(primera.totalPages()).isEqualTo(2);
            assertThat(ids(primera)).containsExactly(10L, 11L);
            assertThat(ids(segunda)).containsExactly(20L);
        }
    }

    // --- Auxiliares ------------------------------------------------------

    private static final AvailabilityCriteria LOS_LUNES =
            new AvailabilityCriteria(DayOfWeek.MONDAY, null, null);

    private ProfessionalPage losQueAtienden(DayOfWeek dia, String desde, String hasta) {
        return buscar(
                null,
                null,
                null,
                AvailabilityCriteria.de(dia == null ? null : dia.name(), desde, hasta).orElseThrow(),
                0,
                100);
    }

    private ProfessionalPage buscar(
            Long categoria,
            String nombre,
            Boolean habilitado,
            AvailabilityCriteria disponibilidad,
            int pagina,
            int tamanio) {

        return profesionales.search(
                new ProfessionalSearchCriteria(
                        categoria, nombre, habilitado, disponibilidad, pagina, tamanio));
    }

    private static List<Long> ids(ProfessionalPage pagina) {
        return pagina.content().stream().map(Professional::getId).toList();
    }

    private void categoria(long id, String nombre) {
        jdbc.update(
                "INSERT INTO professional_category (id, name, enabled, created_at, updated_at)"
                        + " VALUES (?, ?, true, ?, ?)",
                id,
                nombre,
                CUANDO_TZ,
                CUANDO_TZ);
    }

    private static Professional profesional(
            Long id, String nombre, String apellido, Long categoria, boolean habilitado) {
        return Professional.builder()
                .id(id)
                .categoryId(categoria)
                .firstName(nombre)
                .lastName(apellido)
                .enabled(habilitado)
                .createdAt(CUANDO)
                .updatedAt(CUANDO)
                .build();
    }

    private static WeeklySchedule horario(
            Long id,
            Long profesionalId,
            DayOfWeek dia,
            String desde,
            String hasta,
            boolean habilitado) {

        return WeeklySchedule.builder()
                .id(id)
                .professionalId(profesionalId)
                .dayOfWeek(dia)
                .startTime(LocalTime.parse(desde))
                .endTime(LocalTime.parse(hasta))
                .slotDurationMinutes(30)
                .enabled(habilitado)
                .createdAt(CUANDO)
                .updatedAt(CUANDO)
                .build();
    }
}

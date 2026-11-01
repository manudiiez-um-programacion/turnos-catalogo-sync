package com.um.catalogsync.schedule.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.shared.infrastructure.security.TokensDePrueba;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El contrato de agenda vigente, de punta a punta.
 *
 * <p>Contra PostgreSQL real y por HTTP real, porque es lo que el servicio de
 * turnos va a ver: la serializacion del JSON, el formato de las horas, los
 * codigos de error y la cadena de seguridad son justamente lo que un doble se
 * saltearia.
 *
 * <p>La prueba de {@code esquemaExacto} es la prueba de contrato que pide #18:
 * verifica el conjunto <b>completo</b> de campos en cada nivel, no solo que
 * esten los que interesan. Agregar un campo a un DTO rompe esta prueba a
 * proposito: del otro lado hay un repo que va a parsear esto, y un campo nuevo
 * es un cambio de contrato que tiene que ser una decision y no un descuido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurrentAgendaApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long CATEGORIA = 7L;
    private static final long PROFESIONAL = 42L;
    private static final ObjectMapper JACKSON = new ObjectMapper();

    /** Las columnas de auditoria de la copia local son NOT NULL. */
    private static final Instant CUANDO = Instant.parse("2026-10-27T18:00:00Z");

    private final HttpClient cliente =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private WeeklyScheduleRepository horarios;

    @Autowired
    private CatalogVersionRepository versiones;

    @Nested
    @DisplayName("Con la copia local sincronizada")
    class CopiaSincronizada {

        @Test
        @DisplayName("devuelve el esquema exacto del contrato")
        void esquemaExacto() {
            dadaUnaCopiaConUnProfesionalHabilitado();

            HttpResponse<String> respuesta = pedirAgenda(PROFESIONAL, TokensDePrueba.deServicioDeTurnos());
            assertThat(respuesta.statusCode()).isEqualTo(200);

            JsonNode cuerpo = JACKSON.readTree(respuesta.body());
            assertThat(cuerpo.propertyNames())
                    .containsExactlyInAnyOrder("professional", "schedules", "catalogVersion");

            assertThat(cuerpo.path("professional").propertyNames())
                    .containsExactlyInAnyOrder("id", "categoryId", "firstName", "lastName", "enabled");

            assertThat(cuerpo.path("schedules").path(0).propertyNames())
                    .containsExactlyInAnyOrder(
                            "id", "dayOfWeek", "startTime", "endTime", "slotDurationMinutes");

            assertThat(cuerpo.path("catalogVersion").propertyNames())
                    .containsExactlyInAnyOrder("version", "appliedAt", "source");
        }

        @Test
        @DisplayName("los datos del profesional y la version aplicada son los de la copia")
        void datosDeLaCopia() {
            dadaUnaCopiaConUnProfesionalHabilitado();

            JsonNode cuerpo = agendaDe(PROFESIONAL);

            assertThat(cuerpo.path("professional").path("id").asLong()).isEqualTo(PROFESIONAL);
            assertThat(cuerpo.path("professional").path("categoryId").asLong()).isEqualTo(CATEGORIA);
            assertThat(cuerpo.path("professional").path("firstName").asString()).isEqualTo("Ana");
            assertThat(cuerpo.path("professional").path("lastName").asString()).isEqualTo("Gomez");
            assertThat(cuerpo.path("professional").path("enabled").asBoolean()).isTrue();

            assertThat(cuerpo.path("catalogVersion").path("version").asLong()).isEqualTo(128L);
            assertThat(cuerpo.path("catalogVersion").path("source").asString()).isEqualTo("SNAPSHOT");
            assertThat(cuerpo.path("catalogVersion").path("appliedAt").asString()).isNotBlank();
        }

        @Test
        @DisplayName("las horas van en HH:mm, el formato del contrato de la catedra")
        void horasEnFormatoDelContrato() {
            dadaUnaCopiaConUnProfesionalHabilitado();

            JsonNode primero = agendaDe(PROFESIONAL).path("schedules").path(0);

            assertThat(primero.path("startTime").asString()).isEqualTo("09:00");
            assertThat(primero.path("endTime").asString()).isEqualTo("13:00");
            assertThat(primero.path("slotDurationMinutes").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("publica solo los horarios habilitados, ordenados por dia y hora")
        void soloLosHabilitadosYEnOrden() {
            dadaUnaCopiaConUnProfesionalHabilitado();

            JsonNode horariosJson = agendaDe(PROFESIONAL).path("schedules");

            assertThat(horariosJson.size()).isEqualTo(2);
            assertThat(horariosJson.path(0).path("dayOfWeek").asString()).isEqualTo("MONDAY");
            assertThat(horariosJson.path(1).path("dayOfWeek").asString()).isEqualTo("WEDNESDAY");
        }

        @Test
        @DisplayName("un profesional habilitado sin horarios devuelve 200 con la lista vacia")
        void sinHorarios() {
            dadaLaVersionAplicada();
            dadaLaCategoria();
            profesionales.upsert(profesional(99L, true));

            HttpResponse<String> respuesta = pedirAgenda(99L, TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(JACKSON.readTree(respuesta.body()).path("schedules")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Los errores del contrato")
    class Errores {

        @Test
        @DisplayName("profesional inexistente: 404 PROFESSIONAL_NOT_FOUND")
        void inexistente() {
            dadaLaVersionAplicada();

            HttpResponse<String> respuesta = pedirAgenda(777L, TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(404);
            assertThat(problema(respuesta, "code")).isEqualTo("PROFESSIONAL_NOT_FOUND");
            assertThat(respuesta.headers().firstValue("content-type"))
                    .hasValueSatisfying(tipo -> assertThat(tipo).startsWith("application/problem+json"));
        }

        @Test
        @DisplayName("profesional deshabilitado: 409 PROFESSIONAL_DISABLED, distinguible del 404")
        void deshabilitado() {
            dadaLaVersionAplicada();
            dadaLaCategoria();
            profesionales.upsert(profesional(PROFESIONAL, false));

            HttpResponse<String> respuesta = pedirAgenda(PROFESIONAL, TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(409);
            assertThat(problema(respuesta, "code")).isEqualTo("PROFESSIONAL_DISABLED");
        }

        @Test
        @DisplayName("sin version aplicada: 503 CATALOG_NOT_SYNCHRONIZED, no un 404 enganoso")
        void sinVersionAplicada() {
            // Base vacia: ni version ni profesionales. Un 404 diria que el
            // profesional no existe cuando lo que no existe es la copia local.
            HttpResponse<String> respuesta = pedirAgenda(PROFESIONAL, TokensDePrueba.deServicioDeTurnos());

            assertThat(respuesta.statusCode()).isEqualTo(503);
            assertThat(problema(respuesta, "code")).isEqualTo("CATALOG_NOT_SYNCHRONIZED");
        }

        @Test
        @DisplayName("el problema trae la forma de §13: path, message y code")
        void formaDelProblema() {
            dadaLaVersionAplicada();

            HttpResponse<String> respuesta = pedirAgenda(777L, TokensDePrueba.deServicioDeTurnos());

            assertThat(problema(respuesta, "path")).isEqualTo(ruta(777L));
            assertThat(problema(respuesta, "message")).isEqualTo("error.http.404");
            assertThat(problema(respuesta, "title")).isEqualTo("Not Found");
        }
    }

    @Nested
    @DisplayName("Solo el servicio de turnos entra")
    class Autorizacion {

        @Test
        @DisplayName("sin token: 401")
        void sinToken() {
            assertThat(pedirAgenda(PROFESIONAL, null).statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("con el token del usuario final: 401, no alcanza para esta API")
        void conTokenDeUsuarioFinal() {
            // Es el criterio de #18: "un token de usuario final no alcanza".
            // Da 401 y no 403 porque esta cadena solo conoce el secreto
            // interno, asi que falla en la verificacion de firma (ADR 0002).
            assertThat(pedirAgenda(PROFESIONAL, TokensDePrueba.deUsuario()).statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("con un token tecnico sin la autoridad SERVICE_TURNOS: 403")
        void sinLaAutoridad() {
            HttpResponse<String> respuesta =
                    pedirAgenda(PROFESIONAL, TokensDePrueba.deServicioSinAutoridad());

            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(problema(respuesta, "code")).isEqualTo("AUTH_FORBIDDEN");
        }
    }

    private void dadaUnaCopiaConUnProfesionalHabilitado() {
        dadaLaVersionAplicada();
        dadaLaCategoria();
        profesionales.upsert(profesional(PROFESIONAL, true));

        // A proposito desordenados y con uno deshabilitado en el medio.
        horarios.upsert(horario(3L, DayOfWeek.WEDNESDAY, "14:00", "18:00", true));
        horarios.upsert(horario(2L, DayOfWeek.TUESDAY, "09:00", "13:00", false));
        horarios.upsert(horario(1L, DayOfWeek.MONDAY, "09:00", "13:00", true));
    }

    private void dadaLaVersionAplicada() {
        versiones.advanceTo(128L, CatalogVersionSource.SNAPSHOT);
    }

    private void dadaLaCategoria() {
        categorias.upsert(
                ProfessionalCategory.builder()
                        .id(CATEGORIA)
                        .name("Clinica")
                        .description("Medicina clinica")
                        .enabled(true)
                        .createdAt(CUANDO)
                        .updatedAt(CUANDO)
                        .build());
    }

    private static Professional profesional(long id, boolean habilitado) {
        return Professional.builder()
                .id(id)
                .categoryId(CATEGORIA)
                .firstName("Ana")
                .lastName("Gomez")
                .enabled(habilitado)
                .createdAt(CUANDO)
                .updatedAt(CUANDO)
                .build();
    }

    private static WeeklySchedule horario(
            long id, DayOfWeek dia, String desde, String hasta, boolean habilitado) {

        return WeeklySchedule.builder()
                .id(id)
                .professionalId(PROFESIONAL)
                .dayOfWeek(dia)
                .startTime(LocalTime.parse(desde))
                .endTime(LocalTime.parse(hasta))
                .slotDurationMinutes(30)
                .enabled(habilitado)
                .createdAt(CUANDO)
                .updatedAt(CUANDO)
                .build();
    }

    private JsonNode agendaDe(long professionalId) {
        return JACKSON.readTree(pedirAgenda(professionalId, TokensDePrueba.deServicioDeTurnos()).body());
    }

    private String problema(HttpResponse<String> respuesta, String campo) {
        return JACKSON.readTree(respuesta.body()).path(campo).asString();
    }

    private static String ruta(long professionalId) {
        return "/api/internal/professionals/" + professionalId + "/agenda";
    }

    private HttpResponse<String> pedirAgenda(long professionalId, String token) {
        try {
            HttpRequest.Builder pedido =
                    HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta(professionalId)))
                            .GET();
            if (token != null) {
                pedido.header("Authorization", "Bearer " + token);
            }
            return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("No se pudo llamar a la API", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Llamada interrumpida", error);
        }
    }

}

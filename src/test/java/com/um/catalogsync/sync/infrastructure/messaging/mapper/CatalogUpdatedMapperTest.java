package com.um.catalogsync.sync.infrastructure.messaging.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.application.exception.MalformedCatalogEventException;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.infrastructure.messaging.CatalogUpdatedFixtures;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/**
 * La traduccion del mensaje del topic, sin broker.
 *
 * <p>Lo que se prueba acá es que forma se acepta y cual se rechaza. No hace
 * falta Kafka para eso, y tenerlo convertiria una prueba de milisegundos en
 * una de segundos.
 *
 * <p>La ubicacion que se le pasa al mapper es la que el listener arma con el
 * topic, la particion y el offset: las pruebas verifican que aparezca en los
 * mensajes de error, porque es lo unico con lo que se encuentra el mensaje
 * del otro lado.
 */
class CatalogUpdatedMapperTest {

    private static final String UBICACION = CatalogUpdatedFixtures.TOPIC + "-0@42";

    private final CatalogUpdatedMapper mapper = new CatalogUpdatedMapper(new ObjectMapper());

    @Test
    @DisplayName("El ejemplo de §15.3 se traduce campo por campo")
    void elEjemploDeLaReferencia() {
        CatalogUpdatedEvent evento =
                mapper.toEvent(UBICACION, CatalogUpdatedFixtures.mensaje());

        assertThat(evento.getEventId()).isEqualTo(CatalogUpdatedFixtures.EVENT_ID);
        assertThat(evento.getEventType()).isEqualTo("CatalogUpdated");
        assertThat(evento.getNewVersion()).isEqualTo(CatalogUpdatedFixtures.VERSION);
        assertThat(evento.getOccurredAt())
                .isEqualTo(Instant.parse(CatalogUpdatedFixtures.OCURRIO));
        assertThat(evento.getSource()).isEqualTo(CatalogUpdatedFixtures.ORIGEN);
    }

    @Test
    @DisplayName("Los campos que el contrato todavia no tiene se ignoran")
    void ignoraLosCamposDesconocidos() {
        // §15.1 permite agregar campos opcionales dentro de v1 y obliga a
        // ignorar los desconocidos. Es el requisito mas facil de incumplir
        // sin darse cuenta, y el mas caro: un campo nuevo tumbaria el
        // consumo del topic entero.
        String conCampoNuevo = CatalogUpdatedFixtures.mensaje()
                .replace("\"schemaVersion\": 1", "\"priority\": \"HIGH\", \"schemaVersion\": 1");

        assertThatCode(() -> mapper.toEvent(UBICACION, conCampoNuevo)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un source que no es MANUAL ni SCHEDULED se acepta tal cual")
    void elSourceNoSeValida() {
        // §15.3 da dos ejemplos, no una lista cerrada. Un enum convertiria
        // un valor nuevo de la catedra en un error de deserializacion, y la
        // notificacion se perderia por un campo que es solo de diagnostico.
        String conOtroOrigen =
                CatalogUpdatedFixtures.mensaje().replace("MANUAL", "RECOVERY_PIPELINE");

        assertThat(mapper.toEvent(UBICACION, conOtroOrigen).getSource())
                .isEqualTo("RECOVERY_PIPELINE");
    }

    @Test
    @DisplayName("Un source ausente no es un problema")
    void sinSource() {
        String sinOrigen =
                CatalogUpdatedFixtures.mensaje().replace("\"source\": \"MANUAL\",", "");

        assertThat(mapper.toEvent(UBICACION, sinOrigen).getSource()).isNull();
    }

    // --- Lo que se rechaza ------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"{esto no es json", "", "   ", "[]", "null", "\"texto\""})
    @DisplayName("Un contenido que no es el objeto de §15.3 nombra la ubicacion")
    void contenidoInvalido(String json) {
        assertThatThrownBy(() -> mapper.toEvent(UBICACION, json))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining(UBICACION);
    }

    @Test
    @DisplayName("Un mensaje sin contenido se rechaza en lugar de reventar")
    void mensajeNulo() {
        // Un value nulo es un tombstone: tiene sentido en un topic
        // compactado y ninguno en uno de notificaciones. Sin este chequeo
        // seria un NullPointerException, que no se trataria como mensaje
        // envenenado y trabaria la particion.
        assertThatThrownBy(() -> mapper.toEvent(UBICACION, null))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("no tiene contenido");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 99})
    @DisplayName("Un schemaVersion que no es 1 se rechaza diciendo cual llego")
    void esquemaDesconocido(int esquema) {
        // §15.1 promete que dentro de v1 no cambian los campos ni su
        // significado. Fuera de v1 no hay ninguna promesa, asi que
        // interpretar el mensaje "lo mejor posible" seria avanzar la version
        // local con datos de un contrato que no conocemos.
        String otroEsquema = CatalogUpdatedFixtures.mensaje()
                .replace("\"schemaVersion\": 1", "\"schemaVersion\": " + esquema);

        assertThatThrownBy(() -> mapper.toEvent(UBICACION, otroEsquema))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("schemaVersion " + esquema);
    }

    @Test
    @DisplayName("Sin schemaVersion, el error dice que falta el campo")
    void sinEsquema() {
        // El DTO usa Long y no long justamente para esto: con el primitivo,
        // el campo ausente llegaria como 0 y el error diria "schemaVersion
        // 0", que manda a buscar un valor que el mensaje no trae.
        String sinEsquema = CatalogUpdatedFixtures.mensaje()
                .replace(",\n  \"schemaVersion\": 1", "");

        assertThatThrownBy(() -> mapper.toEvent(UBICACION, sinEsquema))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("falta el campo \"schemaVersion\"");
    }

    @Test
    @DisplayName("Un eventType de otro evento se rechaza")
    void otroTipoDeEvento() {
        // El topic de catalogo solo publica CatalogUpdated (§15.2). Si
        // apareciera otro, tratarlo como una notificacion de catalogo seria
        // peor que saltearlo con un mensaje claro.
        String otroTipo = CatalogUpdatedFixtures.mensaje()
                .replace("CatalogUpdated", "AppointmentConfirmed");

        assertThatThrownBy(() -> mapper.toEvent(UBICACION, otroTipo))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("AppointmentConfirmed");
    }

    @Test
    @DisplayName("Sin eventId no hay deduplicacion posible, asi que se rechaza")
    void sinEventId() {
        String sinId = CatalogUpdatedFixtures.mensaje()
                .replace("\"eventId\": \"catalog-event-7-4f3b\",", "");

        assertThatThrownBy(() -> mapper.toEvent(UBICACION, sinId))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("Un eventId en blanco vale lo mismo que ausente")
    void eventIdEnBlanco() {
        String enBlanco =
                CatalogUpdatedFixtures.mensaje().replace("catalog-event-7-4f3b", "   ");

        assertThatThrownBy(() -> mapper.toEvent(UBICACION, enBlanco))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("Sin newVersion, la notificacion no dice nada")
    void sinVersion() {
        String sinVersion =
                CatalogUpdatedFixtures.mensaje().replace("\"newVersion\": 7,", "");

        assertThatThrownBy(() -> mapper.toEvent(UBICACION, sinVersion))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("newVersion");
    }

    @Test
    @DisplayName("Sin occurredAt, se rechaza")
    void sinOccurredAt() {
        String sinInstante = CatalogUpdatedFixtures.mensaje()
                .replace("\"occurredAt\": \"2026-09-10T13:44:59Z\",", "");

        assertThatThrownBy(() -> mapper.toEvent(UBICACION, sinInstante))
                .isInstanceOf(MalformedCatalogEventException.class)
                .hasMessageContaining("occurredAt");
    }
}

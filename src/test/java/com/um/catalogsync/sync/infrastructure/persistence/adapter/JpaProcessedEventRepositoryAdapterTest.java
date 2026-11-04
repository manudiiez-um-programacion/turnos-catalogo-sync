package com.um.catalogsync.sync.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.domain.model.CatalogUpdatedEvent;
import com.um.catalogsync.sync.domain.ports.out.ProcessedEventRepository;
import com.um.catalogsync.sync.infrastructure.messaging.CatalogUpdatedFixtures;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La memoria de idempotencia contra un PostgreSQL real.
 *
 * <p>Lo que no se puede probar con dobles: que la clave primaria sea la que
 * detecta el duplicado —y no un {@code existsById} nuestro—, y que el
 * registro del evento se vaya con el rollback de la transaccion de quien
 * llama. La segunda es la que importa: si el evento quedara registrado
 * despues de un fallo, el reintento lo descartaria como duplicado y sus
 * efectos no ocurririan nunca.
 */
class JpaProcessedEventRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ProcessedEventRepository eventos;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    private List<Map<String, Object>> filas() {
        return jdbc.queryForList("SELECT * FROM processed_event ORDER BY processed_at, event_id");
    }

    @Test
    @DisplayName("Un evento nuevo se registra con todos sus campos")
    void registraElEventoNuevo() {
        assertThat(eventos.recordIfNew(CatalogUpdatedFixtures.evento())).isTrue();

        assertThat(filas()).singleElement().satisfies(fila -> {
            assertThat(fila.get("event_id")).isEqualTo(CatalogUpdatedFixtures.EVENT_ID);
            assertThat(fila.get("event_type")).isEqualTo("CatalogUpdated");
            assertThat(fila.get("new_version")).isEqualTo(CatalogUpdatedFixtures.VERSION);
            assertThat(fila.get("source")).isEqualTo(CatalogUpdatedFixtures.ORIGEN);
            assertThat(fila.get("occurred_at")).isNotNull();
            assertThat(fila.get("processed_at")).isNotNull();
        });
    }

    @Test
    @DisplayName("El mismo eventId no se registra dos veces")
    void elSegundoEsDuplicado() {
        CatalogUpdatedEvent evento = CatalogUpdatedFixtures.evento();

        assertThat(eventos.recordIfNew(evento)).isTrue();
        assertThat(eventos.recordIfNew(evento)).isFalse();

        // La clave primaria es la que decide. Lo que se prueba acá es que el
        // choque se traduzca a "ya estaba" y no se escape como un error.
        assertThat(filas()).hasSize(1);
    }

    @Test
    @DisplayName("Dos eventos distintos de la misma version conviven")
    void dosEventosDeLaMismaVersion() {
        // §15.1: la idempotencia es por eventId. La tabla tiene un indice
        // por new_version para consultar, no una restriccion de unicidad.
        assertThat(eventos.recordIfNew(CatalogUpdatedFixtures.evento("manual-7", 7L))).isTrue();
        assertThat(eventos.recordIfNew(CatalogUpdatedFixtures.evento("programado-7", 7L)))
                .isTrue();

        assertThat(filas()).hasSize(2);
    }

    @Test
    @DisplayName("Un evento sin source se guarda igual")
    void sinSource() {
        // La columna es nullable a proposito: §15.3 describe source con
        // ejemplos y no lo exige, asi que un evento sin el no puede quedar
        // sin registrar —seria un agujero en la deduplicacion—.
        CatalogUpdatedEvent sinOrigen = CatalogUpdatedFixtures.evento();
        sinOrigen.setSource(null);

        assertThat(eventos.recordIfNew(sinOrigen)).isTrue();
        assertThat(filas()).singleElement()
                .satisfies(fila -> assertThat(fila.get("source")).isNull());
    }

    @Test
    @DisplayName("Si la transaccion de quien llama falla, el evento no queda registrado")
    void seVaConElRollback() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        try {
            tx.executeWithoutResult(estado -> {
                eventos.recordIfNew(CatalogUpdatedFixtures.evento());
                throw new IllegalStateException("aplicar la version fallo despues de registrar");
            });
        } catch (IllegalStateException esperada) {
            // Es el escenario, no un problema.
        }

        // Es la razon por la que el adaptador no usa REQUIRES_NEW. Si la
        // fila sobreviviera, el reintento del mensaje veria un duplicado y
        // la version nunca se aplicaria.
        assertThat(filas()).isEmpty();
    }

    @Test
    @DisplayName("La purga borra lo viejo y deja lo reciente")
    void purgaPorRetencion() {
        eventos.recordIfNew(CatalogUpdatedFixtures.evento("viejo", 5L));
        envejecer("viejo", Instant.now().minus(40, ChronoUnit.DAYS));
        eventos.recordIfNew(CatalogUpdatedFixtures.evento("reciente", 7L));

        int borrados = eventos.purgeProcessedBefore(Instant.now().minus(30, ChronoUnit.DAYS));

        assertThat(borrados).isEqualTo(1);
        assertThat(filas()).singleElement()
                .satisfies(fila -> assertThat(fila.get("event_id")).isEqualTo("reciente"));
    }

    @Test
    @DisplayName("La purga no toca la fila que esta justo en el borde")
    void elBordeDeLaVentanaNoSeBorra() {
        Instant corte = Instant.now().truncatedTo(ChronoUnit.MICROS);
        eventos.recordIfNew(CatalogUpdatedFixtures.evento("en-el-borde", 7L));
        envejecer("en-el-borde", corte);

        // El limite es estricto: se borra lo procesado ANTES del corte. Un
        // "menor o igual" borraria el evento que acaba de entrar en el
        // instante exacto del corte, y la diferencia importa porque la
        // consecuencia de borrar de mas es procesar un duplicado como nuevo.
        assertThat(eventos.purgeProcessedBefore(corte)).isZero();
        assertThat(filas()).hasSize(1);
    }

    /** Mueve el processed_at de una fila, para no esperar treinta dias. */
    private void envejecer(String eventId, Instant cuando) {
        jdbc.update("UPDATE processed_event SET processed_at = ? WHERE event_id = ?",
                java.sql.Timestamp.from(cuando), eventId);
    }
}

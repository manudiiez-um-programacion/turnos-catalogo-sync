package com.um.catalogsync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.infrastructure.reconciliation.CatalogReconciliationJob;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisForTests;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * La notificacion perdida, recuperada por el chequeo periodico.
 *
 * <p>Es el criterio central de #27 y el unico lugar donde se puede
 * demostrar: Kafka no entrega nada —el consumidor esta apagado en el perfil
 * de prueba—, la catedra publica versiones nuevas en Redis, y la copia local
 * queda atrasada. El chequeo compara y se pone al dia sin que haya llegado
 * ningun evento.
 *
 * <p>Redis y PostgreSQL son reales y los adaptadores son los de produccion.
 * Lo unico que se reemplaza es la configuracion de integracion, que apunta
 * al contenedor en lugar del Redis de la catedra: es exactamente de donde la
 * toma el servicio (§14.1).
 *
 * <p>El trabajo se invoca a mano y no se espera al planificador: el perfil
 * {@code test} apaga el chequeo a proposito, porque una prueba que espera un
 * intervalo es una prueba lenta y una que espera un intervalo corto es una
 * prueba intermitente.
 */
@TestPropertySource(properties = {
    // El perfil de prueba apaga el chequeo; esta clase lo necesita como
    // bean. Los tiempos quedan en una hora para que el planificador no lo
    // dispare: la prueba lo invoca a mano, que es la unica forma de
    // controlar cuando corre sin esperar ni volverse intermitente.
    "sync.reconciliation.enabled=true",
    "sync.reconciliation.interval=1h",
    "sync.reconciliation.initial-delay=1h",
})
class ReconciliationIntegrationTest extends AbstractPostgresIntegrationTest {

    @MockitoBean
    private GetIntegrationConfigUseCase integracion;

    @Autowired
    private CatalogReconciliationJob chequeo;

    @Autowired
    private ApplyCatalogSnapshotUseCase applySnapshot;

    @Autowired
    private CatalogVersionRepository versiones;

    @Autowired
    private ProfessionalRepository profesionales;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepararElOrigenYLaCopiaLocal() {
        CatedraRedisForTests.limpiar();
        when(integracion.get()).thenReturn(CatedraRedisForTests.apuntandoA(
                CatedraRedisForTests.puerto(), CatedraRedisForTests.CLAVE));

        // La copia local arranca en la version 7, como si la ultima
        // notificacion que si llego hubiera sido la de esa version.
        applySnapshot.apply(CatalogSnapshotFixtures.version(7L));
    }

    @Test
    @DisplayName("Una notificacion perdida deja la copia atrasada y el chequeo la recupera")
    void recuperaLaNotificacionPerdida() {
        // La catedra publico la version 8 y el aviso nunca llego: ningun
        // CatalogUpdated se consumio, asi que nada disparo la
        // sincronizacion.
        metadata(8L, 4L);
        hashProfesional(15L, 1L, "Ana", "Gonzalez");
        claveDeCambios(8L, List.of(15L));

        assertThat(versiones.findCurrent()).get()
                .satisfies(version -> assertThat(version.getVersion()).isEqualTo(7L));

        chequeo.reconciliar();

        // §16: la comparacion de versiones detecto el atraso y lo recupero.
        assertThat(versiones.findCurrent()).get()
                .satisfies(version -> assertThat(version.getVersion()).isEqualTo(8L));
        assertThat(profesionales.findById(15L)).get()
                .satisfies(p -> assertThat(p.getLastName()).isEqualTo("Gonzalez"));
    }

    @Test
    @DisplayName("La corrida queda registrada como RECONCILIATION")
    void dejaLaCorridaEnElHistorial() {
        metadata(8L, 4L);
        claveDeCambios(8L, List.of());

        chequeo.reconciliar();

        // Que el trigger sea RECONCILIATION es lo que permite demostrar en
        // la defensa que el atraso lo recupero el chequeo y no un evento.
        assertThat(corridas()).last().satisfies(corrida -> {
            assertThat(corrida.get("triggered_by")).isEqualTo("RECONCILIATION");
            assertThat(corrida.get("mode")).isEqualTo(SyncMode.INCREMENTAL.name());
            assertThat(corrida.get("decision_reason"))
                    .isEqualTo(SyncDecisionReason.BEHIND_CURRENT_VERSION.name());
            assertThat(corrida.get("from_version")).isEqualTo(7L);
            assertThat(corrida.get("to_version")).isEqualTo(8L);
            assertThat(corrida.get("status")).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    @DisplayName("Si no hay atraso, el chequeo no registra ninguna corrida")
    void sinAtrasoNoRegistraNada() {
        // El criterio explicito del issue: no genera carga ni logs cuando no
        // hay nada que hacer. Un historial con una fila por chequeo cada
        // cinco minutos enterraria las corridas que si hicieron algo.
        metadata(7L, 4L);
        int corridasDelSnapshotInicial = corridas().size();

        chequeo.reconciliar();

        assertThat(corridas()).hasSize(corridasDelSnapshotInicial);
    }

    @Test
    @DisplayName("Si la version local quedo fuera de la ventana, reconstruye (§18.2)")
    void fueraDeLaVentanaReconstruye() {
        // local 7, oldest-available 9: los incrementos que harian falta ya
        // no estan. No se intenta leer changes:8 —ni siquiera existe— y la
        // decision pide el snapshot. Acá el snapshot falla porque no hay
        // catedra, y lo que importa es que la corrida registre el motivo.
        metadata(12L, 9L);

        chequeo.reconciliar();

        assertThat(corridas()).last().satisfies(corrida -> {
            assertThat(corrida.get("mode")).isEqualTo(SyncMode.FULL.name());
            assertThat(corrida.get("decision_reason"))
                    .isEqualTo(SyncDecisionReason.VERSION_OUT_OF_WINDOW.name());
        });
    }

    @Test
    @DisplayName("Si Redis no responde, el chequeo no revienta")
    void siRedisNoRespondeNoRevienta() {
        // El puerto 1 no tiene nada escuchando: el estado no se puede
        // verificar, asi que la decision pide el snapshot, que tampoco
        // llega. Lo que importa es que el planificador no reciba la
        // excepcion.
        when(integracion.get()).thenReturn(
                CatedraRedisForTests.apuntandoA(1, CatedraRedisForTests.CLAVE));

        chequeo.reconciliar();

        assertThat(corridas()).last().satisfies(corrida ->
                assertThat(corrida.get("decision_reason"))
                        .isEqualTo(SyncDecisionReason.STATE_UNVERIFIABLE.name()));
    }

    // --- Lo que la catedra publica en Redis -------------------------------

    private void metadata(long actual, long masAntigua) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.METADATA,
                "currentVersion", String.valueOf(actual),
                "oldestAvailableVersion", String.valueOf(masAntigua),
                "publishedAt", "2026-09-10T13:44:59Z");
    }

    private void claveDeCambios(long version, List<Long> profesionalesQueCambiaron) {
        CatedraRedisForTests.cli("SET", PublishedCatalogKeys.changes(version), """
                {"version": %d, "changes": {"professionalCategories": [],
                 "professionals": %s, "weeklySchedules": []}}
                """.formatted(version, profesionalesQueCambiaron));
    }

    private void hashProfesional(long id, long categoriaId, String nombre, String apellido) {
        CatedraRedisForTests.cli("HSET", PublishedCatalogKeys.PROFESSIONALS,
                String.valueOf(id), """
                        {"id": %d, "categoryId": %d, "firstName": "%s", "lastName": "%s",
                         "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                         "updatedAt": "2026-09-10T13:40:00Z"}
                        """.formatted(id, categoriaId, nombre, apellido));
    }

    private List<Map<String, Object>> corridas() {
        return jdbc.queryForList("SELECT * FROM sync_run ORDER BY id");
    }
}

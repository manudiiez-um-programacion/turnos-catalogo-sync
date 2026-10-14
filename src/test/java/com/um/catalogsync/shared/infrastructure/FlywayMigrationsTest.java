package com.um.catalogsync.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que las migraciones dejan el esquema esperado sobre una base
 * vacia.
 *
 * <p>Corre contra un PostgreSQL real y descartable, no contra H2: el
 * enunciado prohibe las bases embebidas como almacenamiento principal (§3),
 * y ademas la mitad de lo que hay que probar aca —la extension pg_trgm, el
 * indice GIN, el comportamiento de las FK— no existe o se comporta distinto
 * en otro motor. Probar contra H2 daria verde sin probar nada.
 */
class FlywayMigrationsTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Flyway aplica todas las migraciones y llega a la ultima version")
    void aplicaTodasLasMigracionesYLlegaALaUltima() {
        List<String> aplicadas = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true "
                        + "AND version IS NOT NULL ORDER BY installed_rank",
                String.class);

        assertThat(aplicadas).containsExactly("1", "2");
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
    }

    @Test
    @DisplayName("Volver a migrar no aplica nada ni falla: reiniciar el servicio es seguro")
    void volverAMigrarEsIdempotente() {
        MigrateResult resultado = flyway.migrate();

        assertThat(resultado.migrationsExecuted).isZero();
        assertThat(resultado.success).isTrue();
    }

    @Test
    @DisplayName("Crea las tablas del catalogo local y las de sincronizacion")
    void creaTodasLasTablas() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history' ORDER BY table_name",
                String.class);

        assertThat(tablas).containsExactly(
                "catalog_version",
                "processed_event",
                "professional",
                "professional_category",
                "sync_error",
                "sync_run",
                "weekly_schedule");
    }

    @Test
    @DisplayName("Instala pg_trgm y el indice GIN que necesita la busqueda parcial")
    void instalaElIndiceDeBusquedaPorNombre() {
        Integer extension = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        assertThat(extension).isOne();

        String definicion = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes "
                        + "WHERE tablename = 'professional' AND indexname = 'ix_professional_search_name'",
                String.class);
        assertThat(definicion).contains("gin").contains("gin_trgm_ops");
    }

    @Test
    @DisplayName("La version aplicada admite una sola fila")
    void catalogVersionAdmiteUnaSolaFila() {
        jdbc.update("INSERT INTO catalog_version (id, applied_version, applied_at, source) "
                + "VALUES (1, 7, ?, 'SNAPSHOT')", OffsetDateTime.now());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO catalog_version (id, applied_version, applied_at, source) "
                        + "VALUES (2, 8, ?, 'SNAPSHOT')", OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM catalog_version");
    }

    @Test
    @DisplayName("No se puede borrar una categoria que todavia tiene profesionales")
    void lasFkNoBorranEnCascada() {
        OffsetDateTime ahora = OffsetDateTime.now();
        jdbc.update("INSERT INTO professional_category (id, name, enabled, created_at, updated_at) "
                + "VALUES (1, 'Clinica medica', true, ?, ?)", ahora, ahora);
        jdbc.update("INSERT INTO professional "
                + "(id, category_id, first_name, last_name, search_name, enabled, created_at, updated_at) "
                + "VALUES (15, 1, 'Ana', 'Gomez', 'ana gomez', true, ?, ?)", ahora, ahora);

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM professional_category WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
    }
}

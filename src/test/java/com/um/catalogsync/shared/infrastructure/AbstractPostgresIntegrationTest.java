package com.um.catalogsync.shared.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de las pruebas de integracion: un PostgreSQL real, compartido por
 * toda la suite, y vacio al empezar cada prueba.
 *
 * <p>El contenedor se arranca a mano en un bloque estatico y no con
 * {@code @Container}. La diferencia importa: la extension de JUnit maneja
 * los contenedores por clase de prueba, asi que con {@code @Container} en
 * una clase base cada subclase levantaria y apagaria su propio PostgreSQL.
 * Arrancandolo asi, hay uno solo para todas. No se apaga explicitamente: de
 * eso se encarga Ryuk, el contenedor de limpieza de Testcontainers, cuando
 * termina la JVM.
 *
 * <p>Se usa la misma version mayor que el compose. Probar contra una
 * version distinta de la que se despliega esconde justamente los errores
 * que estas pruebas tienen que encontrar.
 *
 * <p>Toda subclase hereda tambien {@code @SpringBootTest}, asi que levanta
 * el contexto completo. Eso incluye la validacion del mapeo JPA contra el
 * esquema que crearon las migraciones ({@code ddl-auto: validate}): si una
 * {@code @Entity} y su migracion no coinciden, estas pruebas no arrancan.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    /** Tablas de datos, en una sola sentencia. Flyway no se toca. */
    private static final String TRUNCAR_TODO = """
            TRUNCATE weekly_schedule,
                     professional,
                     professional_category,
                     sync_error,
                     sync_run,
                     processed_event,
                     catalog_version
            """;

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcParaLimpiar;

    /**
     * Deja la base vacia antes de cada prueba.
     *
     * <p>Vive aca y no en cada clase porque el contenedor es compartido: si
     * una prueba deja filas, la siguiente las encuentra. Y con las FK en
     * RESTRICT el sintoma aparece lejos de la causa — un {@code DELETE}
     * sobre categorias falla porque quedaron profesionales de otra clase de
     * prueba, que no tiene nada que ver con lo que se estaba probando.
     *
     * <p>{@code TRUNCATE} con todas las tablas en una sola sentencia y no
     * {@code DELETE} en orden de dependencia: PostgreSQL acepta truncar un
     * grupo de tablas relacionadas de una vez, sin pedir el orden ni
     * {@code CASCADE}. Asi no hay una lista de borrados que mantener
     * ordenada a mano cada vez que aparece una tabla nueva.
     *
     * <p>Las subclases pueden sumar su propio {@code @BeforeEach} para
     * precargar lo que necesiten: JUnit corre primero el de la clase base.
     */
    @BeforeEach
    void limpiarLaCopiaLocal() {
        jdbcParaLimpiar.execute(TRUNCAR_TODO);
    }
}

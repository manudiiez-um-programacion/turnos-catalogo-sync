package com.um.catalogsync.shared.infrastructure;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de las pruebas de integracion: un PostgreSQL real, compartido por
 * toda la suite.
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
}

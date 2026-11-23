package com.um.catalogsync.shared.infrastructure;

/**
 * Las propiedades que encienden la documentacion en una prueba, en un solo lugar.
 *
 * <p>No es azucar sintactico: es lo que mantiene a estas pruebas en <b>un</b>
 * contexto de Spring. El perfil {@code test} apaga springdoc, asi que toda
 * prueba que necesite la documentacion tiene que encenderla por
 * {@code properties}, y esas propiedades forman parte de la clave con la que
 * Spring cachea contextos: dos clases que enciendan lo mismo escrito distinto
 * levantan dos aplicaciones completas, cada una con su pool de conexiones.
 *
 * <p>Eso ya costo una vez. Al agregar {@code SwaggerUiNavegableTest} con un par
 * de propiedades distinto del que ya usaban las dos pruebas de contrato,
 * apareció un contexto mas y la suite entera empezo a fallar con
 * {@code FATAL: sorry, too many clients already}: el PostgreSQL de
 * Testcontainers es uno y compartido, y la suma de los pools paso su
 * {@code max_connections}. El sintoma no señalaba a la prueba nueva —fallaban
 * las de sincronizacion, por orden de ejecucion—, que es la peor forma de
 * enterarse.
 *
 * <p>Usar estas constantes en lugar de literales hace que agregar una prueba de
 * documentacion no agregue un contexto. Las dos van juntas: con
 * {@code api-docs} sola, los recursos de Swagger UI no se sirven, el 404 se
 * reenvia a {@code /error} —que exige credencial— y la prueba ve un 401 que no
 * tiene nada que ver con lo que estaba midiendo.
 */
public final class DocumentacionEncendida {

    /** El documento OpenAPI: {@code /v3/api-docs} y su variante YAML. */
    public static final String API_DOCS = "springdoc.api-docs.enabled=true";

    /** La pagina de Swagger UI y sus recursos estaticos. */
    public static final String SWAGGER_UI = "springdoc.swagger-ui.enabled=true";

    private DocumentacionEncendida() {}
}

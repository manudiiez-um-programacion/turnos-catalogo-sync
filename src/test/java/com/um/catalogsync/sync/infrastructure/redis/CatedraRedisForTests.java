package com.um.catalogsync.sync.infrastructure.redis;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.shared.domain.model.Secret;
import java.io.IOException;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;

/**
 * Un Redis con la ACL de la catedra, compartido por las pruebas que leen de
 * Redis.
 *
 * <p><b>Un solo contenedor para toda la corrida.</b> Se arranca la primera
 * vez que alguien toca esta clase y no se para: Testcontainers lo elimina al
 * terminar la JVM con su contenedor de limpieza. Con un contenedor por clase
 * de prueba, cada clase pagaria el arranque y —lo que importa mas— cada una
 * tendria que configurar la ACL por su cuenta, que es justamente la parte
 * que no puede divergir: si una clase olvidara el {@code %R~}, sus pruebas
 * pasarian escribiendo en {@code catedra:sync:*}, que en produccion no se
 * puede.
 *
 * <p><b>Redis de verdad y no un doble en memoria</b> porque lo que se prueba
 * son tres cosas que un doble se saltearia: que {@code GET}, {@code HGET} y
 * {@code HGETALL} distingan ausencia de vacio como lo hace Redis, que la
 * autenticacion con usuario y clave funcione, y que los permisos sean los de
 * §14.1.
 *
 * <p>Cada prueba empieza llamando a {@link #limpiar()}: el contenedor es
 * compartido, asi que el aislamiento entre pruebas no lo da el contenedor
 * sino el {@code FLUSHALL}. No borra los usuarios de la ACL, que son
 * configuracion del servidor y no datos.
 */
public final class CatedraRedisForTests {

    /** El usuario de la integracion tecnica, como lo nombra la catedra. */
    public static final String USUARIO = "grp_" + IntegrationFixtures.GROUP_ID;

    /** Su clave. Falsa, igual que la de los fixtures de integracion. */
    public static final String CLAVE = IntegrationFixtures.REDIS_PASSWORD_FALSA;

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
        aclDeLaCatedra();
    }

    private CatedraRedisForTests() {
    }

    public static String host() {
        return REDIS.getHost();
    }

    public static int puerto() {
        return REDIS.getMappedPort(6379);
    }

    /** Borra los datos de la corrida anterior, no la ACL. */
    public static void limpiar() {
        cli("FLUSHALL");
    }

    /**
     * Una conexion como la que arma Spring, con los timeouts de verdad: son
     * parte de lo que se prueba.
     */
    public static CatedraRedisConnection conexionCon(GetIntegrationConfigUseCase integracion) {
        return new CatedraRedisConnection(
                integracion,
                new CatedraRedisProperties(Duration.ofSeconds(1), Duration.ofSeconds(2)));
    }

    /**
     * Una configuracion de integracion que apunta a este Redis.
     *
     * @param puerto el puerto; uno cerrado sirve para probar la
     *     indisponibilidad
     * @param clave la clave; una equivocada sirve para probar el rechazo
     */
    public static IntegrationConfig apuntandoA(int puerto, String clave) {
        return IntegrationFixtures.conRedisEn(host(), puerto, USUARIO, Secret.of(clave));
    }

    /** Corre {@code redis-cli} dentro del contenedor. */
    public static void cli(String... comando) {
        String[] argv = new String[comando.length + 1];
        argv[0] = "redis-cli";
        System.arraycopy(comando, 0, argv, 1, comando.length);
        try {
            var resultado = REDIS.execInContainer(argv);
            if (resultado.getExitCode() != 0 || resultado.getStdout().startsWith("ERR")) {
                throw new IllegalStateException(
                        "redis-cli fallo: " + resultado.getStdout() + resultado.getStderr());
            }
        } catch (IOException | InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No se pudo correr redis-cli en el contenedor", error);
        }
    }

    /**
     * Configura el usuario con los permisos que describe §14.1.
     *
     * <p>{@code %R~} concede un patron de claves <b>solo para lectura</b>, y
     * {@code %RW~} lectura y escritura. Es lo que hace que las pruebas de
     * permisos prueben algo: sin esto, el usuario tendria acceso a todo y
     * escribir en {@code catedra:sync:*} funcionaria.
     */
    private static void aclDeLaCatedra() {
        cli("ACL", "SETUSER", USUARIO, "on", ">" + CLAVE,
                "%R~" + PublishedCatalogKeys.NAMESPACE + "*",
                "%RW~alumnos:" + IntegrationFixtures.GROUP_ID + ":*",
                "+@read", "+@write", "+@connection");
    }
}

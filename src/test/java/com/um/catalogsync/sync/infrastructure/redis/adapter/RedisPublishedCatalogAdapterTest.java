package com.um.catalogsync.sync.infrastructure.redis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.shared.domain.model.Secret;
import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisConnection;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisProperties;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogFixtures;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import com.um.catalogsync.sync.infrastructure.redis.mapper.PublishedCatalogMapper;
import io.lettuce.core.RedisCommandExecutionException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * El adaptador de lectura contra un Redis real con la ACL de la catedra.
 *
 * <p>Redis de verdad por Testcontainers y no un doble en memoria: lo que se
 * prueba son las tres cosas que un doble se saltearia —que
 * {@code HGET}/{@code HGETALL} distingan ausencia de vacio como lo hace
 * Redis, que la autenticacion con usuario y clave funcione, y que los
 * permisos sean los que §14.1 describe—. La ultima es la que importa mas: un
 * doble aceptaria cualquier comando, y el criterio del issue es justamente
 * que no se escriba en {@code catedra:sync:*} ni se toque
 * {@code catedra:holds:*}.
 *
 * <p><b>La ACL se configura igual que la de la catedra:</b> lectura sobre
 * {@code catedra:sync:*}, lectura y escritura sobre
 * {@code alumnos:{groupId}:*} y nada mas. Se usa la forma {@code %R~} de
 * Redis 7, que concede un patron de claves solo para lectura, asi que un
 * intento de escritura sobre el namespace de la catedra falla aca igual que
 * fallaria en produccion. Dos pruebas lo comprueban con comandos crudos: el
 * puerto no ofrece escrituras, asi que no hay otra forma de demostrar que el
 * permiso no esta.
 *
 * <p><b>El adaptador se arma a mano</b> con un doble del unico puerto que no
 * es Redis —la configuracion de integracion, de donde salen host, puerto,
 * usuario y clave— igual que hace la prueba del adaptador del snapshot con
 * el token. Las propiedades son las de verdad: los timeouts son parte de lo
 * que se prueba.
 */
class RedisPublishedCatalogAdapterTest {

    private static final String USUARIO = "grp_" + IntegrationFixtures.GROUP_ID;
    private static final String CLAVE = IntegrationFixtures.REDIS_PASSWORD_FALSA;

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
        aclDeLaCatedra();
    }

    @AfterAll
    static void apagarRedis() {
        REDIS.stop();
    }

    /** La configuracion que el adaptador va a ver. Las pruebas la cambian. */
    private IntegrationConfig configuracion;

    private CatedraRedisConnection conexion;
    private RedisPublishedCatalogAdapter adaptador;

    @BeforeEach
    void prepararAdaptador() {
        redisCli("FLUSHALL");
        configuracion = apuntandoA(REDIS.getMappedPort(6379), CLAVE);
        conexion = conexionCon(() -> configuracion);
        adaptador = new RedisPublishedCatalogAdapter(
                conexion, new PublishedCatalogMapper(new ObjectMapper()));
    }

    @AfterEach
    void cerrarConexion() {
        conexion.destroy();
    }

    // --- Lo que la catedra publica ----------------------------------------

    @Test
    @DisplayName("Lee las dos claves de version de §14.2")
    void leeLasVersiones() {
        redisCli("SET", PublishedCatalogKeys.CURRENT_VERSION, "7");
        redisCli("SET", PublishedCatalogKeys.OLDEST_AVAILABLE_VERSION, "4");

        assertThat(adaptador.currentVersion()).contains(7L);
        assertThat(adaptador.oldestAvailableVersion()).contains(4L);
    }

    @Test
    @DisplayName("Lee el hash de metadata con sus tres campos")
    void leeLaMetadata() {
        dadaLaMetadataDeLaReferencia();

        PublishedCatalogMetadata metadata = adaptador.metadata().orElseThrow();

        assertThat(metadata.getCurrentVersion())
                .isEqualTo(PublishedCatalogFixtures.VERSION_ACTUAL);
        assertThat(metadata.getOldestAvailableVersion())
                .isEqualTo(PublishedCatalogFixtures.VERSION_MAS_ANTIGUA);
        assertThat(metadata.getPublishedAt())
                .isEqualTo(Instant.parse(PublishedCatalogFixtures.PUBLICADO));
    }

    @Test
    @DisplayName("Lee las tres entidades del ejemplo de §14.4 por su id")
    void leeLasTresEntidades() {
        dadasLasEntidadesDeLaReferencia();

        assertThat(adaptador.findCategory(PublishedCatalogFixtures.CATEGORIA_ID))
                .get()
                .satisfies(categoria -> {
                    assertThat(categoria.getName()).isEqualTo("Clinica medica");
                    assertThat(categoria.getDescription()).isEqualTo("Atencion clinica general");
                    assertThat(categoria.isEnabled()).isTrue();
                    assertThat(categoria.getCreatedAt())
                            .isEqualTo(Instant.parse("2026-07-01T12:00:00Z"));
                });
        assertThat(adaptador.findProfessional(PublishedCatalogFixtures.PROFESIONAL_ID))
                .get()
                .satisfies(profesional -> {
                    assertThat(profesional.getCategoryId())
                            .isEqualTo(PublishedCatalogFixtures.CATEGORIA_ID);
                    assertThat(profesional.getFirstName()).isEqualTo("Ana");
                    assertThat(profesional.getLastName()).isEqualTo("Gomez");
                });
        assertThat(adaptador.findSchedule(PublishedCatalogFixtures.HORARIO_ID))
                .get()
                .satisfies(horario -> {
                    assertThat(horario.getProfessionalId())
                            .isEqualTo(PublishedCatalogFixtures.PROFESIONAL_ID);
                    assertThat(horario.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
                    assertThat(horario.getStartTime()).isEqualTo(LocalTime.of(9, 0));
                    assertThat(horario.getEndTime()).isEqualTo(LocalTime.of(12, 0));
                    assertThat(horario.getSlotDurationMinutes()).isEqualTo(30);
                });
    }

    @Test
    @DisplayName("Una entidad deshabilitada se lee como baja logica, no se descarta")
    void leeLasDeshabilitadas() {
        redisCli("HSET", PublishedCatalogKeys.PROFESSIONALS, "16",
                PublishedCatalogFixtures.profesionalDeshabilitado(16L));

        // §14.4: las deshabilitadas permanecen en los hashes. Filtrarlas aca
        // esconderia la baja en lugar de aplicarla: #25 necesita verla para
        // marcar la fila local como deshabilitada.
        assertThat(adaptador.findProfessional(16L)).get()
                .satisfies(profesional -> assertThat(profesional.isEnabled()).isFalse());
    }

    @Test
    @DisplayName("Los campos que el contrato todavia no tiene se ignoran")
    void ignoraLosCamposDesconocidos() {
        // El JSON del fixture trae un "telefono" que no existe en §14.4. §4
        // pide tolerar campos nuevos compatibles, asi que esto no puede
        // fallar: si fallara, un agregado de la catedra tumbaria la
        // sincronizacion incremental completa.
        redisCli("HSET", PublishedCatalogKeys.PROFESSIONALS, "16",
                PublishedCatalogFixtures.profesionalDeshabilitado(16L));

        assertThat(adaptador.findProfessional(16L)).isPresent();
    }

    // --- Ausente no es lo mismo que invalido ------------------------------

    @Test
    @DisplayName("Con Redis vacio, todo es vacio y nada es un error")
    void laAusenciaEsUnEstadoLegitimo() {
        // Es el estado del origen antes de la primera publicacion. Si esto
        // fuera una excepcion, el arranque de #27 tendria que tratar "la
        // catedra todavia no publico" como una falla.
        assertThat(adaptador.currentVersion()).isEmpty();
        assertThat(adaptador.oldestAvailableVersion()).isEmpty();
        assertThat(adaptador.metadata()).isEmpty();
        assertThat(adaptador.findCategory(1L)).isEmpty();
        assertThat(adaptador.findProfessional(15L)).isEmpty();
        assertThat(adaptador.findSchedule(21L)).isEmpty();
    }

    @Test
    @DisplayName("Un field que no esta en un hash que si existe tambien es vacio")
    void unFieldAusenteEsVacio() {
        dadasLasEntidadesDeLaReferencia();

        assertThat(adaptador.findProfessional(999L)).isEmpty();
    }

    @Test
    @DisplayName("Una version que no es un numero es un error, no un vacio")
    void unaVersionInvalidaEsUnError() {
        redisCli("SET", PublishedCatalogKeys.CURRENT_VERSION, "siete");

        // Es la otra mitad del criterio: la clave existe, asi que la catedra
        // publico algo, y lo que publico no cumple el contrato. Devolver
        // vacio diria "no hay version publicada", que es falso y llevaria a
        // la decision equivocada.
        assertThatThrownBy(() -> adaptador.currentVersion())
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(PublishedCatalogKeys.CURRENT_VERSION)
                .hasMessageContaining("siete");
    }

    @Test
    @DisplayName("Un JSON que no se puede leer nombra la clave y el field")
    void unJsonRotoEsUnError() {
        redisCli("HSET", PublishedCatalogKeys.PROFESSIONALS, "15", "{esto no es json");

        assertThatThrownBy(() -> adaptador.findProfessional(15L))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(PublishedCatalogKeys.PROFESSIONALS)
                .hasMessageContaining("15");
    }

    @Test
    @DisplayName("Si el field y el id del JSON no coinciden, se rechaza")
    void elIdTieneQueCoincidirConElField() {
        // El field dice 16 y el JSON dice 15. Sin este chequeo, pedir el 16
        // devolveria al profesional 15 y #25 lo aplicaria con el id
        // equivocado: dos filas mezcladas en la copia local y ningun error.
        redisCli("HSET", PublishedCatalogKeys.PROFESSIONALS, "16",
                PublishedCatalogFixtures.profesional());

        assertThatThrownBy(() -> adaptador.findProfessional(16L))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("§14.4");
    }

    @Test
    @DisplayName("A un hash de metadata incompleto le falta un campo, y lo dice")
    void laMetadataIncompletaEsUnError() {
        redisCli("HSET", PublishedCatalogKeys.METADATA, "currentVersion", "7");

        assertThatThrownBy(() -> adaptador.metadata())
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("oldestAvailableVersion");
    }

    // --- Los permisos de §14.1 --------------------------------------------

    @Test
    @DisplayName("La ACL no permite escribir en catedra:sync:*")
    void noSePuedeEscribirEnElNamespaceDeLaCatedra() {
        // El puerto no ofrece ninguna escritura, asi que esto se comprueba
        // con un comando crudo sobre la misma conexion que usa el adaptador.
        // La prueba no vigila al adaptador: vigila que el permiso que
        // justifica no haber escrito nunca sea real. Si manana alguien
        // agregara un metodo que escribe, fallaria en produccion con NOPERM,
        // y esta prueba es la que muestra por que.
        assertThatThrownBy(() -> conexion.comandos()
                        .set(PublishedCatalogKeys.CURRENT_VERSION, "99"))
                .isInstanceOf(RedisCommandExecutionException.class)
                .hasMessageContaining("NOPERM");
    }

    @Test
    @DisplayName("La ACL no da ningun acceso a catedra:holds:*")
    void noSePuedeTocarElNamespaceDeHolds() {
        // §14.1: los holds son internos de la catedra y se manipulan solo por
        // REST. No hay permiso ni para leerlos, asi que el adaptador no tiene
        // ninguna constante que los nombre.
        assertThatThrownBy(() -> conexion.comandos().get("catedra:holds:cualquiera"))
                .isInstanceOf(RedisCommandExecutionException.class)
                .hasMessageContaining("NOPERM");
    }

    @Test
    @DisplayName("El namespace propio si acepta escritura, que es lo que dice la ACL")
    void elNamespacePropioAceptaEscritura() {
        // No se usa todavia (§14.6) pero el permiso esta, y tenerlo probado
        // deja claro que la restriccion de arriba es del namespace de la
        // catedra y no de la cuenta.
        String propia = "alumnos:" + IntegrationFixtures.GROUP_ID + ":diagnostics:last-sync";

        conexion.comandos().set(propia, "2026-11-01T18:00:00Z");

        assertThat(conexion.comandos().get(propia)).isEqualTo("2026-11-01T18:00:00Z");
    }

    // --- Cuando Redis no esta -------------------------------------------

    @Test
    @DisplayName("Si no se llega a Redis, es un problema de disponibilidad")
    void siNoSeLlegaARedisEsUnErrorDeDisponibilidad() {
        // Puerto cerrado: el connect timeout corta y el mensaje manda a
        // mirar ZeroTier, que es la causa mas comun.
        CatedraRedisConnection inalcanzable = conexionCon(() -> apuntandoA(1, CLAVE));

        assertThatThrownBy(() -> new RedisPublishedCatalogAdapter(
                        inalcanzable, new PublishedCatalogMapper(new ObjectMapper()))
                        .currentVersion())
                .isInstanceOf(PublishedCatalogUnavailableException.class)
                .hasMessageContaining("ZeroTier");
    }

    @Test
    @DisplayName("Si la clave de Redis es otra, la falla no se confunde con un dato faltante")
    void laClaveEquivocadaEsUnErrorDeDisponibilidad() {
        CatedraRedisConnection conClaveMala =
                conexionCon(() -> apuntandoA(REDIS.getMappedPort(6379), "clave-que-no-es"));

        assertThatThrownBy(() -> new RedisPublishedCatalogAdapter(
                        conClaveMala, new PublishedCatalogMapper(new ObjectMapper()))
                        .currentVersion())
                .isInstanceOf(PublishedCatalogUnavailableException.class);
    }

    @Test
    @DisplayName("Sin redisPassword, el mensaje manda al alta de la cuenta tecnica")
    void sinClaveAvisaDelAlta() {
        CatedraRedisConnection sinClave =
                conexionCon(() -> IntegrationFixtures.conEstado(ProvisioningStatus.PENDING));

        assertThatThrownBy(() -> sinClave.comandos())
                .isInstanceOf(PublishedCatalogUnavailableException.class)
                .hasMessageContaining("§5.1");
    }

    @Test
    @DisplayName("Si la configuracion de integracion cambia, se reconecta")
    void siCambiaLaConfiguracionSeReconecta() {
        redisCli("SET", PublishedCatalogKeys.CURRENT_VERSION, "7");
        assertThat(adaptador.currentVersion()).contains(7L);

        // La configuracion se puede releer y los datos de Redis pueden
        // cambiar (§5.3, §5.4). Si la conexion vieja se reusara, esto
        // seguiria leyendo del servidor anterior sin que nadie se enterara.
        configuracion = apuntandoA(1, CLAVE);

        assertThatThrownBy(() -> adaptador.currentVersion())
                .isInstanceOf(PublishedCatalogUnavailableException.class);
    }

    // --- Auxiliares -------------------------------------------------------

    private CatedraRedisConnection conexionCon(GetIntegrationConfigUseCase integracion) {
        return new CatedraRedisConnection(
                integracion,
                new CatedraRedisProperties(Duration.ofSeconds(1), Duration.ofSeconds(2)));
    }

    private static IntegrationConfig apuntandoA(int puerto, String clave) {
        return IntegrationFixtures.conRedisEn(
                REDIS.getHost(), puerto, USUARIO, Secret.of(clave));
    }

    private void dadaLaMetadataDeLaReferencia() {
        redisCli("HSET", PublishedCatalogKeys.METADATA,
                "currentVersion", String.valueOf(PublishedCatalogFixtures.VERSION_ACTUAL),
                "oldestAvailableVersion",
                String.valueOf(PublishedCatalogFixtures.VERSION_MAS_ANTIGUA),
                "publishedAt", PublishedCatalogFixtures.PUBLICADO);
    }

    private void dadasLasEntidadesDeLaReferencia() {
        redisCli("HSET", PublishedCatalogKeys.PROFESSIONAL_CATEGORIES,
                String.valueOf(PublishedCatalogFixtures.CATEGORIA_ID),
                PublishedCatalogFixtures.categoria());
        redisCli("HSET", PublishedCatalogKeys.PROFESSIONALS,
                String.valueOf(PublishedCatalogFixtures.PROFESIONAL_ID),
                PublishedCatalogFixtures.profesional());
        redisCli("HSET", PublishedCatalogKeys.WEEKLY_SCHEDULES,
                String.valueOf(PublishedCatalogFixtures.HORARIO_ID),
                PublishedCatalogFixtures.horario());
    }

    /**
     * Configura el usuario con los permisos que describe §14.1.
     *
     * <p>{@code %R~} concede un patron de claves <b>solo para lectura</b>, y
     * {@code %RW~} lectura y escritura. Es lo que hace que las pruebas de
     * permisos de arriba prueben algo: sin esto, el usuario tendria acceso a
     * todo y escribir en {@code catedra:sync:*} funcionaria.
     */
    private static void aclDeLaCatedra() {
        redisCli("ACL", "SETUSER", USUARIO, "on", ">" + CLAVE,
                "%R~" + PublishedCatalogKeys.NAMESPACE + "*",
                "%RW~alumnos:" + IntegrationFixtures.GROUP_ID + ":*",
                "+@read", "+@write", "+@connection");
    }

    /** Corre un comando como el usuario por defecto, que no tiene ACL. */
    private static void redisCli(String... comando) {
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
}

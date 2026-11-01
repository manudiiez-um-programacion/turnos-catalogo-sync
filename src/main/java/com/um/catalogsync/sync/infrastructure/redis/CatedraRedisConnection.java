package com.um.catalogsync.sync.infrastructure.redis;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.ports.in.GetIntegrationConfigUseCase;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

/**
 * La conexion al Redis de la catedra, abierta la primera vez que se la
 * necesita y reusada despues.
 *
 * <p><b>Por que no es un bean autoconfigurado.</b> Los datos de conexion no
 * estan en la configuracion de este servicio: llegan en el objeto
 * {@code integration} de la catedra (§14.1), que se resuelve despues del
 * arranque y puede cambiar. Un {@code RedisConnectionFactory} armado por
 * Spring Boot a partir de {@code spring.data.redis.*} tendria que inventar
 * un host al arrancar y nunca enterarse del verdadero.
 *
 * <p><b>Por que se abre tarde y no al arrancar.</b> Pedir la configuracion de
 * integracion dispara un login contra la catedra, y el arranque no puede
 * depender de que la red privada este disponible: el servicio tiene que
 * levantar igual y fallar cuando se intenta usar lo que no esta (es la misma
 * decision que la sincronizacion de arranque, #14). Asi que la conexion se
 * abre en la primera lectura.
 *
 * <p><b>Por que se reconecta si cambia la configuracion.</b> La
 * configuracion de integracion se puede releer (§5.3, §5.4: una credencial
 * expuesta se rota). Si el host, el puerto o el usuario cambiaron, la
 * conexion vieja apunta al lugar viejo y seguiria "funcionando" hasta que
 * alguien se diera cuenta. Se compara una huella de esos tres valores —sin
 * la clave, que no hace falta para decidir— y si cambio se cierra y se abre
 * de nuevo.
 *
 * <p><b>Un solo cliente y una sola conexion.</b> La API sincronica de Lettuce
 * es segura entre hilos sobre una conexion compartida, asi que no hace falta
 * un pool: multiplexa los comandos sobre el mismo socket. Abrir una conexion
 * por lectura costaria un saludo completo —con su {@code AUTH}— sobre un
 * enlace de ~228 ms (D6) cada vez.
 */
@Slf4j
@RequiredArgsConstructor
public class CatedraRedisConnection implements DisposableBean {

    private final GetIntegrationConfigUseCase integracion;
    private final CatedraRedisProperties propiedades;

    private RedisClient cliente;
    private StatefulRedisConnection<String, String> conexion;

    /** Host, puerto y usuario de la conexion abierta. Nunca la clave. */
    private String huella;

    /**
     * Los comandos sincronicos, abriendo la conexion si hace falta.
     *
     * <p>{@code synchronized} en lugar de un doble chequeo como el de la
     * sesion tecnica: aca el camino caliente no es este metodo sino los
     * comandos que devuelve, que corren fuera del lock. Lo que se serializa
     * es abrir y cerrar, que es lo que no puede pasar dos veces a la vez.
     *
     * @throws PublishedCatalogUnavailableException si la configuracion de
     *     integracion no trae datos de Redis usables, o si no se pudo abrir
     *     la conexion
     */
    public synchronized RedisCommands<String, String> comandos() {
        IntegrationConfig config = integracion.get();
        String huellaActual = huellaDe(config);

        if (conexion != null && conexion.isOpen() && huellaActual.equals(huella)) {
            return conexion.sync();
        }
        if (conexion != null) {
            log.info("Reabriendo la conexion a Redis de la catedra");
            cerrar();
        }
        abrir(config, huellaActual);
        return conexion.sync();
    }

    private void abrir(IntegrationConfig config, String huellaNueva) {
        if (config.getRedisPassword() == null) {
            // Pasa con una cuenta que no esta PROVISIONED: el contrato
            // entrega redisPassword solo en ese estado (§5.1). El sintoma
            // sin este chequeo seria un NullPointerException al armar la
            // URI, que no dice nada sobre la causa.
            throw new PublishedCatalogUnavailableException(
                    "La configuracion de integracion no trae redisPassword, asi que la"
                            + " cuenta tecnica no esta habilitada todavia (§5.1): no hay"
                            + " manera de autenticarse contra el Redis de la catedra");
        }
        RedisURI uri = RedisURI.builder()
                .withHost(config.getRedisHost())
                .withPort(config.getRedisPort())
                .withAuthentication(
                        config.getRedisUsername(), config.getRedisPassword().value().toCharArray())
                // El timeout de comando va en la URI: es el que Lettuce
                // aplica por defecto a cada operacion sincronica.
                .withTimeout(propiedades.commandTimeout())
                .build();

        RedisClient clienteNuevo = RedisClient.create(uri);
        clienteNuevo.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(propiedades.connectTimeout())
                        .build())
                .build());
        try {
            conexion = clienteNuevo.connect();
        } catch (RedisException falla) {
            clienteNuevo.shutdown();
            throw new PublishedCatalogUnavailableException("la conexion inicial", falla);
        }
        cliente = clienteNuevo;
        huella = huellaNueva;
        // Sin host ni usuario: la direccion privada del servidor y el nombre
        // del usuario vienen de integration y no tienen por que estar en un
        // log. El groupId alcanza para saber de que integracion se habla.
        log.info("Conexion a Redis de la catedra abierta para la integracion \"{}\"",
                config.getGroupId());
    }

    /** Cierra lo que haya abierto. Lo llama Spring al apagar el contexto. */
    @Override
    public synchronized void destroy() {
        cerrar();
    }

    private void cerrar() {
        if (conexion != null) {
            conexion.close();
            conexion = null;
        }
        if (cliente != null) {
            cliente.shutdown();
            cliente = null;
        }
        huella = null;
    }

    private static String huellaDe(IntegrationConfig config) {
        return config.getRedisHost() + ":" + config.getRedisPort() + ":"
                + config.getRedisUsername();
    }
}

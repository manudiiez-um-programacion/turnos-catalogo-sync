package com.um.catalogsync.sync.infrastructure.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los timeouts con los que se habla con el Redis de la catedra.
 *
 * <p><b>Por que aca solo hay timeouts.</b> El host, el puerto, el usuario y
 * la clave <b>no</b> son configuracion de este servicio: llegan en tiempo de
 * ejecucion en el objeto {@code integration} que entrega la catedra (§14.1),
 * y pueden cambiar sin que se reinicie nada. Ponerlos tambien en un
 * {@code application.yaml} daria dos fuentes para el mismo dato y la
 * pregunta inevitable de cual gana. Lo unico que decide este servicio es
 * cuanto esta dispuesto a esperar.
 *
 * <p><b>Por que los dos timeouts y no uno.</b> Son cosas distintas y las dos
 * importan. Sin {@code connectTimeout}, un host inalcanzable tarda lo que
 * tarde el sistema operativo en rendirse —minutos, en una red privada que no
 * responde—. Sin {@code commandTimeout}, un servidor que acepta la conexion
 * y no contesta deja el comando colgado para siempre. El enlace a la catedra
 * es ZeroTier y tiene ~228 ms de ida y vuelta medidos (D6), asi que los
 * valores son holgados a proposito; el ajuste fino va con los reintentos
 * (#31).
 *
 * <p>No hay secretos, asi que no hace falta un {@code toString()} propio
 * —y no se agrega uno por si acaso: un {@code toString()} que oculta dos
 * duraciones solo estorba al diagnosticar—.
 *
 * @param connectTimeout cuanto esperar para establecer la conexion
 * @param commandTimeout cuanto esperar la respuesta de un comando
 */
@ConfigurationProperties(prefix = "catedra.redis")
public record CatedraRedisProperties(Duration connectTimeout, Duration commandTimeout) {

    /**
     * Rechaza los valores que harian esperar para siempre.
     *
     * <p>Se valida al construir y no donde se usa, al contrario de lo que
     * hace {@code CatedraApiProperties} con las credenciales. La razon es la
     * diferencia entre los dos casos: las credenciales pueden estar vacias
     * legitimamente mientras no exista la cuenta tecnica —Docker Compose
     * tiene que levantar igual— y un timeout en cero o negativo no tiene
     * ningun escenario valido. Un valor asi significa "esperar para
     * siempre" en Lettuce, que es justamente lo que estas propiedades
     * existen para evitar, y es mejor que el arranque falle nombrando la
     * propiedad que descubrirlo con un hilo colgado.
     */
    public CatedraRedisProperties {
        exigirPositivo(connectTimeout, "catedra.redis.connect-timeout");
        exigirPositivo(commandTimeout, "catedra.redis.command-timeout");
    }

    private static void exigirPositivo(Duration valor, String propiedad) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException(
                    propiedad + " tiene que ser una duracion positiva, y llego \"" + valor
                            + "\". En cero o negativa, Lettuce la interpreta como esperar sin"
                            + " limite, que es lo contrario de para que esta esta propiedad");
        }
    }
}

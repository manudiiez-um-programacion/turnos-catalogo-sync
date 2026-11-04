package com.um.catalogsync.sync.infrastructure.client.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cuantas veces y con cuanta espera se reintenta el pedido del snapshot
 * completo ante una falla transitoria (issue #31).
 *
 * <p><b>Por que vive en {@code sync} y no en {@code integration}.</b> El
 * {@code connectTimeout} y el {@code readTimeout} del {@code RestClient} son
 * de {@code integration} porque ese slice es dueño de la "configuracion de
 * acceso" a la catedra (ver {@code CatedraApiProperties}). Este reintento, en
 * cambio, es exclusivo de {@code RestCatalogSnapshotAdapter}: ni el login ni
 * la consulta de la integracion lo necesitan, porque los dos ya tienen su
 * propia politica (el 401 se reintenta reemitiendo el token, decidido en
 * #14). Ponerlo en {@code CatedraApiProperties} obligaria a
 * {@code RestCatalogSnapshotAdapter} a importar
 * {@code integration.infrastructure}, que es justo lo que la arquitectura
 * por slices prohibe.
 *
 * <p><b>Que no se reintenta.</b> Segun el ADR 0004 §7, una sincronizacion
 * fallida no se reintenta en el momento: la recupera la reconciliacion
 * periodica (#27) o la proxima notificacion, sin agregar carga inmediata
 * sobre un enlace de ~228 ms (D6). Este reintento es mas acotado y mas bajo
 * en la pila: amortigua una falla de red de un solo pedido HTTP —una
 * conexion que se cae, un 503 pasajero— dentro del mismo intento de
 * sincronizacion, no decide reintentar la sincronizacion completa.
 *
 * @param maxAttempts cuantas veces se intenta el pedido en total, contando el
 *     primero. En 1 equivale a no reintentar
 * @param backoff cuanto esperar entre un intento y el siguiente
 */
@ConfigurationProperties(prefix = "catedra.api.snapshot-retry")
public record CatalogSnapshotRetryProperties(int maxAttempts, Duration backoff) {

    /**
     * Rechaza lo que dejaria el reintento sin sentido o sin limite.
     *
     * <p>Mismo criterio que {@code CatedraRedisProperties} y
     * {@code CatedraKafkaProperties}: se valida al construir porque ninguno
     * de estos valores tiene un escenario valido fuera del rango permitido.
     */
    public CatalogSnapshotRetryProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "catedra.api.snapshot-retry.max-attempts tiene que ser al menos 1,"
                            + " y llego " + maxAttempts);
        }
        if (backoff == null || backoff.isNegative()) {
            throw new IllegalArgumentException(
                    "catedra.api.snapshot-retry.backoff no puede ser negativo,"
                            + " y llego \"" + backoff + "\"");
        }
    }
}

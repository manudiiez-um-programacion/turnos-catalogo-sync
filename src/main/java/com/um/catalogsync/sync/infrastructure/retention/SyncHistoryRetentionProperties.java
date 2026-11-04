package com.um.catalogsync.sync.infrastructure.retention;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cuanto se conserva el historial de sincronizaciones.
 *
 * <p>Es la retencion acotada que pide #28 para los errores. La ventana se
 * aplica sobre {@code sync_run} y los errores se van con su corrida, porque un
 * error no tiene sentido sin el intento que lo produjo.
 *
 * <p><b>Por que hace falta.</b> {@code sync_run} suma una fila por intento y
 * nada la achica sola. Con el chequeo periodico de #27 corriendo cada pocos
 * minutos, la tabla crece aunque nunca pase nada interesante: la mayoria de
 * esas corridas son "ya estaba al dia". Sin politica, el historial del primer
 * mes convive para siempre con el del ultimo.
 *
 * <p><b>Que tiene que cumplir la ventana, y en que se diferencia de la de
 * {@code processed_event}.</b> Aquella es una restriccion de
 * <i>correctitud</i>: borrar un {@code eventId} antes de tiempo hace que un
 * duplicado se procese de nuevo. Esta es una decision de <i>cuanta historia
 * se quiere poder mirar</i>: borrar de mas no rompe nada, solo deja menos
 * para diagnosticar. Noventa dias cubre con holgura la ventana de la entrega
 * y un cuatrimestre entero de clase.
 *
 * <p>Por eso tampoco hay un minimo que validar mas alla de que sea positiva:
 * en cero borraria todo el historial terminado en cada corrida, lo que
 * dejaria el reporte de estado sin errores que mostrar justo cuando mas
 * valen.
 *
 * @param retention cuanto tiempo se conserva una corrida terminada
 * @param purgeInterval cada cuanto corre la purga. Un intervalo largo no
 *     tiene costo: lo que se borra en cada corrida es lo que ya vencio, sin
 *     importar hace cuanto
 */
@ConfigurationProperties(prefix = "sync.history")
public record SyncHistoryRetentionProperties(Duration retention, Duration purgeInterval) {

    public SyncHistoryRetentionProperties {
        exigirPositivo(retention, "sync.history.retention");
        exigirPositivo(purgeInterval, "sync.history.purge-interval");
    }

    private static void exigirPositivo(Duration valor, String propiedad) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException(
                    propiedad + " tiene que ser una duracion positiva, y llego \"" + valor
                            + "\". En cero borraria el historial terminado entero en cada"
                            + " corrida, y el reporte de estado se quedaria sin errores que"
                            + " informar");
        }
    }
}

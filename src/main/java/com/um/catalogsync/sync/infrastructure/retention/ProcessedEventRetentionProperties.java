package com.um.catalogsync.sync.infrastructure.retention;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cuanto se conserva la memoria de idempotencia.
 *
 * <p><b>Por que hace falta una politica y no alcanza con dejar crecer la
 * tabla.</b> {@code processed_event} suma una fila por notificacion y nada
 * la achica sola: sin retencion seria el unico objeto del esquema que crece
 * para siempre, y lo haria al ritmo que la catedra publique versiones. No es
 * un problema de espacio —las filas son chicas— sino de que una tabla sin
 * limite declarado es una tabla que nadie va a revisar hasta que moleste.
 *
 * <p><b>Que tiene que cumplir la ventana.</b> Una sola cosa: ser mas larga
 * que el atraso maximo con el que un duplicado puede llegar. Si se borrara
 * un {@code eventId} que despues se reentrega, el evento se procesaria de
 * nuevo como si fuera nuevo. Treinta dias es holgado por varios ordenes de
 * magnitud frente a la retencion habitual de un topic de Kafka, que es lo
 * que acota de verdad cuanto puede tardar una reentrega.
 *
 * <p><b>Por que es configuracion y no una constante.</b> Porque depende del
 * entorno —de la retencion del topic, que la fija la catedra— y porque una
 * purga es una operacion destructiva: tener el numero a la vista en el
 * {@code application.yaml}, y poder bajarlo en una prueba, vale mas que
 * ahorrarse dos propiedades.
 *
 * @param retention cuanto tiempo se conserva un evento procesado
 * @param purgeInterval cada cuanto corre la purga. Un intervalo largo no
 *     tiene costo: lo que se borra en cada corrida es lo que ya vencio, sin
 *     importar hace cuanto
 */
@ConfigurationProperties(prefix = "sync.processed-events")
public record ProcessedEventRetentionProperties(Duration retention, Duration purgeInterval) {

    public ProcessedEventRetentionProperties {
        exigirPositivo(retention, "sync.processed-events.retention");
        exigirPositivo(purgeInterval, "sync.processed-events.purge-interval");
    }

    private static void exigirPositivo(Duration valor, String propiedad) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException(
                    propiedad + " tiene que ser una duracion positiva, y llego \"" + valor
                            + "\". En cero borraria la memoria de idempotencia entera en cada"
                            + " corrida, y un duplicado se volveria a procesar");
        }
    }
}

package com.um.catalogsync.sync.infrastructure.retention;

import com.um.catalogsync.sync.domain.ports.out.ProcessedEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borra de a poco la memoria de idempotencia que ya no hace falta.
 *
 * <p>Es la politica de retencion que el issue pide: sin ella,
 * {@code processed_event} crece con cada notificacion y nunca baja. Lo que
 * se borra es lo procesado hace mas de {@code retention}, que es tiempo de
 * sobra para que cualquier reentrega de Kafka ya haya ocurrido.
 *
 * <p><b>Por que un trabajo periodico y no un borrado al insertar.</b> Borrar
 * dentro de la transaccion del evento mezclaria dos cosas con riesgos
 * distintos: una purga lenta atrasaria la confirmacion del offset, y una
 * purga que falla haria fallar el procesamiento de un evento que no tiene
 * nada que ver. Separadas, lo peor que puede pasar es que la purga no corra
 * una vez.
 *
 * <p><b>Las fallas no se propagan.</b> Las invoca el planificador y no hay a
 * quien avisarle; lo que importa es que queden en el log. Una purga que no
 * pudo correr no rompe nada: el evento sigue siendo deduplicable, que es lo
 * unico de lo que depende la correccion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ProcessedEventRetentionProperties.class)
public class ProcessedEventRetentionJob {

    private final ProcessedEventRepository eventosProcesados;
    private final ProcessedEventRetentionProperties propiedades;

    @Scheduled(
            initialDelayString = "${sync.processed-events.purge-interval}",
            fixedDelayString = "${sync.processed-events.purge-interval}")
    public void purgar() {
        Instant limite = Instant.now().minus(propiedades.retention());
        try {
            int borrados = eventosProcesados.purgeProcessedBefore(limite);
            log.debug("Purga de eventos procesados anteriores a {}: {} filas", limite, borrados);
        } catch (RuntimeException falla) {
            log.warn("No se pudo purgar la tabla de eventos procesados: {}."
                            + " Se reintenta en el proximo ciclo",
                    falla.getMessage());
            log.debug("Detalle de la purga fallida", falla);
        }
    }
}

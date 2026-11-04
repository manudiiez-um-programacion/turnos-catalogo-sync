package com.um.catalogsync.sync.infrastructure.retention;

import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borra de a poco el historial de sincronizaciones que ya no hace falta.
 *
 * <p>Es la mitad operativa de la retencion que pide #28: sin esto,
 * {@code sync_run} y {@code sync_error} crecen con cada intento y nunca bajan.
 *
 * <p><b>Es el mismo patron que {@code ProcessedEventRetentionJob}</b> y eso es
 * deliberado: dos tablas con la misma necesidad —crecen solas, se limpian por
 * tiempo, la purga es destructiva y no urgente— deberian limpiarse de la misma
 * forma, para que quien lea una entienda la otra. No se unificaron en un solo
 * trabajo porque las ventanas significan cosas distintas: la de los eventos es
 * una restriccion de correctitud de la idempotencia, y esta es cuanta historia
 * se quiere poder mirar. Un unico intervalo las ataria.
 *
 * <p><b>Las fallas no se propagan.</b> Las invoca el planificador y no hay a
 * quien avisarle; lo que importa es que queden en el log. Una purga que no
 * pudo correr no rompe nada: lo peor que pasa es que la tabla quede un ciclo
 * mas grande.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SyncHistoryRetentionProperties.class)
public class SyncHistoryRetentionJob {

    private final SyncRunRepository corridas;
    private final SyncHistoryRetentionProperties propiedades;

    @Scheduled(
            initialDelayString = "${sync.history.purge-interval}",
            fixedDelayString = "${sync.history.purge-interval}")
    public void purgar() {
        Instant limite = Instant.now().minus(propiedades.retention());
        try {
            int borradas = corridas.purgeRunsFinishedBefore(limite);
            log.debug("Purga del historial de sincronizacion anterior a {}: {} corridas",
                    limite, borradas);
        } catch (RuntimeException falla) {
            log.warn("No se pudo purgar el historial de sincronizacion: {}."
                            + " Se reintenta en el proximo ciclo",
                    falla.getMessage());
            log.debug("Detalle de la purga fallida", falla);
        }
    }
}

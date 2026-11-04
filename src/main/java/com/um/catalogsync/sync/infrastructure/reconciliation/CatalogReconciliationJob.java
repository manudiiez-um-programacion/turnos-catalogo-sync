package com.um.catalogsync.sync.infrastructure.reconciliation;

import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Compara cada tanto la version local con la que publica la catedra, y
 * sincroniza si esta atrasada.
 *
 * <p><b>Es la red de seguridad del sistema entero.</b> §16: Kafka es una
 * notificacion y no la fuente del catalogo, y si una notificacion se pierde,
 * la comparacion de versiones tiene que permitir detectar y recuperar el
 * atraso. Sin esto, un mensaje que nunca llego —o una sincronizacion que
 * fallo despues de que el evento quedara registrado (#26)— dejaria la copia
 * local atrasada hasta el proximo reinicio. Con esto, el peor caso es un
 * intervalo de atraso.
 *
 * <p>Tambien cubre el arranque despues de una caida larga, que es lo que §8
 * pide: el servicio vuelve, compara y se pone al dia sin depender de que la
 * catedra reenvie nada.
 *
 * <p><b>No decide nada por su cuenta.</b> Llama a
 * {@code CatalogSyncService.synchronize}, que es donde viven el arbol de
 * decision de §6.2 y el lock que garantiza una sola sincronizacion a la vez.
 * Si una notificacion de Kafka esta sincronizando en este momento, este
 * chequeo se encuentra el lock tomado y se va sin hacer nada: lo que iba a
 * hacer lo esta haciendo el otro.
 *
 * <p><b>No genera carga ni logs cuando no hay nada que hacer</b>, que es un
 * criterio explicito del issue. El camino frecuente es una sola lectura de
 * {@code catedra:sync:metadata}, la comparacion, y nada mas: ninguna corrida
 * en {@code sync_run} y ninguna linea en el log por encima de {@code DEBUG}.
 * Un chequeo que logueara "todo en orden" cada cinco minutos enterraria los
 * mensajes que importan.
 *
 * <p><b>Las fallas no se propagan.</b> Lo invoca el planificador y no hay a
 * quien avisarle; lo que importa es que quede registrado, y la corrida
 * fallida ya quedo en {@code sync_run}. Propagar solo lograria una traza del
 * planificador arriba de la que ya se escribio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CatalogReconciliationProperties.class)
@ConditionalOnProperty(
        prefix = "sync.reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CatalogReconciliationJob {

    private final CatalogSyncService sincronizacion;

    @Scheduled(
            initialDelayString = "${sync.reconciliation.initial-delay}",
            fixedDelayString = "${sync.reconciliation.interval}")
    public void reconciliar() {
        try {
            sincronizacion.synchronize(SyncTrigger.RECONCILIATION)
                    .ifPresent(this::informar);
        } catch (RuntimeException falla) {
            log.warn("La reconciliacion periodica fallo: {}. Se reintenta en el proximo"
                            + " ciclo y el motivo quedo en sync_run",
                    falla.getMessage());
            log.debug("Detalle de la reconciliacion fallida", falla);
        }
    }

    /**
     * Avisa solo cuando hubo trabajo.
     *
     * <p>Que una corrida exista significa que la copia local estaba
     * atrasada, y eso si merece una linea: es la evidencia de que una
     * notificacion se perdio y de que el chequeo la recupero.
     */
    private void informar(SyncRun corrida) {
        log.info("La reconciliacion periodica encontro un atraso y lo recupero:"
                        + " corrida {} ({}, {}) → version {}",
                corrida.getId(), corrida.getMode(), corrida.getDecisionReason(),
                corrida.getToVersion());
    }
}

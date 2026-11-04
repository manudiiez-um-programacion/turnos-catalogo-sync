package com.um.catalogsync.sync.infrastructure.metrics;

import com.um.catalogsync.sync.domain.model.CatalogUpdatedOutcome;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncRun;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Los contadores que {@code /actuator/metrics} publica sobre la
 * sincronizacion.
 *
 * <p>Son el criterio de #28 que pide "metricas de actuator equivalentes para
 * las sincronizaciones y los eventos descartados por duplicado", y el segundo
 * es la evidencia de idempotencia que §11 exige: un contador de duplicados es
 * la forma mas directa de mostrar que un mensaje repetido no repitio ningun
 * efecto, sin pedirle a nadie que lea un log.
 *
 * <p><b>Por que vive en infraestructura y no detras de un puerto.</b> La
 * tentacion es un {@code SyncMetricsPort} en el dominio con un adaptador de
 * Micrometer. Se descarta porque ninguna decision del servicio depende de
 * estos numeros: no hay un caso de uso que lea un contador para decidir algo.
 * Un puerto de salida existe cuando la aplicacion <i>necesita</i> algo de
 * afuera, y acá es al revés —afuera necesita mirar lo que la aplicacion ya
 * hace—, asi que seria un puerto escrito solo para no nombrar a Micrometer en
 * una clase que de todas formas es un detalle.
 *
 * <p><b>Que significa cada contador.</b>
 *
 * <ul>
 *   <li>{@value #CORRIDAS} — una por corrida terminada, con etiquetas
 *       {@code mode}, {@code trigger} y {@code status}. Es el mismo hecho que
 *       {@code sync_run} registra fila por fila: el contador dice cuantas y
 *       de que tipo, la tabla dice cuales.
 *   <li>{@value #ERRORES} — una por corrida fallida, etiquetada por el
 *       {@code code} estable. Va aparte del anterior y no como una etiqueta
 *       mas porque un contador tiene que llevar siempre el mismo juego de
 *       etiquetas: un {@code code} que solo existe en las fallidas obligaria
 *       a inventar un valor para las exitosas.
 *   <li>{@value #EVENTOS} — una por notificacion resuelta, con etiqueta
 *       {@code outcome}. {@code outcome=DUPLICATE} es el contador de eventos
 *       descartados por duplicado.
 * </ul>
 *
 * <p>Las etiquetas son todas enums del dominio, asi que la cardinalidad esta
 * acotada por construccion. Eso no es un detalle: una etiqueta de
 * cardinalidad abierta —el id de la corrida, el detalle del error— haria
 * crecer la serie temporal sin limite, y es el error clasico de las metricas.
 */
@Component
@RequiredArgsConstructor
public class SyncMetrics {

    /** Corridas terminadas, por modo, motivo de disparo y resultado. */
    static final String CORRIDAS = "catalog.sync.runs";

    /** Corridas fallidas, por codigo estable del error. */
    static final String ERRORES = "catalog.sync.errors";

    /** Notificaciones resueltas, por resultado. {@code DUPLICATE} es el descarte. */
    static final String EVENTOS = "catalog.sync.events";

    private final MeterRegistry registro;

    /**
     * Cuenta una corrida que acaba de cerrarse.
     *
     * @param run la corrida ya cerrada, con su estado final
     */
    public void corridaTerminada(SyncRun run) {
        Counter.builder(CORRIDAS)
                .description("Corridas de sincronizacion terminadas")
                .tag("mode", run.getMode().name())
                .tag("trigger", run.getTriggeredBy().name())
                .tag("status", run.getStatus().name())
                .register(registro)
                .increment();
    }

    /**
     * Cuenta el error con el que se cerro una corrida.
     *
     * <p>Se llama ademas de {@link #corridaTerminada(SyncRun)} y no en lugar
     * de él: una corrida fallida es las dos cosas, una corrida que termino y
     * un error. Sumar los dos contadores no tendria sentido, pero nadie los
     * suma: uno responde "cuantas sincronizaciones hubo" y el otro "por que
     * fallan".
     *
     * @param error el error registrado, del que se toma el codigo estable
     */
    public void corridaFallida(SyncError error) {
        Counter.builder(ERRORES)
                .description("Corridas de sincronizacion fallidas, por codigo de error")
                .tag("code", error.getCode().name())
                .register(registro)
                .increment();
    }

    /**
     * Cuenta una notificacion de catalogo ya resuelta.
     *
     * @param outcome si se proceso o se reconocio como duplicado
     */
    public void notificacionResuelta(CatalogUpdatedOutcome outcome) {
        Counter.builder(EVENTOS)
                .description("Notificaciones CatalogUpdated resueltas, por resultado")
                .tag("outcome", outcome.name())
                .register(registro)
                .increment();
    }
}

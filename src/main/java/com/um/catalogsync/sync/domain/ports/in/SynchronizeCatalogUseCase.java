package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import java.util.Optional;

/**
 * Poner la copia local al dia eligiendo <b>como</b>: con cambios
 * incrementales o reconstruyendo con el snapshot completo.
 *
 * <p>Es el nodo de decision del diagrama de §16, el {@code alt} entre
 * "historial continuo disponible" y "discontinuidad o version demasiado
 * antigua". Todo lo que hace falta para decidir ya existe: las versiones que
 * publica la catedra, la version local, y el recorrido incremental que
 * informa cuando no alcanza. Lo que faltaba era el lugar donde se compara y
 * se elige.
 *
 * <p><b>Las cuatro razones que obligan a reconstruir</b> (§6.2) son las que
 * enumera {@code SyncDecisionReason}: no hay version local, la version local
 * quedo por debajo de {@code oldest-available-version}, falta una version
 * del rango necesario, o el estado del origen no se pudo verificar. Las
 * cuatro terminan en la misma operacion y se registran distinto, porque para
 * quien revisa no significan lo mismo.
 *
 * <p><b>Por que es un puerto de entrada y no un servicio interno.</b> Porque
 * lo invocan tres cosas desde afuera del slice: la notificacion de Kafka
 * (#24), el chequeo periodico de reconciliacion (#27) y la operacion manual.
 * Los tres pasan un {@link SyncTrigger} distinto y ninguno sabe nada de
 * Redis ni de snapshots.
 */
public interface SynchronizeCatalogUseCase {

    /**
     * Compara el estado local con el del origen y sincroniza si hace falta.
     *
     * <p><b>El {@link Optional} vacio significa "no habia nada que
     * hacer"</b>, y es el resultado normal: la copia local ya estaba en la
     * version que publica la catedra. No se registra corrida en ese caso, a
     * proposito —un historial con una fila por cada chequeo que no hizo nada
     * no deja ver las que si hicieron algo— y es tambien lo que pide #27:
     * el chequeo periodico no tiene que generar carga ni logs cuando no hay
     * novedades.
     *
     * <p>Cuando si hubo trabajo, lo que vuelve es la corrida ya cerrada, con
     * su modo y su motivo. Si el recorrido incremental arranco y se encontro
     * con una version faltante, la corrida que vuelve es la de la
     * reconstruccion: es la que describe como quedo la copia local.
     *
     * @param trigger quien pidio la sincronizacion
     * @return la corrida que se hizo, o vacio si la copia local ya estaba al
     *     dia
     * @throws RuntimeException la falla original, despues de registrarla:
     *     igual que en {@link RunFullSyncUseCase}, quien pidio sincronizar
     *     tiene que enterarse de que no se hizo
     */
    Optional<SyncRun> synchronize(SyncTrigger trigger);
}

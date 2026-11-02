package com.um.catalogsync.sync.domain.ports.out;

import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;

/**
 * Puerto de salida del historial de corridas de sincronizacion.
 *
 * <p>No se parece a los puertos de catalogo y no deberia: no hay upsert ni
 * reemplazo de coleccion. Lo que hay son <b>tres transiciones</b>, y estan
 * modeladas como tres operaciones en lugar de un {@code save(SyncRun)}
 * generico a proposito.
 *
 * <p><b>Por que tres operaciones y no un save.</b> Con un {@code save} el
 * llamador podria escribir una corrida {@code SUCCEEDED} sin
 * {@code finishedAt}, o una {@code FAILED} con {@code toVersion} puesto:
 * dos filas que mienten y que la base solo atajaria en el primer caso, por
 * el CHECK. Con estas tres firmas esos estados no se pueden expresar. La
 * invariante no se sostiene por disciplina de quien llama, se sostiene
 * porque no hay forma de romperla.
 *
 * <p><b>Las tres escriben en su propia transaccion</b>, separadas de la que
 * aplica los datos. Es el punto central: si la unidad de trabajo falla y
 * hace rollback, el rollback <b>no</b> se tiene que llevar el registro de
 * que fallo. Un historial que desaparece cuando hay un error es peor que no
 * tener historial, porque da la impresion de que nunca se intento nada.
 * Es la diferencia exacta con {@code CatalogVersionRepository.advanceTo},
 * que si comparte la transaccion del llamador y por eso retrocede con ella.
 */
public interface SyncRunRepository {

    /**
     * Anota que arranca una corrida y la deja en {@code RUNNING}.
     *
     * <p>Se llama <b>antes</b> de empezar el trabajo y commitea sola, asi
     * que la fila existe aunque despues el proceso se caiga sin llegar a
     * cerrarla. Una corrida que queda en {@code RUNNING} para siempre es
     * informacion: dice que el servicio se cayo sincronizando.
     *
     * @param mode completa o incremental
     * @param trigger por que arranco
     * @param fromVersion version local al empezar, o {@code null} si la base
     *     esta recien inicializada
     * @return la corrida con el id que le asigno la base
     */
    SyncRun start(SyncMode mode, SyncTrigger trigger, Long fromVersion);

    /**
     * Cierra la corrida como {@code SUCCEEDED} y anota la version a la que
     * se llego.
     *
     * <p>Se llama <b>despues</b> de que la unidad de trabajo commiteo. El
     * orden importa: si se llamara antes, un fallo en el commit dejaria una
     * corrida declarada exitosa sobre datos que no se guardaron.
     *
     * @param runId el id que devolvio {@link #start}
     * @param toVersion la version que la copia local tiene ahora
     * @return la corrida cerrada
     */
    SyncRun succeed(long runId, long toVersion);

    /**
     * Cierra la corrida como {@code FAILED} y registra el motivo en
     * {@code sync_error}.
     *
     * <p>{@code toVersion} queda en {@code null}, que es la regla del
     * proyecto vista desde el historial: no se llego a ninguna version
     * nueva.
     *
     * @param runId el id que devolvio {@link #start}
     * @param code codigo estable del motivo, para no tener que parsear el
     *     detalle
     * @param detail texto libre para diagnosticar. Nunca lleva secretos: lo
     *     que se guarda es el mensaje de las excepciones del proyecto, que
     *     estan escritas para no incluirlos
     * @return la corrida cerrada
     */
    SyncRun fail(long runId, SyncErrorCode code, String detail);
}

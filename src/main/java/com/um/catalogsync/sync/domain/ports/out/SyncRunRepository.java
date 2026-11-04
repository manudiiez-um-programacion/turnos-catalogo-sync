package com.um.catalogsync.sync.domain.ports.out;

import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida del historial de corridas de sincronizacion.
 *
 * <p>No se parece a los puertos de catalogo y no deberia: no hay upsert ni
 * reemplazo de coleccion. Lo que hay son <b>tres transiciones</b> —y estan
 * modeladas como tres operaciones en lugar de un {@code save(SyncRun)}
 * generico a proposito—, mas las <b>lecturas y la purga</b> que necesita el
 * reporte de estado (#28).
 *
 * <p><b>Por que las lecturas estan en este mismo puerto y no en uno
 * aparte.</b> Son el otro lado de lo que estas tres operaciones escriben: la
 * ultima corrida y sus errores no son otra coleccion, son el historial que
 * {@link #start}, {@link #succeed} y {@link #fail} van dejando. Un puerto de
 * lectura separado sobre las mismas dos tablas obligaria a mantener dos
 * adaptadores de acuerdo sobre lo que significa "la ultima corrida".
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
     * @param trigger quien la pidio
     * @param reason por que se eligio este modo. Se guarda junto con la
     *     corrida porque es la mitad de la decision que {@code mode} no
     *     alcanza a contar: las tres discontinuidades de §6.2 producen todas
     *     una corrida completa y significan cosas distintas
     * @param fromVersion version local al empezar, o {@code null} si la base
     *     esta recien inicializada
     * @return la corrida con el id que le asigno la base
     */
    SyncRun start(SyncMode mode, SyncTrigger trigger, SyncDecisionReason reason,
            Long fromVersion);

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

    /**
     * La corrida mas reciente, haya terminado o no.
     *
     * <p><b>La mas reciente por orden de arranque, no la ultima que termino.</b>
     * Son distintas y la diferencia es justo el caso que importa: si hay una
     * corrida abierta, es la que describe el estado de ahora, y una corrida
     * anterior que si termino diria que todo esta cerrado mientras algo se
     * esta reconstruyendo.
     *
     * <p>El {@code Optional} vacio significa que nunca se intento
     * sincronizar: base recien inicializada y arranque que todavia no corrio.
     * No se traduce a una corrida ficticia, porque "nunca se intento" y
     * "se intento y fallo" son dos estados con reacciones distintas.
     *
     * @return la ultima corrida, o vacio si no hay ninguna
     */
    Optional<SyncRun> findLast();

    /**
     * Los ultimos errores registrados, del mas nuevo al mas viejo.
     *
     * <p>Transversales a las corridas y no los de una sola: lo que hace falta
     * informar es "que viene fallando", y con los errores de la ultima
     * corrida nada mas, una sincronizacion exitosa despues de diez fallidas
     * borraria del reporte las diez.
     *
     * @param limit cuantos devolver como maximo. Acotado por quien pregunta
     *     y no por el adaptador: el reporte no es un visor de historial, y
     *     una lista sin limite sobre una tabla que crece es una respuesta que
     *     tarde o temprano no se puede serializar
     * @return los errores, del mas nuevo al mas viejo. Vacia si no hubo
     *     ninguno
     */
    List<SyncError> findRecentErrors(int limit);

    /**
     * Borra las corridas que terminaron antes de un instante, con sus
     * errores.
     *
     * <p>Es la retencion acotada que pide #28, y hace falta por lo mismo que
     * la de {@code processed_event}: {@code sync_run} suma una fila por
     * intento y nada la achica sola. Con el chequeo periodico corriendo cada
     * pocos minutos, una tabla sin politica crece para siempre aunque nunca
     * pase nada.
     *
     * <p><b>Los errores se van con su corrida</b>, no por separado: la FK de
     * {@code sync_error} es {@code ON DELETE CASCADE} justamente porque un
     * error no tiene sentido sin el intento que lo produjo.
     *
     * <p><b>Dos cosas nunca se borran</b>, y las dos por la misma razon —que
     * el reporte de estado siga teniendo algo que decir—: una corrida abierta
     * (no termino, asi que no entra en el criterio) y la mas reciente, aunque
     * sea mas vieja que la ventana. Un servicio que estuvo quieto mas tiempo
     * del que dura la retencion informaria "ninguna sincronizacion" en lugar
     * de "la ultima fue hace mucho", que es peor: lo segundo es el dato que
     * se esta buscando.
     *
     * @param limite se borra lo que termino estrictamente antes de este
     *     instante
     * @return cuantas corridas se borraron
     */
    int purgeRunsFinishedBefore(Instant limite);
}

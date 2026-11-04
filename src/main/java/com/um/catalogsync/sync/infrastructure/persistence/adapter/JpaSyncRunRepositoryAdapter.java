package com.um.catalogsync.sync.infrastructure.persistence.adapter;

import com.um.catalogsync.sync.domain.model.SyncDecisionReason;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncRunEntity;
import com.um.catalogsync.sync.infrastructure.metrics.SyncMetrics;
import com.um.catalogsync.sync.infrastructure.persistence.mapper.SyncErrorMapper;
import com.um.catalogsync.sync.infrastructure.persistence.mapper.SyncRunMapper;
import com.um.catalogsync.sync.infrastructure.persistence.repository.JpaSyncErrorRepository;
import com.um.catalogsync.sync.infrastructure.persistence.repository.JpaSyncRunRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el puerto del historial con JPA.
 *
 * <p><b>Las tres operaciones son {@code REQUIRES_NEW}, y es lo contrario de
 * lo que hace {@code JpaCatalogVersionRepositoryAdapter}.</b> Esa oposicion
 * es el punto, no una inconsistencia:
 *
 * <ul>
 *   <li>el avance de version es {@code REQUIRED} porque <b>tiene</b> que
 *       irse con el rollback de los datos: una version commiteada sobre
 *       datos que no se guardaron seria una mentira;</li>
 *   <li>el registro de la corrida es {@code REQUIRES_NEW} porque <b>no</b>
 *       tiene que irse con ese rollback: si desapareciera, el historial
 *       perderia justamente las corridas fallidas, que son las unicas que
 *       vale la pena mirar.</li>
 * </ul>
 *
 * <p>Hoy el orquestador llama estas operaciones fuera de la transaccion de
 * datos, asi que {@code REQUIRED} alcanzaria. Se declara
 * {@code REQUIRES_NEW} igual porque la garantia no debe depender de que el
 * llamador siga ordenando las cosas asi: si manana alguien envuelve la
 * sincronizacion entera en una transaccion, el registro tiene que seguir
 * sobreviviendo al rollback. La propagacion es la que lo promete.
 *
 * <p><b>Las lecturas, al contrario, no abren transaccion propia</b>: son
 * {@code readOnly} y {@code SUPPORTS}, asi que si alguien pregunta por el
 * estado dentro de una transaccion, la aprovechan, y si no, no hay ninguna
 * que abrir. Un {@code REQUIRES_NEW} acá seria costo sin ninguna garantia a
 * cambio.
 *
 * <p><b>Los contadores de {@code /actuator/metrics} se incrementan desde
 * acá</b>, y no en los casos de uso que cierran las corridas. Es a proposito:
 * este adaptador es el <i>unico</i> lugar por donde pasan las tres
 * transiciones, asi que un contador puesto acá no puede desacoplarse de la
 * tabla. Puestos en la capa de aplicacion habria que acordarse de llamarlos
 * en cada camino —la sincronizacion completa, la incremental, la que
 * reconstruye despues de una incremental trunca— y el primero que se olvide
 * deja la metrica mintiendo sin que nada falle.
 *
 * <p>Lo que si puede pasar: el incremento ocurre antes del commit de esta
 * transaccion, asi que un fallo al commitear el registro contaria una corrida
 * que no quedo en la tabla. Se acepta —un contador de observabilidad que se
 * pase por uno en un caso que ademas significa que la base se cayo no cambia
 * ninguna decision— y no se arregla con un {@code TransactionSynchronization}
 * que agregaria mas maquinaria que la que vale el problema.
 *
 * <p>Nunca devuelve una entidad JPA: todo sale mapeado a {@link SyncRun}, y
 * nunca devuelve {@code null}: ver {@link #aDominio(SyncRunEntity)}.
 */
@Component
@RequiredArgsConstructor
public class JpaSyncRunRepositoryAdapter implements SyncRunRepository {

    private final JpaSyncRunRepository runs;
    private final JpaSyncErrorRepository errors;
    private final SyncRunMapper mapper;
    private final SyncErrorMapper errorMapper;
    private final SyncMetrics metricas;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncRun start(SyncMode mode, SyncTrigger trigger, SyncDecisionReason reason,
            Long fromVersion) {
        SyncRunEntity nueva = SyncRunEntity.builder()
                .mode(mode.name())
                .triggeredBy(trigger.name())
                .decisionReason(reason == null ? null : reason.name())
                .status(SyncStatus.RUNNING.name())
                .fromVersion(fromVersion)
                // RUNNING sin finishedAt y sin toVersion: es lo que exige el
                // CHECK ck_sync_run_finished_at, y lo unico que esta firma
                // permite expresar.
                .startedAt(ahora())
                .build();
        return aDominio(runs.save(nueva));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncRun succeed(long runId, long toVersion) {
        SyncRunEntity corrida = exigir(runId);
        corrida.setStatus(SyncStatus.SUCCEEDED.name());
        corrida.setToVersion(toVersion);
        corrida.setFinishedAt(ahora());

        SyncRun cerrada = aDominio(runs.save(corrida));
        metricas.corridaTerminada(cerrada);
        return cerrada;
    }

    /**
     * Cierra la corrida y escribe su error en la misma transaccion.
     *
     * <p>Las dos escrituras van juntas a proposito: una corrida
     * {@code FAILED} sin su motivo no sirve para nada, y separarlas abriria
     * la posibilidad de que quede una sin la otra.
     *
     * <p>{@code toVersion} se deja en {@code null} explicitamente y no se
     * "olvida": si la corrida venia de un reintento sobre la misma fila,
     * tiene que quedar limpio. No se llego a ninguna version nueva.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncRun fail(long runId, SyncErrorCode code, String detail) {
        Instant cuando = ahora();

        SyncRunEntity corrida = exigir(runId);
        corrida.setStatus(SyncStatus.FAILED.name());
        corrida.setToVersion(null);
        corrida.setFinishedAt(cuando);
        SyncRun cerrada = aDominio(runs.save(corrida));

        SyncError error = errorMapper.toDomain(errors.save(SyncErrorEntity.builder()
                .syncRunId(runId)
                .occurredAt(cuando)
                .code(code.name())
                .detail(detail)
                .build()));

        metricas.corridaTerminada(cerrada);
        metricas.corridaFallida(error);
        return cerrada;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<SyncRun> findLast() {
        return runs.findFirstByOrderByStartedAtDescIdDesc().map(this::aDominio);
    }

    /**
     * Los ultimos errores, acotados por quien pregunta.
     *
     * <p>El {@code PageRequest} se usa solo para limitar: no hay paginado que
     * exponer, y pedir la primera pagina de un tamaño dado es la forma que
     * Spring Data tiene de escribir un {@code LIMIT}.
     *
     * <p>Un limite que no sea positivo devuelve la lista vacia en lugar de
     * fallar: {@code PageRequest} rechazaria el cero con una excepcion, y
     * "mostrame cero errores" es una peticion coherente que no tiene por que
     * romper un reporte de estado.
     */
    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<SyncError> findRecentErrors(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return errors.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(errorMapper::toDomain)
                .toList();
    }

    /**
     * Aplica la retencion del historial.
     *
     * <p>{@code REQUIRES_NEW} por lo mismo que las tres transiciones, aunque
     * el motivo acá sea mas simple: un {@code DELETE} masivo de JPQL necesita
     * una transaccion y la invoca un trabajo periodico, que no tiene ninguna.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeRunsFinishedBefore(Instant limite) {
        return runs.purgarTerminadasAntesDe(limite);
    }

    /**
     * Mapea la fila a dominio y <b>garantiza que no sale un nulo</b>.
     *
     * <p>Hace falta por una razon concreta y vale escribirla, porque la linea
     * parece de mas. {@code SyncRunMapper.toDomain} devuelve {@code null} si
     * recibe {@code null} —es la convencion de todos los mappers del proyecto
     * y tiene su prueba—, y acá nunca recibe {@code null}: las filas vienen de
     * {@code save}, que no devuelve nulo, y de {@code exigir}, que falla
     * antes. Pero el analisis estatico no puede saberlo y sigue ese camino
     * hasta el final, asi que concluye que {@link #start} puede devolver nulo
     * y marca como posible {@code NullPointerException} cada
     * {@code corrida.getId()} de los casos de uso que la llaman.
     *
     * <p>Se arregla acá y no en el mapper porque <b>este</b> es el lugar que
     * promete un {@link SyncRun} no nulo: el puerto declara que las tres
     * transiciones devuelven la corrida, no un {@link Optional}. Cambiar el
     * mapper obligaria a tocar una convencion que comparten todos los slices
     * para resolver un problema de un solo adaptador.
     *
     * @throws NullPointerException si el mapeo devolviera nulo, que solo
     *     puede venir de un cambio en el mapper que rompa su contrato
     */
    private SyncRun aDominio(SyncRunEntity entity) {
        return Objects.requireNonNull(mapper.toDomain(entity),
                "El mapeo de una corrida de sincronizacion no puede devolver null");
    }

    /**
     * La corrida que se quiere cerrar, o un error de programacion.
     *
     * <p>No existe es {@code IllegalStateException} y no una excepcion de
     * dominio: los ids de corrida los devuelve {@link #start} en el mismo
     * flujo, asi que cerrar una que no esta solo puede venir de un bug, y no
     * hay nada que la sincronizacion pueda decidir al respecto.
     */
    private SyncRunEntity exigir(long runId) {
        return runs.findById(runId).orElseThrow(() -> new IllegalStateException(
                "No hay una corrida de sincronizacion con id " + runId + " para cerrar"));
    }

    /**
     * El instante del registro, truncado a microsegundos.
     *
     * <p>Por lo mismo que en {@code JpaCatalogVersionRepositoryAdapter}:
     * {@code TIMESTAMPTZ} tiene resolucion de microsegundos y
     * {@code Instant.now()} puede traer nanosegundos, asi que sin truncar lo
     * que devuelve este metodo y lo que se lee despues de la base no serian
     * el mismo valor.
     */
    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

package com.um.catalogsync.sync.infrastructure.persistence.adapter;

import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncRunEntity;
import com.um.catalogsync.sync.infrastructure.persistence.mapper.SyncRunMapper;
import com.um.catalogsync.sync.infrastructure.persistence.repository.JpaSyncErrorRepository;
import com.um.catalogsync.sync.infrastructure.persistence.repository.JpaSyncRunRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
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
 * <p>Nunca devuelve una entidad JPA: todo sale mapeado a {@link SyncRun}.
 */
@Component
@RequiredArgsConstructor
public class JpaSyncRunRepositoryAdapter implements SyncRunRepository {

    private final JpaSyncRunRepository runs;
    private final JpaSyncErrorRepository errors;
    private final SyncRunMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncRun start(SyncMode mode, SyncTrigger trigger, Long fromVersion) {
        SyncRunEntity nueva = SyncRunEntity.builder()
                .mode(mode.name())
                .triggeredBy(trigger.name())
                .status(SyncStatus.RUNNING.name())
                .fromVersion(fromVersion)
                // RUNNING sin finishedAt y sin toVersion: es lo que exige el
                // CHECK ck_sync_run_finished_at, y lo unico que esta firma
                // permite expresar.
                .startedAt(ahora())
                .build();
        return mapper.toDomain(runs.save(nueva));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncRun succeed(long runId, long toVersion) {
        SyncRunEntity corrida = exigir(runId);
        corrida.setStatus(SyncStatus.SUCCEEDED.name());
        corrida.setToVersion(toVersion);
        corrida.setFinishedAt(ahora());
        return mapper.toDomain(runs.save(corrida));
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
        SyncRunEntity cerrada = runs.save(corrida);

        errors.save(SyncErrorEntity.builder()
                .syncRunId(runId)
                .occurredAt(cuando)
                .code(code.name())
                .detail(detail)
                .build());

        return mapper.toDomain(cerrada);
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

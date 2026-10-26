package com.um.catalogsync.sync.infrastructure.persistence.mapper;

import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncRunEntity;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Traduce entre la entidad JPA de una corrida y el modelo de dominio.
 *
 * <p>Los tres enums se convierten con el mismo criterio que
 * {@code CatalogVersionMapper} usa para el origen, y por la misma razon: son
 * vocabulario <b>nuestro</b> y las columnas tienen un CHECK que lo
 * restringe. Un valor que no sea uno de los declarados no puede venir del
 * contrato ni de un bug de mapeo, solo de que alguien escribio en la tabla
 * por fuera de la aplicacion. Eso es un estado corrupto y no una
 * inconsistencia de datos externos, asi que corresponde un
 * {@code IllegalStateException}: no hay nada que la sincronizacion pueda
 * hacer al respecto.
 *
 * <p>Es la diferencia con {@code DayOfWeek.fromContract}, donde un valor
 * desconocido significa que el contrato de la catedra cambio y la
 * sincronizacion lo registra como inconsistencia.
 */
@Component
public class SyncRunMapper {

    public SyncRun toDomain(SyncRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return SyncRun.builder()
                .id(entity.getId())
                .mode(convertir(entity.getMode(), SyncMode::valueOf, "mode"))
                .triggeredBy(convertir(entity.getTriggeredBy(), SyncTrigger::valueOf,
                        "triggered_by"))
                .status(convertir(entity.getStatus(), SyncStatus::valueOf, "status"))
                .fromVersion(entity.getFromVersion())
                .toVersion(entity.getToVersion())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .build();
    }

    public SyncRunEntity toEntity(SyncRun run) {
        if (run == null) {
            return null;
        }
        return SyncRunEntity.builder()
                .id(run.getId())
                .mode(nombreDe(run.getMode()))
                .triggeredBy(nombreDe(run.getTriggeredBy()))
                .status(nombreDe(run.getStatus()))
                .fromVersion(run.getFromVersion())
                .toVersion(run.getToVersion())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .build();
    }

    private static String nombreDe(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    /**
     * @throws IllegalStateException si la columna tiene un valor que esta
     *     aplicacion nunca escribe
     */
    private static <E extends Enum<E>> E convertir(
            String almacenado, Function<String, E> aEnum, String columna) {
        if (almacenado == null) {
            return null;
        }
        try {
            return aEnum.apply(almacenado);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "La tabla sync_run tiene en " + columna + " el valor \"" + almacenado
                            + "\", que esta aplicacion nunca escribe", e);
        }
    }
}

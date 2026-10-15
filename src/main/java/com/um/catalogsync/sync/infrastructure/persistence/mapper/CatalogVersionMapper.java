package com.um.catalogsync.sync.infrastructure.persistence.mapper;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import org.springframework.stereotype.Component;

/**
 * Traduce entre la entidad JPA y el modelo de dominio.
 *
 * <p>La conversion del origen merece una nota, porque se resuelve distinto
 * que el dia de la semana en el slice de horarios y la diferencia es
 * intencional. Alla los siete dias los define un sistema externo, asi que
 * un valor desconocido significa que el contrato cambio y se lanza una
 * excepcion de dominio que la sincronizacion registra como inconsistencia.
 * Aca los dos origenes los escribimos nosotros y la columna tiene un CHECK
 * que los restringe: un valor que no sea uno de los dos no puede venir del
 * contrato ni de un bug de mapeo, solo de que alguien escribio en la tabla
 * por fuera de la aplicacion. Eso es un estado corrupto, no una
 * inconsistencia de datos externos, y corresponde un
 * {@code IllegalStateException}: no hay nada que la sincronizacion pueda
 * hacer al respecto.
 */
@Component
public class CatalogVersionMapper {

    public CatalogVersion toDomain(CatalogVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        return CatalogVersion.builder()
                .version(entity.getAppliedVersion())
                .appliedAt(entity.getAppliedAt())
                .source(aOrigen(entity.getSource()))
                .build();
    }

    public CatalogVersionEntity toEntity(CatalogVersion version) {
        if (version == null) {
            return null;
        }
        return CatalogVersionEntity.builder()
                .id(CatalogVersionEntity.ID_UNICA)
                .appliedVersion(version.getVersion())
                .appliedAt(version.getAppliedAt())
                .source(version.getSource() == null ? null : version.getSource().name())
                .build();
    }

    /** @throws IllegalStateException si la tabla tiene un origen que no escribimos nosotros */
    private static CatalogVersionSource aOrigen(String almacenado) {
        if (almacenado == null) {
            return null;
        }
        try {
            return CatalogVersionSource.valueOf(almacenado);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "La tabla catalog_version tiene el origen \"" + almacenado
                            + "\", que esta aplicacion nunca escribe", e);
        }
    }
}

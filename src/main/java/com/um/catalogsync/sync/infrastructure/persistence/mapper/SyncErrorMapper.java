package com.um.catalogsync.sync.infrastructure.persistence.mapper;

import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncErrorCode;
import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import org.springframework.stereotype.Component;

/**
 * Traduce la fila de un error al modelo de dominio.
 *
 * <p>Solo tiene direccion de salida, al revés que {@link SyncRunMapper}. No es
 * una omision: los errores no se escriben por un {@code save} de entidad
 * mapeada sino dentro de {@code SyncRunRepository.fail}, que arma la fila con
 * el codigo y el detalle que recibe. Un {@code toEntity} seria un metodo sin
 * llamadores que invitaria a escribir errores por fuera de esa transicion.
 *
 * <p>El codigo se convierte con el mismo criterio que los enums de
 * {@link SyncRunMapper}: un valor que esta aplicacion nunca escribe solo
 * puede venir de que alguien escribio en la tabla por fuera, y eso es un
 * estado corrupto y no un dato externo inconsistente.
 */
@Component
public class SyncErrorMapper {

    public SyncError toDomain(SyncErrorEntity entity) {
        if (entity == null) {
            return null;
        }
        return SyncError.builder()
                .syncRunId(entity.getSyncRunId())
                .occurredAt(entity.getOccurredAt())
                .code(convertir(entity.getCode()))
                .detail(entity.getDetail())
                .build();
    }

    /**
     * @throws IllegalStateException si la columna tiene un codigo que esta
     *     aplicacion nunca escribe
     */
    private static SyncErrorCode convertir(String almacenado) {
        try {
            return SyncErrorCode.valueOf(almacenado);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "La tabla sync_error tiene en code el valor \"" + almacenado
                            + "\", que esta aplicacion nunca escribe", e);
        }
    }
}

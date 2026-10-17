package com.um.catalogsync.professional.domain.ports.out;

import com.um.catalogsync.professional.domain.model.Professional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida del slice de profesionales.
 *
 * <p>Misma forma que el de categorias y por el mismo motivo: la unica
 * escritura viene de la sincronizacion, asi que expone {@code upsert} y
 * {@code replaceAll} en lugar de CRUD.
 *
 * <p>Las escrituras exigen que la categoria del profesional ya exista
 * localmente. Ver {@code UnknownCategoryException} para el porque.
 */
public interface ProfessionalRepository {

    /**
     * Inserta el profesional o lo reemplaza si ya existe, decidiendo por id.
     *
     * @throws com.um.catalogsync.professional.application.exception.UnknownCategoryException
     *     si la categoria del profesional no esta en la copia local
     */
    Professional upsert(Professional professional);

    /** @return el profesional, o vacio si no esta. Nunca {@code null}. */
    Optional<Professional> findById(Long id);

    List<Professional> findAll();

    /**
     * Reemplaza por completo el contenido local de profesionales.
     *
     * <p>Solo se ocupa de los profesionales: si hay horarios apuntando a
     * los que se borran, las FK son {@code RESTRICT} y la operacion falla.
     * El orden de borrado entre slices lo decide quien orquesta la
     * sincronizacion.
     *
     * @throws com.um.catalogsync.professional.application.exception.UnknownCategoryException
     *     si algun profesional referencia una categoria que no esta
     */
    List<Professional> replaceAll(Collection<Professional> professionals);

    long deleteByIdIn(Collection<Long> ids);
}

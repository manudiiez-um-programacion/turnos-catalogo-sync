package com.um.catalogsync.professional.domain.ports.out;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
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
     * Busca por categoria, nombre y estado, paginado.
     *
     * <p><b>Se resuelve entero en la base.</b> Filtrar en memoria sobre
     * {@link #findAll()} daria el mismo resultado hoy y seria una trampa: el
     * costo crece con el catalogo completo y no con la pagina pedida, y el
     * indice de trigramas que la migracion de M1 creo para esto no se usaria
     * nunca. Los filtros que vienen en {@code null} no se traducen a ninguna
     * condicion, en lugar de a un {@code :param IS NULL OR ...}, para que el
     * planificador vea solo las condiciones reales.
     *
     * <p><b>El orden es parte del contrato y tiene que ser total:</b>
     * apellido, nombre e id, ascendente. Los dos primeros son lo que un
     * usuario espera de un listado alfabetico; el id esta para desempatar.
     * Sin desempate, dos profesionales homonimos pueden salir en cualquier
     * orden entre dos consultas, y el que esta justo en el borde de la pagina
     * aparece repetido o se pierde al pedir la pagina siguiente.
     *
     * @param criteria filtros y pagina; el nombre ya viene normalizado
     * @return la pagina pedida, posiblemente vacia, con el total sin paginar
     */
    ProfessionalPage search(ProfessionalSearchCriteria criteria);

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

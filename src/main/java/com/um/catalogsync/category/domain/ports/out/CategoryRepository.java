package com.um.catalogsync.category.domain.ports.out;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: lo que el dominio de categorias necesita del
 * almacenamiento.
 *
 * <p>Expone {@code upsert} y {@code replaceAll} en lugar de
 * {@code create/update/delete}, que es el desvio respecto de la plantilla
 * CRUD de la skill. El motivo: la unica escritura sobre este slice viene de
 * la sincronizacion con la catedra, nunca de HTTP. Un cliente no crea una
 * categoria; llega una version del catalogo y se aplica. Pedirle al dominio
 * que distinga "crear" de "actualizar" lo obligaria a consultar antes si la
 * categoria ya existe, una pregunta que no le interesa.
 *
 * <p>Este puerto es tambien el que usa el slice {@code sync} para escribir
 * categorias: ningun otro slice toca el repositorio JPA de este.
 */
public interface CategoryRepository {

    /**
     * Inserta la categoria o la reemplaza si ya existe, decidiendo por id.
     *
     * @return la categoria tal como quedo guardada
     */
    ProfessionalCategory upsert(ProfessionalCategory category);

    /**
     * @return la categoria, o vacio si no esta. Nunca {@code null} ni una
     *     excepcion: que no exista es una respuesta valida, y quien decide
     *     si eso es un error es la capa de aplicacion.
     */
    Optional<ProfessionalCategory> findById(Long id);

    List<ProfessionalCategory> findAll();

    /**
     * Reemplaza por completo el contenido local de categorias por el que se
     * recibe. Es la operacion de la sincronizacion completa (#13).
     *
     * <p>Solo se ocupa de las categorias. Si hay profesionales apuntando a
     * las categorias que se borran, las FK son {@code RESTRICT} y la
     * operacion falla: el orden de borrado entre slices lo decide quien
     * orquesta la sincronizacion, no este puerto.
     *
     * @return las categorias que quedaron guardadas
     */
    List<ProfessionalCategory> replaceAll(Collection<ProfessionalCategory> categories);

    /**
     * Borra las categorias indicadas. Usado por la sincronizacion
     * incremental cuando una version informa bajas fisicas.
     *
     * @return cuantas se borraron
     */
    long deleteByIdIn(Collection<Long> ids);
}

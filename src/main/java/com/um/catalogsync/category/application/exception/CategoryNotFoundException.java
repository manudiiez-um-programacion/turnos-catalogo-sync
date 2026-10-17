package com.um.catalogsync.category.application.exception;

/**
 * La categoria pedida no esta en la copia local.
 *
 * <p>La lanza el facade del slice, no los puertos: ellos devuelven
 * {@code Optional} y no opinan sobre si la ausencia es un error. Depende de
 * quien pregunte — para una busqueda, no encontrar nada es normal; para una
 * consulta por id, es un 404.
 *
 * <p>No lleva anotaciones web: traducirla a una respuesta HTTP con
 * {@code application/problem+json} y un {@code code} estable (§13) es tarea
 * del manejador de errores, que llega en M2 con la API.
 */
public class CategoryNotFoundException extends RuntimeException {

    private final Long categoryId;

    public CategoryNotFoundException(Long categoryId) {
        super("No hay una categoria con id " + categoryId + " en la copia local del catalogo");
        this.categoryId = categoryId;
    }

    public Long getCategoryId() {
        return categoryId;
    }
}

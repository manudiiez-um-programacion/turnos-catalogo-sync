package com.um.catalogsync.professional.domain.model;

/**
 * Lo que se pide en una busqueda de profesionales: los filtros y la pagina.
 *
 * <p>Son los tres primeros filtros obligatorios de §4.1 —categoria, nombre y
 * habilitado—. La disponibilidad es el cuarto y se suma en #16.
 *
 * <p><b>Los tres filtros son opcionales y combinables.</b> Un {@code null} no
 * es "no encontre nada", es "no filtres por esto": con los tres en
 * {@code null} la busqueda devuelve el catalogo completo paginado. Por eso
 * {@code enabled} es {@code Boolean} y no {@code boolean}, que no podria
 * distinguir "quiero los deshabilitados" de "no me importa".
 *
 * <p><b>Por que vive en el dominio y no es un DTO web.</b> Define la semantica
 * de la busqueda: que el nombre se normaliza con la misma regla con la que se
 * guardo, y que hay un tamaño de pagina maximo. Si eso estuviera en el
 * controlador, cualquier otra forma de llamar a la busqueda —una prueba, un
 * caso de uso futuro— podria saltearselo.
 *
 * <p><b>El nombre se normaliza al construir.</b> Es el punto central del
 * filtro parcial: la columna {@code search_name} guarda el nombre completo
 * pasado por {@link SearchableName}, y el termino de busqueda tiene que pasar
 * por exactamente la misma funcion. Si las dos normalizaciones no
 * coincidieran, buscar "gomez" no encontraria a "Gómez" aunque el indice de
 * trigramas estuviera perfecto. Hacerlo en el constructor y no en el
 * adaptador deja una sola verdad: no existe un criterio con el nombre sin
 * normalizar.
 *
 * @param categoryId categoria exacta, o {@code null} para no filtrar
 * @param name termino <b>ya normalizado</b> para la coincidencia parcial, o
 *     {@code null} para no filtrar. Nunca es cadena vacia: un nombre en blanco
 *     equivale a no filtrar y se convierte en {@code null}
 * @param enabled estado exacto, o {@code null} para traer habilitados y
 *     deshabilitados
 * @param page numero de pagina, base cero
 * @param size cuantos elementos por pagina
 */
public record ProfessionalSearchCriteria(
        Long categoryId, String name, Boolean enabled, int page, int size) {

    /** Tamaño de pagina cuando el cliente no pide uno. */
    public static final int TAMANIO_POR_DEFECTO = 20;

    /**
     * Tope de elementos por pagina.
     *
     * <p>No es un numero arbitrario con forma de limite: sin tope, un cliente
     * pidiendo {@code size=100000} convierte una busqueda paginada en un
     * volcado de la tabla, y el costo lo paga el servicio, no quien lo pidio.
     * El valor es publico para que la validacion del borde web y esta
     * invariante usen la misma constante en lugar de dos numeros que pueden
     * separarse.
     */
    public static final int TAMANIO_MAXIMO = 100;

    public ProfessionalSearchCriteria {
        name = normalizarONulo(name);
        if (page < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa: " + page);
        }
        if (size < 1 || size > TAMANIO_MAXIMO) {
            throw new IllegalArgumentException(
                    "El tamaño de pagina tiene que estar entre 1 y " + TAMANIO_MAXIMO + ": " + size);
        }
    }

    /** Todo el catalogo, primera pagina: los tres filtros sin aplicar. */
    public static ProfessionalSearchCriteria primeraPagina() {
        return new ProfessionalSearchCriteria(null, null, null, 0, TAMANIO_POR_DEFECTO);
    }

    /**
     * Normaliza el termino con la misma regla con la que se guardo el nombre,
     * y trata el blanco como "no filtres".
     *
     * <p>{@code ?name=} y {@code ?name=%20} llegan como cadena vacia o
     * espacios. Dejarlos pasar produciria un {@code LIKE '%%'}, que no es un
     * error pero recorre el indice entero para no filtrar nada: es mas honesto
     * —y mas rapido— decir que no hay filtro.
     */
    private static String normalizarONulo(String name) {
        if (name == null) {
            return null;
        }
        String normalizado = SearchableName.normalize(name);
        return normalizado.isEmpty() ? null : normalizado;
    }

    /** @return si hay que aplicar el filtro por nombre */
    public boolean filtraPorNombre() {
        return name != null;
    }

    /** @return si hay que aplicar el filtro por categoria */
    public boolean filtraPorCategoria() {
        return categoryId != null;
    }

    /** @return si hay que aplicar el filtro por estado */
    public boolean filtraPorEstado() {
        return enabled != null;
    }
}

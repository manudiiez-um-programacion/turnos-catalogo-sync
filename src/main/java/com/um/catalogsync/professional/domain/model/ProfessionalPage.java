package com.um.catalogsync.professional.domain.model;

import java.util.List;

/**
 * Una pagina de resultados de busqueda.
 *
 * <p><b>Por que un tipo propio y no el {@code Page} de Spring Data.</b> El
 * puerto de salida lo devuelve, asi que su tipo cruza hacia el dominio: usar
 * el de Spring metería el framework de persistencia en una firma que el ADR
 * 0001 exige mantener libre de framework, y ataria la forma de la respuesta
 * —que es contrato con la app KMP— a la version de una libreria. Son cuatro
 * campos; el costo de tenerlos propios es menor que el de ese acoplamiento.
 *
 * <p><b>Una pagina vacia es una respuesta valida</b>, no un error. Es un
 * criterio de aceptacion de #15 y la diferencia con "no existe el recurso":
 * una combinacion de filtros que no matchea nada es una busqueda que se
 * resolvio bien y encontro cero. Por eso no hay ninguna excepcion asociada a
 * {@code content} vacio, ni aqui ni en la fachada.
 *
 * @param content los profesionales de esta pagina, en el orden estable del
 *     puerto: apellido, nombre e id
 * @param page numero de pagina devuelta, base cero
 * @param size tamaño de pagina pedido
 * @param totalElements cuantos profesionales matchean los filtros en total,
 *     sin paginar
 */
public record ProfessionalPage(
        List<Professional> content, int page, int size, long totalElements) {

    public ProfessionalPage {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /** Pagina vacia: los filtros no matchearon nada. */
    public static ProfessionalPage vacia(ProfessionalSearchCriteria criteria) {
        return new ProfessionalPage(List.of(), criteria.page(), criteria.size(), 0L);
    }

    /**
     * Cuantas paginas hay en total.
     *
     * <p>Se deriva en lugar de guardarse: un campo mas seria un dato que puede
     * contradecir a los otros tres.
     */
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
    }
}

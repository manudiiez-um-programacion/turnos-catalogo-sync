package com.um.catalogsync.professional.infrastructure.persistence.specification;

import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.infrastructure.persistence.entity.ProfessionalEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Traduce los filtros de dominio a predicados JPA.
 *
 * <p><b>Por que {@code Specification} y no un {@code @Query} con parametros
 * anulables.</b> La alternativa es un JPQL con {@code (:categoryId IS NULL OR
 * p.categoryId = :categoryId)} repetido por filtro. Funciona, y tiene dos
 * problemas concretos: el SQL que llega a PostgreSQL lleva siempre las tres
 * condiciones, asi que el planificador no puede elegir el indice de la unica
 * que importa, y el {@code LIKE} del nombre quedaria ahi aunque no haya
 * termino. Armando solo los predicados presentes, una busqueda por categoria
 * produce exactamente la consulta de una busqueda por categoria.
 *
 * <p><b>Vive en {@code infrastructure}</b> porque habla de {@code Root},
 * {@code CriteriaBuilder} y nombres de atributos JPA. Lo que entra es el
 * criterio de dominio; lo que sale es un detalle de persistencia que no cruza
 * hacia adentro.
 *
 * <p><b>La disponibilidad entra ya resuelta, como una lista de ids</b>, y no
 * como un {@code JOIN} contra {@code weekly_schedule}. El motivo es la regla
 * entre slices: construir ese cruce con la Criteria API pide la
 * {@code @Entity} del slice de horarios, que es su {@code infrastructure} y no
 * se importa. Escribir la tabla a mano en un JPQL seria el mismo acoplamiento
 * sin que el compilador lo muestre, que es peor. Quien resuelve los ids es el
 * puerto de dominio de ese slice, y el costo es una consulta mas y un
 * {@code IN} con tantos elementos como profesionales con agenda: decenas, en
 * un catalogo de catedra. Si el catalogo creciera a miles, lo que corresponde
 * es mover la busqueda entera a una consulta nativa con un {@code EXISTS}, y
 * decidir ahi si la frontera entre slices vale ese precio.
 */
public final class ProfessionalSpecifications {

    /**
     * Caracter de escape del {@code LIKE}.
     *
     * <p>Hace falta porque {@code %} y {@code _} son comodines: sin escapar,
     * un termino con {@code %} matchearia cualquier cosa y uno con {@code _}
     * cualquier caracter en esa posicion. No es una inyeccion —el valor viaja
     * como parametro, no concatenado al SQL— pero si un resultado incorrecto
     * y silencioso: buscar literalmente "a_b" traeria "aab".
     */
    private static final char ESCAPE = '\\';

    private ProfessionalSpecifications() {
    }

    /**
     * Arma la especificacion con los filtros que efectivamente vienen.
     *
     * <p>Sin ningun filtro devuelve una especificacion vacia, que Spring Data
     * traduce a una consulta sin {@code WHERE}: el catalogo completo paginado,
     * que es lo que corresponde cuando el cliente no pide nada.
     */
    public static Specification<ProfessionalEntity> de(
            ProfessionalSearchCriteria criteria, Collection<Long> idsConAgenda) {

        List<Specification<ProfessionalEntity>> filtros = new ArrayList<>(5);

        if (criteria.filtraPorCategoria()) {
            filtros.add(deCategoria(criteria.categoryId()));
        }
        if (criteria.filtraPorEstado()) {
            filtros.add(conEstado(criteria.enabled()));
        }
        if (criteria.filtraPorNombre()) {
            filtros.add(conNombreParecidoA(criteria.name()));
        }
        if (criteria.filtraPorDisponibilidad()) {
            // Las dos mitades del criterio de disponibilidad. Los ids ya
            // vienen filtrados por horario habilitado; lo que falta es que el
            // profesional tambien lo este.
            filtros.add(conEstado(true));
            filtros.add(conAlgunoDeEstosIds(idsConAgenda));
        }

        return Specification.allOf(filtros);
    }

    /** Igualdad exacta: la categoria es un id, no un texto. */
    private static Specification<ProfessionalEntity> deCategoria(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    private static Specification<ProfessionalEntity> conEstado(boolean enabled) {
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    /**
     * Coincidencia parcial sobre el nombre completo normalizado.
     *
     * <p>Compara contra {@code search_name}, que el mapper ya guardo en
     * minusculas y sin acentos, con un termino que paso por la misma funcion.
     * Por eso el predicado es un {@code LIKE} liso y no un {@code lower(...)}
     * ni un {@code unaccent(...)}: envolver la columna en una funcion la
     * volveria inutilizable para el indice GIN de trigramas, que es
     * exactamente lo que permite que {@code '%texto%'} no sea un scan
     * secuencial.
     *
     * <p>Busca sobre nombre y apellido juntos, no sobre cada uno por separado,
     * asi que "ana gomez" encuentra a Ana Gómez. Es lo que pide el criterio y
     * lo que un buscador hace.
     */
    private static Specification<ProfessionalEntity> conNombreParecidoA(String normalizado) {
        String patron = "%" + escapar(normalizado) + "%";
        return (root, query, cb) -> cb.like(root.get("searchName"), patron, ESCAPE);
    }

    /**
     * Restringe a los profesionales que el slice de horarios devolvio.
     *
     * <p>Es la mitad del filtro de disponibilidad que no se puede expresar
     * aca: cruzar con {@code weekly_schedule} exigiria la entidad JPA de otro
     * slice, y el ADR 0001 prohibe importar el {@code infrastructure} ajeno.
     * Asi que la lista de ids llega resuelta, por el puerto de dominio de ese
     * slice, y lo unico que queda es el {@code IN}.
     *
     * <p>Nunca recibe una lista vacia: el adaptador corta antes y devuelve la
     * pagina vacia sin consultar, porque un {@code IN ()} no es SQL valido.
     */
    private static Specification<ProfessionalEntity> conAlgunoDeEstosIds(
            Collection<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    /** Neutraliza los comodines del {@code LIKE} dentro del termino. */
    private static String escapar(String termino) {
        StringBuilder escapado = new StringBuilder(termino.length() + 4);
        for (char caracter : termino.toCharArray()) {
            if (caracter == '%' || caracter == '_' || caracter == ESCAPE) {
                escapado.append(ESCAPE);
            }
            escapado.append(caracter);
        }
        return escapado.toString();
    }
}

package com.um.catalogsync.professional.domain.ports.in;

import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchResult;

/**
 * Busca profesionales en la copia local por categoria, nombre y estado.
 *
 * <p>Es la razon de ser de la copia local: §4.1 exige estos filtros y §6.2
 * <b>prohibe</b> consultar al servicio central por cada busqueda. Una
 * implementacion que llamara a la catedra —por REST o leyendo Redis— estaria
 * rompiendo una regla no negociable del proyecto, no optimizando mal.
 *
 * <p>La disponibilidad, que es el cuarto filtro obligatorio, se suma en #16
 * sobre este mismo puerto.
 */
public interface SearchProfessionalsUseCase {

    /**
     * @param criteria filtros y pagina pedidos; los tres filtros son
     *     opcionales y combinables
     * @return la pagina de resultados y la version local con la que se
     *     resolvio. Una pagina vacia es una respuesta valida
     * @throws com.um.catalogsync.sync.application.exception.CatalogNotSynchronizedException
     *     si todavia no hay una version de catalogo aplicada: en ese estado la
     *     copia local no es copia de nada y un listado vacio seria enganoso
     */
    ProfessionalSearchResult search(ProfessionalSearchCriteria criteria);
}

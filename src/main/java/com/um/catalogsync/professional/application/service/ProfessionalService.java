package com.um.catalogsync.professional.application.service;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchResult;
import com.um.catalogsync.professional.domain.ports.in.GetAllProfessionalsUseCase;
import com.um.catalogsync.professional.domain.ports.in.GetProfessionalByIdUseCase;
import com.um.catalogsync.professional.domain.ports.in.SearchProfessionalsUseCase;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade del slice de profesionales.
 *
 * <p>Misma division que en categorias: los puertos devuelven
 * {@code Optional} y aca se decide que significa el vacio.
 *
 * <p>La busqueda (#15) es la operacion que importa del slice y es la que el
 * controlador consume. Con ella aparece la otra mitad del trabajo de una
 * fachada: {@link #search} no traduce ningun vacio a excepcion, porque una
 * pagina sin resultados es una respuesta correcta y convertirla en un 404
 * obligaria a la app KMP a tratar "no hay dermatologos" como un error.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final GetAllProfessionalsUseCase getAllProfessionals;
    private final GetProfessionalByIdUseCase getProfessionalById;
    private final SearchProfessionalsUseCase searchProfessionals;

    public List<Professional> getAll() {
        return getAllProfessionals.getAll();
    }

    /** @throws ProfessionalNotFoundException si el profesional no esta */
    public Professional getById(Long id) {
        return getProfessionalById.getById(id)
                .orElseThrow(() -> new ProfessionalNotFoundException(id));
    }

    public Optional<Professional> findById(Long id) {
        return getProfessionalById.getById(id);
    }

    /**
     * Busca sobre la copia local por categoria, nombre y estado (#15).
     *
     * @return la pagina pedida —posiblemente vacia, que es valido— y la
     *     version de catalogo con la que se resolvio
     * @throws com.um.catalogsync.sync.application.exception.CatalogNotSynchronizedException
     *     si todavia no hay catalogo aplicado
     */
    public ProfessionalSearchResult search(ProfessionalSearchCriteria criteria) {
        return searchProfessionals.search(criteria);
    }
}

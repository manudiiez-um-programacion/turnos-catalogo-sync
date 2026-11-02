package com.um.catalogsync.professional.application.usecases;

import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchResult;
import com.um.catalogsync.professional.domain.ports.in.SearchProfessionalsUseCase;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.sync.application.exception.CatalogNotSynchronizedException;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca profesionales leyendo <b>solo</b> la copia local.
 *
 * <p><b>Sus dos dependencias son de persistencia.</b> No hay aca ningun
 * cliente REST ni nada de Redis, y eso no es un detalle de implementacion:
 * §6.2 prohibe consultar al servicio central en cada busqueda, y es la regla
 * que justifica que este servicio guarde una copia. Hay una prueba que falla
 * si el puerto del snapshot llega a invocarse durante una busqueda, para que
 * la regla quede verificada y no solo escrita.
 *
 * <p><b>Solo lectura y REPEATABLE READ</b>, por el mismo motivo que
 * {@code GetCurrentAgendaUseCaseImpl}: son dos lecturas —la version y la
 * pagina— que tienen que ver la misma copia. Con READ COMMITTED, que es el
 * default de PostgreSQL, cada sentencia toma su propia instantanea, asi que
 * el commit de una sincronizacion puede colarse entre las dos y devolver el
 * numero de una version con las filas de otra. Con REPEATABLE READ la
 * instantanea se toma una sola vez. El costo es nulo: PostgreSQL lo
 * implementa con MVCC, sin bloqueos de lectura, y los errores de
 * serializacion de ese nivel solo afectan a transacciones que escriben.
 *
 * <p><b>Por que la version va primero.</b> Si no hay copia local, cualquier
 * respuesta seria enganosa: una pagina vacia diria "no hay profesionales que
 * cumplan los filtros" cuando lo que no hay es catalogo. Son dos cosas
 * distintas y el cliente tiene que poder distinguirlas —la primera no se
 * arregla reintentando y la segunda si—, asi que el estado sin catalogo se
 * informa como 503 y no como una lista vacia. No contradice el criterio de
 * que una pagina vacia es valida: eso vale cuando hay catalogo y los filtros
 * no matchean nada, que es el caso que {@link ProfessionalPage} documenta.
 */
@Component
@RequiredArgsConstructor
public class SearchProfessionalsUseCaseImpl implements SearchProfessionalsUseCase {

    private final ProfessionalRepository profesionales;
    private final CatalogVersionRepository versiones;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfessionalSearchResult search(ProfessionalSearchCriteria criteria) {
        CatalogVersion version =
                versiones.findCurrent().orElseThrow(CatalogNotSynchronizedException::new);

        return new ProfessionalSearchResult(profesionales.search(criteria), version);
    }
}

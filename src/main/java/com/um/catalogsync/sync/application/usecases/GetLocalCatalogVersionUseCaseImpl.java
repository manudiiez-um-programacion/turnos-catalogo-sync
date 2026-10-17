package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.ports.in.GetLocalCatalogVersionUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lee la version local. Nunca consulta a la catedra: la version que informa
 * es la que este servicio tiene <i>aplicada</i>, que es justamente el dato
 * que hay que comparar contra {@code current-version} de Redis para saber
 * si hay trabajo pendiente (#14).
 */
@Component
@RequiredArgsConstructor
public class GetLocalCatalogVersionUseCaseImpl implements GetLocalCatalogVersionUseCase {

    private final CatalogVersionRepository catalogVersionRepository;

    @Override
    public Optional<CatalogVersion> getCurrent() {
        return catalogVersionRepository.findCurrent();
    }
}

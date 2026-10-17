package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import java.util.Optional;

/** Puerto de entrada: que version de catalogo tiene aplicada este servicio. */
public interface GetLocalCatalogVersionUseCase {

    /**
     * @return la version vigente, o vacio si la base esta recien
     *     inicializada y nunca se aplico un catalogo
     */
    Optional<CatalogVersion> getCurrent();
}

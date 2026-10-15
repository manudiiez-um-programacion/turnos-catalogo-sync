package com.um.catalogsync.sync.application.service;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.ports.in.GetLocalCatalogVersionUseCase;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade de la version de catalogo.
 *
 * <p>El slice {@code sync} va a tener mas de una fachada, y eso es una
 * diferencia con los slices de catalogo que vale aclarar: {@code category},
 * {@code professional} y {@code schedule} modelan una entidad cada uno, asi
 * que les alcanza una. {@code sync} modela varios procesos distintos —la
 * version aplicada, la sincronizacion completa (#13), la incremental (#25),
 * el estado y los errores (#28)— y agruparlos en un unico {@code @Service}
 * daria una clase que crece sin criterio. Esta cubre la version aplicada.
 *
 * <p>No expone como avanzar la version. Es deliberado: avanzarla no es una
 * operacion suelta que alguien pueda invocar, es el ultimo paso de una
 * unidad de trabajo que aplica datos. Quien sincroniza la avanza por el
 * puerto de salida, dentro de su propia transaccion. Si estuviera aca,
 * seria una invitacion a avanzar la version sin haber aplicado nada.
 */
@Service
@RequiredArgsConstructor
public class CatalogVersionService {

    private final GetLocalCatalogVersionUseCase getLocalCatalogVersion;

    /**
     * @return la version vigente, o vacio si la base esta recien
     *     inicializada. Se devuelve el {@code Optional} tal cual, sin
     *     traducirlo a excepcion: a diferencia de "no existe esta
     *     categoria", una base sin version es un estado esperado y util,
     *     y es lo que dispara la sincronizacion completa.
     */
    public Optional<CatalogVersion> getCurrent() {
        return getLocalCatalogVersion.getCurrent();
    }
}

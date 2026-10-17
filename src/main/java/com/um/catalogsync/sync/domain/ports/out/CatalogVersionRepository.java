package com.um.catalogsync.sync.domain.ports.out;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import java.util.Optional;

/**
 * Puerto de salida de la version local.
 *
 * <p>No se parece a los puertos de los slices de catalogo y no deberia: no
 * hay coleccion que listar ni ids que borrar. Hay un unico valor, se lee y
 * se avanza. Por eso solo tiene dos operaciones.
 */
public interface CatalogVersionRepository {

    /**
     * La version local vigente.
     *
     * <p><b>El {@code Optional} vacio es informacion, no un caso borde.</b>
     * Significa que la base esta recien inicializada: las migraciones
     * corrieron pero todavia no se aplico ningun catalogo. Es lo que la
     * deteccion de estado (#14) usa para decidir que hace falta una
     * sincronizacion completa (§6.1), asi que no se traduce a la version 0
     * ni a un valor por defecto. "No tengo ninguna version" y "tengo la
     * version 0" son dos estados distintos y confundirlos haria que una base
     * vacia pareciera sincronizada.
     *
     * @return la version vigente, o vacio si nunca se aplico ninguna
     */
    Optional<CatalogVersion> findCurrent();

    /**
     * Deja asentado que la copia local esta en la version pedida.
     *
     * <p><b>Participa de la transaccion de quien llama</b> ({@code REQUIRED},
     * la propagacion por defecto). Eso es lo que hace cumplir la regla
     * central del proyecto sin necesidad de coordinar nada: si la
     * sincronizacion aplica datos y despues falla, el rollback se lleva
     * tambien este avance. Un {@code REQUIRES_NEW} aca rompería la
     * invariante entera, porque la version quedaria commiteada describiendo
     * datos que nunca se guardaron.
     *
     * <p>Queda una sola fila vigente siempre: ni el llamador ni dos
     * llamadores concurrentes pueden dejar dos versiones anotadas.
     *
     * @param version numero de version de la catedra que corresponde a los
     *     datos que quedaron en la copia local
     * @param source como se llego a esa version
     * @return la version que quedo asentada, con el {@code appliedAt} que se
     *     grabo
     * @throws com.um.catalogsync.sync.application.exception.VersionRegressionException
     *     si la version pedida es anterior a la vigente y el origen no es
     *     {@code SNAPSHOT}
     * @throws com.um.catalogsync.sync.application.exception.ConcurrentVersionAdvanceException
     *     si otra sincronizacion asento la primera version al mismo tiempo
     */
    CatalogVersion advanceTo(long version, CatalogVersionSource source);
}

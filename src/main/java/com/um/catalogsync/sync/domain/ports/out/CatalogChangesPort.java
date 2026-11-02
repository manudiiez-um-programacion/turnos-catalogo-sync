package com.um.catalogsync.sync.domain.ports.out;

import com.um.catalogsync.sync.domain.model.CatalogChanges;
import java.util.Optional;

/**
 * Puerto de salida de la lista de cambios de una version
 * ({@code catedra:sync:changes:{version}}, §14.5).
 *
 * <p><b>Por que es un puerto aparte de {@link PublishedCatalogPort}.</b> Los
 * dos leen del mismo Redis, pero no responden la misma pregunta ni tienen la
 * misma vida. El catalogo publicado responde "como esta esta entidad ahora"
 * y sus claves no tienen TTL: siempre estan. Esto responde "que cambio en la
 * version N" y su disponibilidad depende de la ventana de versiones
 * retenida: la misma lectura que hoy funciona, en una semana puede no tener
 * la clave. Juntarlos haria que un puerto prometiera dos garantias
 * distintas, y quien lo use no sabria cual de los dos {@code Optional.empty()}
 * le toco.
 *
 * <p><b>Que hace falta para aplicar una version.</b> Primero esto, que da
 * los ids afectados; despues {@link PublishedCatalogPort} para el estado
 * vigente de cada uno. En ese orden y no al revés: la lista de cambios es lo
 * unico que dice <i>que</i> mirar, y leer los hashes enteros para comparar
 * seria traerse el catalogo completo en cada version, que es exactamente lo
 * que el camino incremental evita.
 *
 * <p>Solo lectura, como el otro: la ACL de la catedra permite leer
 * {@code catedra:sync:*} y nada mas (§14.1).
 */
public interface CatalogChangesPort {

    /**
     * Los ids que cambiaron en una version, por coleccion.
     *
     * <p><b>Vacio no significa "sin cambios".</b> Significa que la clave no
     * esta, y eso solo puede querer decir dos cosas: la version todavia no
     * se publico, o ya salio de la ventana retenida. La segunda, para una
     * version que hace falta, es una discontinuidad y fuerza el snapshot
     * completo (§18.2). Cual de las dos es se decide comparando con las
     * versiones de {@link PublishedCatalogPort}, y eso es de #26: este
     * puerto no interpreta, informa.
     *
     * <p>Una version publicada que no toco nada devuelve un
     * {@link CatalogChanges} con las tres listas vacias, que se puede
     * aplicar y hace avanzar la version local.
     *
     * @param version la version cuyos cambios se buscan
     * @return los cambios, o vacio si la clave no existe
     * @throws com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException
     *     si la clave existe y su contenido no cumple §14.5
     * @throws com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException
     *     si no se pudo hablar con Redis
     */
    Optional<CatalogChanges> changesFor(long version);
}

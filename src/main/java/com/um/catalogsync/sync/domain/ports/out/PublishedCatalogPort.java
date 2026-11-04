package com.um.catalogsync.sync.domain.ports.out;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import java.util.Optional;

/**
 * Puerto de salida del catalogo que la catedra publica en Redis (§14).
 *
 * <p>Es la segunda fuente de datos del servicio, al lado del snapshot REST.
 * La diferencia no es de tecnologia sino de proposito: el snapshot da el
 * catalogo <b>entero</b> para reconstruir la copia local de cero, y esto da
 * el <b>estado vigente de una entidad</b> para aplicar un cambio puntual.
 * Kafka solo avisa que algo cambio; los datos se leen de aca (§15.3).
 *
 * <p><b>Solo lectura, y a proposito.</b> La ACL de la catedra permite leer
 * {@code catedra:sync:*} y nada mas sobre ese namespace (§14.1), asi que una
 * operacion de escritura no fallaria en revision sino en produccion, con un
 * error de permisos. El puerto no ofrece ninguna: lo que no se puede hacer,
 * no se declara. Tampoco hay nada sobre {@code catedra:holds:*}, que es
 * interno de la catedra y se manipula exclusivamente por REST.
 *
 * <p><b>{@link Optional} en todo, porque la ausencia es un estado
 * legitimo.</b> Que una clave no exista significa que la catedra todavia no
 * publico eso, y eso no es un error: es informacion. Lo que si es error es
 * una clave que existe y trae algo que no se entiende, y eso sale como
 * excepcion. Las dos cosas tienen respuestas distintas —esperar contra
 * revisar el contrato— asi que no se mezclan en el mismo valor de retorno.
 *
 * <p><b>Sin credenciales en la firma.</b> El host, el puerto, el usuario y la
 * clave de Redis llegan en el objeto {@code integration} de la catedra
 * (§14.1) y el adaptador se los pide al slice {@code integration}. Si
 * estuvieran en la firma, la clave tendria que atravesar la capa de
 * aplicacion de {@code sync}, que es exactamente lo que el ADR 0002 evita.
 *
 * <p>Las entidades deshabilitadas siguen en los hashes y son bajas logicas
 * (§14.4): este puerto las devuelve como vienen, con su {@code enabled} en
 * {@code false}. Filtrarlas aca esconderia la baja en lugar de aplicarla.
 */
public interface PublishedCatalogPort {

    /**
     * La ultima version que la catedra publico
     * ({@code catedra:sync:current-version}).
     *
     * @return la version, o vacio si la clave no existe
     * @throws com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException
     *     si el valor existe y no es un numero decimal
     * @throws com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException
     *     si no se pudo hablar con Redis
     */
    Optional<Long> currentVersion();

    /**
     * La version mas antigua desde la que todavia se puede avanzar con
     * cambios incrementales ({@code catedra:sync:oldest-available-version}).
     *
     * <p>Es la clave de la deteccion de discontinuidad: si la version local
     * quedo por debajo, los incrementos que faltan ya no estan y hay que
     * pedir el snapshot completo (§14.2, §18.2).
     *
     * @return la version, o vacio si la clave no existe
     */
    Optional<Long> oldestAvailableVersion();

    /**
     * La metadata del catalogo publicado ({@code catedra:sync:metadata}).
     *
     * @return la metadata, o vacio si el hash no existe
     * @throws com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException
     *     si el hash existe y le falta algun campo de §14.3
     */
    Optional<PublishedCatalogMetadata> metadata();

    /**
     * El estado vigente de una categoria
     * ({@code catedra:sync:professional-categories}, field = id).
     *
     * @param id el id de la catedra
     * @return la categoria, o vacio si ese field no esta en el hash
     */
    Optional<ProfessionalCategory> findCategory(long id);

    /**
     * El estado vigente de un profesional
     * ({@code catedra:sync:professionals}, field = id).
     */
    Optional<Professional> findProfessional(long id);

    /**
     * El estado vigente de un horario semanal
     * ({@code catedra:sync:weekly-schedules}, field = id).
     */
    Optional<WeeklySchedule> findSchedule(long id);
}

package com.um.catalogsync.sync.domain.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Que cambio en una version del catalogo, segun
 * {@code catedra:sync:changes:{version}} (§14.5).
 *
 * <p><b>Son ids, no entidades.</b> Es la diferencia de fondo con
 * {@link CatalogSnapshot}: el snapshot trae el catalogo entero y con esto
 * alcanza para reconstruir la copia local, mientras que esto solo dice
 * <i>a quienes</i> hay que ir a buscar. El estado vigente de cada id se lee
 * despues de los hashes de §14.4, por el puerto del catalogo publicado. Por
 * eso los campos son {@code List<Long>} y no listas de modelos: poner
 * entidades acá seria prometer un dato que la clave no tiene.
 *
 * <p><b>Una version sin cambios existe y es valida.</b> Las tres listas
 * vacias describen una version que la catedra publico sin tocar nada, y
 * aplicarla hace avanzar la version local igual que cualquier otra (§14.5).
 * Eso es distinto de que la clave no exista, que es una discontinuidad y
 * fuerza snapshot (#26): el puerto devuelve {@code Optional.empty()} para la
 * segunda y un objeto con {@link #isEmpty()} en {@code true} para la
 * primera. Las dos cosas no pueden colapsar en el mismo valor porque las
 * respuestas son opuestas —avanzar contra reconstruir—.
 *
 * <p><b>Las listas nunca son {@code null}</b> cuando el objeto lo construye
 * el mapper: una coleccion ausente en el JSON es un incumplimiento del
 * contrato y falla antes de llegar acá. Tomarla como vacia haria que un
 * cambio de nombre en el contrato borrara cambios en silencio y dejara la
 * version local avanzando sobre una copia incompleta, que es justamente el
 * estado que #26 no podria detectar despues.
 *
 * <p>Los ids se conservan tal como vinieron, sin ordenar ni deduplicar: el
 * orden dentro de una version no significa nada —son el conjunto de
 * afectados— y aplicar dos veces el mismo id es idempotente, porque lo que
 * se aplica es el estado vigente y no un delta.
 *
 * <p>POJO puro, como el resto de los modelos: sin JPA, sin validacion, sin
 * Jackson. No se persiste: la copia local guarda el resultado de aplicar los
 * cambios y la version alcanzada, no la lista de que cambio.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogChanges {

    /**
     * La version que estos cambios describen.
     *
     * <p>Es el numero que la copia local pasa a tener <b>solo</b> cuando los
     * tres grupos quedaron aplicados (§14.5, #25). Viene repetido en la
     * clave y dentro del JSON, y el mapper exige que coincidan.
     */
    private long version;

    /** Ids de {@code catedra:sync:professional-categories} que cambiaron. */
    private List<Long> categoryIds;

    /** Ids de {@code catedra:sync:professionals} que cambiaron. */
    private List<Long> professionalIds;

    /** Ids de {@code catedra:sync:weekly-schedules} que cambiaron. */
    private List<Long> scheduleIds;

    /**
     * Si la version no toco ninguna entidad.
     *
     * <p>Vive en el modelo y no en quien lo consulta porque la pregunta se
     * repite: #25 la necesita para saltearse el trabajo de leer entidades y
     * #28 para informar que la version se aplico sin cambios. Escrita en
     * cada lado, serian tres condiciones de tres listas repetidas.
     */
    public boolean isEmpty() {
        return categoryIds.isEmpty() && professionalIds.isEmpty() && scheduleIds.isEmpty();
    }

    /** Cuantas entidades hay que ir a leer para aplicar esta version. */
    public int totalIds() {
        return categoryIds.size() + professionalIds.size() + scheduleIds.size();
    }
}

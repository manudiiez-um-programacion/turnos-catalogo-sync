package com.um.catalogsync.sync.domain.model;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una version incremental lista para aplicar: el estado vigente de lo que
 * cambio, ya leido de Redis.
 *
 * <p><b>Por que existe en lugar de aplicar {@link CatalogChanges} directo.</b>
 * {@code CatalogChanges} trae ids y aplicar necesita entidades, asi que entre
 * las dos cosas hay un paso de lectura: un {@code HGET} por id contra el
 * Redis de la catedra (§14.5). Ese paso <b>no puede ocurrir dentro de la
 * transaccion</b> —son cientos de milisegundos por lectura sobre ZeroTier
 * (D6), y mantendria los locks de las tres tablas tomados todo ese tiempo—,
 * asi que la lectura pasa antes y su resultado viaja en este objeto. Es la
 * misma separacion que ya existe entre traer el snapshot y aplicarlo.
 *
 * <p><b>Los ids que ya no estan en los hashes son bajas.</b> Un id que figura
 * en los cambios y no aparece en su hash significa que el estado vigente de
 * esa entidad es "no existe", y la copia local tiene que reflejarlo. No es un
 * caso anomalo: al ponerse al dia varias versiones de corrido, una entidad
 * que la version 7 modifico puede haber sido eliminada en la 8, y el hash
 * —que guarda el estado actual, no el historico— ya no la tiene.
 *
 * <p>Ojo con no confundirlo con la baja logica, que es lo habitual: una
 * entidad deshabilitada <b>permanece</b> en el hash con {@code enabled} en
 * {@code false} (§14.4), asi que llega en las listas de entidades y se aplica
 * como cualquier otra modificacion. Solo la ausencia del hash es una baja de
 * verdad.
 *
 * <p>POJO puro, como el resto de los modelos. No se persiste: es el insumo de
 * una transaccion, no un estado.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogVersionDelta {

    /** La version que este delta deja aplicada. */
    private long version;

    /** Estado vigente de las categorias que cambiaron. */
    private List<ProfessionalCategory> categories;

    /** Estado vigente de los profesionales que cambiaron. */
    private List<Professional> professionals;

    /** Estado vigente de los horarios que cambiaron. */
    private List<WeeklySchedule> schedules;

    /** Categorias que ya no estan en el hash: se borran de la copia local. */
    private List<Long> removedCategoryIds;

    /** Profesionales que ya no estan en el hash. */
    private List<Long> removedProfessionalIds;

    /** Horarios que ya no estan en el hash. */
    private List<Long> removedScheduleIds;

    /**
     * Si la version no toco nada.
     *
     * <p>Es un estado valido y no un error: una version con los tres arrays
     * de cambios vacios se aplica y hace avanzar la version local (§14.5).
     * Tambien puede llegar acá un delta vacio porque los ids que cambiaron
     * no existian ni existen, que es el mismo resultado.
     */
    public boolean isEmpty() {
        return categories.isEmpty() && professionals.isEmpty() && schedules.isEmpty()
                && removedCategoryIds.isEmpty() && removedProfessionalIds.isEmpty()
                && removedScheduleIds.isEmpty();
    }

    /** Cuantas entidades se escriben. Para el log. */
    public int upserts() {
        return categories.size() + professionals.size() + schedules.size();
    }

    /** Cuantas entidades se borran. Para el log. */
    public int removals() {
        return removedCategoryIds.size() + removedProfessionalIds.size()
                + removedScheduleIds.size();
    }
}

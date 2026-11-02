package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * La unidad de trabajo de la sincronizacion completa.
 *
 * <p>Esta clase es el corazon del proyecto, asi que vale explicar las dos
 * decisiones que la sostienen.
 *
 * <p><b>Uno: la transaccion vive acá, en la capa de aplicacion.</b> Es el
 * unico lugar donde puede estar. La regla no negociable —"la version local
 * avanza solo cuando la unidad de trabajo termino correctamente"— no es una
 * propiedad de un adaptador sino de la operacion completa: reemplazar tres
 * colecciones y despues avanzar la version. Los cuatro adaptadores declaran
 * {@code REQUIRED}, la propagacion por defecto, asi que se suman a <i>esta</i>
 * transaccion en lugar de abrir una propia, y el rollback se lleva las
 * cuatro escrituras juntas. Poner la frontera en un adaptador escondería la
 * regla mas importante del servicio dentro de un detalle reemplazable.
 *
 * <p>La prueba de que funciona no es esta anotacion sino el test que fuerza
 * un fallo en el ultimo paso y verifica que la copia anterior y la version
 * quedaron intactas.
 *
 * <p><b>Dos: el snapshot llega por parametro, ya traido.</b> Este caso de
 * uso no conoce {@code CatalogSnapshotPort}. No es una omision: la llamada
 * REST a la catedra tarda cientos de milisegundos por ZeroTier (D6) y,
 * si ocurriera adentro, mantendria abierta la transaccion —y los locks de
 * las tres tablas— durante todo ese tiempo. Quien trae el snapshot es
 * {@link RunFullSyncUseCaseImpl}, fuera de la transaccion.
 *
 * <p><b>Lo que esta clase no hace: filtrar.</b> Las entidades
 * deshabilitadas del snapshot se guardan como vinieron. Una entidad
 * deshabilitada es una baja logica que la copia local tiene que conservar
 * (§14.4), no una fila que se descarte: si se filtraran, la busqueda por
 * {@code enabled} no tendria nada que filtrar y un profesional que la
 * catedra deshabilito desapareceria en lugar de aparecer deshabilitado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyCatalogSnapshotUseCaseImpl implements ApplyCatalogSnapshotUseCase {

    private final CategoryRepository categories;
    private final ProfessionalRepository professionals;
    private final WeeklyScheduleRepository schedules;
    private final CatalogVersionRepository versions;

    /**
     * Cinco pasos, y el orden no es negociable.
     *
     * <p>Las FK del catalogo son {@code RESTRICT} en los dos sentidos
     * (categoria ← profesional ← horario), asi que <b>se borra de hijo a
     * padre y se escribe de padre a hijo</b>. De ahi salen los cinco pasos:
     *
     * <ol>
     *   <li>vaciar horarios — si no, no se pueden borrar los profesionales;</li>
     *   <li>vaciar profesionales — si no, no se pueden borrar las categorias;</li>
     *   <li>reemplazar categorias — ya se puede: nadie las referencia;</li>
     *   <li>escribir profesionales — las categorias nuevas ya estan;</li>
     *   <li>escribir horarios — los profesionales nuevos ya estan.</li>
     * </ol>
     *
     * <p>Los dos primeros pasos son {@code replaceAll} con una coleccion
     * vacia, que es exactamente "vaciar" y no necesita una operacion nueva
     * en los puertos: cada uno de los tres ya documenta que el orden entre
     * slices lo decide quien orquesta, y este es ese lugar. Los pasos 4 y 5
     * vuelven a borrar antes de insertar, sobre tablas que ya quedaron
     * vacias: es un {@code DELETE} que no toca ninguna fila, el precio de
     * componer con la operacion que los puertos ya ofrecen en lugar de
     * sumarles un {@code deleteAll} que solo usaria esta clase.
     *
     * <p>Un atajo que se descarto: hacer las FK {@code DEFERRABLE INITIALLY
     * DEFERRED} volveria el orden irrelevante dentro de la transaccion. No
     * sirve —el orden sigue siendo necesario para los borrados— y moveria
     * los errores al COMMIT, donde ya no se pueden atribuir a una fila. La
     * discusion completa esta en {@code UnknownCategoryException}.
     *
     * <p><b>La version se avanza al final y por eso.</b> Es el ultimo
     * {@code INSERT} de la transaccion. Si cualquiera de los cinco pasos
     * falla, este no se ejecuta; y si fallara el commit, tampoco quedaria.
     * El origen es {@link CatalogVersionSource#SNAPSHOT}, el unico que puede
     * retroceder el numero: la version tiene que describir lo que la copia
     * local <i>tiene</i>, y despues de reemplazarla entera eso es la
     * {@code snapshotVersion} que vino con esos datos, sea mayor o menor que
     * la anterior (§18.2).
     */
    @Override
    @Transactional
    public CatalogVersion apply(CatalogSnapshot snapshot) {
        log.debug("Aplicando el snapshot {}: {} categorias, {} profesionales, {} horarios",
                snapshot.getSnapshotVersion(),
                snapshot.getCategories().size(),
                snapshot.getProfessionals().size(),
                snapshot.getSchedules().size());

        // Borrar de hijo a padre.
        schedules.replaceAll(List.of());
        professionals.replaceAll(List.of());

        // Escribir de padre a hijo.
        categories.replaceAll(snapshot.getCategories());
        professionals.replaceAll(snapshot.getProfessionals());
        schedules.replaceAll(snapshot.getSchedules());

        CatalogVersion aplicada =
                versions.advanceTo(snapshot.getSnapshotVersion(), CatalogVersionSource.SNAPSHOT);

        log.info("Snapshot {} aplicado: la version local quedo en {}",
                snapshot.getSnapshotVersion(), aplicada.getVersion());
        return aplicada;
    }
}

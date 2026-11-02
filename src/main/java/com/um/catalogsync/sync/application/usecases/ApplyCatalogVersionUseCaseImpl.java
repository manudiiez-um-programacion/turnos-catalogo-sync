package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionDelta;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogVersionUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * La unidad de trabajo de una version incremental.
 *
 * <p>Es la hermana de {@link ApplyCatalogSnapshotUseCaseImpl} y comparte con
 * ella las dos decisiones de fondo: la transaccion vive acá, en la capa de
 * aplicacion, porque la regla "la version local avanza solo cuando la unidad
 * de trabajo termino completa" es una propiedad de la operacion entera y no
 * de un adaptador; y los datos llegan por parametro ya leidos, porque leerlos
 * adentro mantendria los locks tomados durante las llamadas de red.
 *
 * <p><b>En que se diferencia de aplicar un snapshot.</b> El snapshot
 * reemplaza las tres colecciones enteras; esto toca solo lo que cambio. De
 * ahi dos consecuencias. La primera es que el origen de la version es
 * {@link CatalogVersionSource#INCREMENTAL} y no {@code SNAPSHOT}, y eso
 * importa porque solo el snapshot puede retroceder el numero: una version
 * incremental que retrocediera estaria afirmando un estado que la copia local
 * no tiene. La segunda es que hay borrados puntuales, que el snapshot no
 * necesita porque vacia todo antes de escribir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyCatalogVersionUseCaseImpl implements ApplyCatalogVersionUseCase {

    private final CategoryRepository categories;
    private final ProfessionalRepository professionals;
    private final WeeklyScheduleRepository schedules;
    private final CatalogVersionRepository versions;

    /**
     * Siete pasos, y el orden lo imponen las FK.
     *
     * <p>Las FK del catalogo son {@code RESTRICT} en los dos sentidos
     * (categoria ← profesional ← horario), asi que vale la misma regla que en
     * el snapshot: <b>se borra de hijo a padre y se escribe de padre a
     * hijo</b>. Y los borrados van <b>antes</b> de las escrituras, porque una
     * version puede eliminar un profesional y crear otro en la misma
     * categoria: si se escribiera primero, el borrado del viejo seguiria
     * siendo valido, pero al reves no —borrar una categoria que el profesional
     * nuevo acaba de referenciar fallaria—.
     *
     * <ol>
     *   <li>borrar horarios que ya no estan;</li>
     *   <li>borrar profesionales que ya no estan — sus horarios ya se fueron;</li>
     *   <li>borrar categorias que ya no estan — sus profesionales ya se fueron;</li>
     *   <li>escribir categorias — nadie las referencia todavia;</li>
     *   <li>escribir profesionales — sus categorias ya estan;</li>
     *   <li>escribir horarios — sus profesionales ya estan;</li>
     *   <li>avanzar la version local.</li>
     * </ol>
     *
     * <p>El paso 4 antes del 5 es el que resuelve el caso del profesional
     * cuya categoria llega en la misma version, que es la decision que ya se
     * habia tomado en #7: sin ese orden, el {@code upsert} del profesional
     * fallaria por una FK contra una categoria que todavia no existe.
     *
     * <p><b>La version se avanza al final, y por eso.</b> Es la ultima
     * escritura de la transaccion: si cualquiera de los seis pasos falla,
     * este no se ejecuta, y si fallara el commit, tampoco quedaria. La copia
     * local nunca afirma una version que no tiene.
     *
     * <p><b>Una version vacia tambien avanza.</b> §14.5 lo dice: una version
     * con los tres arrays vacios es valida. Saltearla dejaria la version
     * local atras del origen para siempre, y el proximo recorrido volveria a
     * encontrarse con la misma clave vacia.
     */
    @Override
    @Transactional
    public CatalogVersion apply(CatalogVersionDelta delta) {
        log.debug("Aplicando la version {}: {} escrituras, {} bajas",
                delta.getVersion(), delta.upserts(), delta.removals());

        // Borrar de hijo a padre, y antes de escribir.
        schedules.deleteByIdIn(delta.getRemovedScheduleIds());
        professionals.deleteByIdIn(delta.getRemovedProfessionalIds());
        categories.deleteByIdIn(delta.getRemovedCategoryIds());

        // Escribir de padre a hijo.
        delta.getCategories().forEach(categories::upsert);
        delta.getProfessionals().forEach(professionals::upsert);
        delta.getSchedules().forEach(schedules::upsert);

        CatalogVersion aplicada =
                versions.advanceTo(delta.getVersion(), CatalogVersionSource.INCREMENTAL);

        log.info("Version {} aplicada: la version local quedo en {}",
                delta.getVersion(), aplicada.getVersion());
        return aplicada;
    }
}

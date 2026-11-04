package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.domain.model.CatalogChanges;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionDelta;
import com.um.catalogsync.sync.domain.model.IncrementalSyncOutcome;
import com.um.catalogsync.sync.domain.model.IncrementalSyncResult;
import com.um.catalogsync.sync.domain.ports.in.ApplyCatalogVersionUseCase;
import com.um.catalogsync.sync.domain.ports.in.ApplyIncrementalChangesUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogChangesPort;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.PublishedCatalogPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * El recorrido: una version por vez, en orden, sin saltear ninguna.
 *
 * <p><b>Esta clase no tiene transaccion y es a proposito.</b> La transaccion
 * es por version y la abre {@link ApplyCatalogVersionUseCase}, que es otro
 * bean. Si estuviera acá, las diez versiones de un atraso terminarian en una
 * sola transaccion: una falla en la septima tiraria tambien las seis que ya
 * habian terminado bien, y la version local volveria al principio. Separadas,
 * cada version es su propia unidad y el atraso se recupera de a pedazos.
 *
 * <p><b>Las lecturas de Redis pasan fuera de la transaccion.</b> Por cada
 * version hay una lectura de la clave de cambios y un {@code HGET} por id
 * afectado, y cada una cuesta cientos de milisegundos sobre ZeroTier (D6).
 * Hacerlas dentro mantendria los locks de las tres tablas tomados toda la
 * lectura. Asi que el ciclo es: leer, resolver el delta, y recien entonces
 * abrir la transaccion.
 *
 * <p><b>Donde se detiene.</b> En la primera version cuya clave de cambios no
 * esta. No se saltea: cada version describe una diferencia contra la
 * anterior, asi que un salto deja un hueco que nada posterior corrige. Lo
 * informa en el resultado y deja que #26 decida pedir el snapshot (§18.2).
 *
 * <p><b>Que pasa si una version falla por otra cosa</b> —Redis que se corta,
 * la base que no responde, un dato que no cumple el contrato—: la excepcion
 * se propaga. Las versiones anteriores ya estan commiteadas y la version
 * local quedo en la ultima completa, que es el estado correcto desde el cual
 * reintentar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyIncrementalChangesUseCaseImpl implements ApplyIncrementalChangesUseCase {

    private final CatalogVersionRepository versions;
    private final CatalogChangesPort changes;
    private final PublishedCatalogPort publishedCatalog;
    private final ApplyCatalogVersionUseCase applyVersion;

    @Override
    public IncrementalSyncResult applyUpTo(long targetVersion) {
        Optional<CatalogVersion> local = versions.findCurrent();
        if (local.isEmpty()) {
            // Base recien inicializada: los cambios describen diferencias
            // contra un estado que no existe. No es un error, es que este
            // camino no aplica (§6.1).
            log.debug("No hay version local: el camino incremental no aplica");
            return new IncrementalSyncResult(
                    null, null, targetVersion, 0, IncrementalSyncOutcome.NO_LOCAL_VERSION);
        }

        long desde = local.get().getVersion();
        if (targetVersion <= desde) {
            // Nunca se retrocede. Una version local mas adelantada que el
            // destino puede venir de un snapshot recien aplicado mientras una
            // notificacion vieja esperaba su turno.
            log.debug("La version local {} ya cubre el destino {}", desde, targetVersion);
            return new IncrementalSyncResult(
                    desde, desde, targetVersion, 0, IncrementalSyncOutcome.UP_TO_DATE);
        }

        log.info("Poniendose al dia de la version {} a la {}", desde, targetVersion);
        long aplicada = desde;
        int cuantas = 0;

        for (long version = desde + 1; version <= targetVersion; version++) {
            Optional<CatalogChanges> cambios = changes.changesFor(version);
            if (cambios.isEmpty()) {
                log.warn("No esta la clave de cambios de la version {}: el recorrido se"
                                + " detiene con la copia local en la version {}",
                        version, aplicada);
                return new IncrementalSyncResult(desde, aplicada, targetVersion, cuantas,
                        IncrementalSyncOutcome.MISSING_VERSION);
            }

            // Leer primero, aplicar despues: lo de arriba es red y lo de
            // abajo es una transaccion, y no pueden solaparse.
            aplicada = applyVersion.apply(resolver(cambios.get())).getVersion();
            cuantas++;
        }

        log.info("Al dia en la version {}: se aplicaron {} versiones", aplicada, cuantas);
        return new IncrementalSyncResult(
                desde, aplicada, targetVersion, cuantas, IncrementalSyncOutcome.CAUGHT_UP);
    }

    /**
     * Cambia los ids por el estado vigente de cada entidad (§14.5).
     *
     * <p>Los ids que no aparecen en su hash quedan como bajas. No es un caso
     * anomalo: el hash guarda el estado actual, no el historico, asi que al
     * ponerse al dia varias versiones de corrido una entidad que la version 7
     * modifico puede haber sido eliminada en la 8. La baja <i>logica</i> es
     * otra cosa y llega como entidad con {@code enabled} en {@code false}
     * (§14.4).
     */
    private CatalogVersionDelta resolver(CatalogChanges cambios) {
        var categorias = new Resueltos<ProfessionalCategory>();
        categorias.resolver(cambios.getCategoryIds(), publishedCatalog::findCategory);

        var profesionales = new Resueltos<Professional>();
        profesionales.resolver(cambios.getProfessionalIds(), publishedCatalog::findProfessional);

        var horarios = new Resueltos<WeeklySchedule>();
        horarios.resolver(cambios.getScheduleIds(), publishedCatalog::findSchedule);

        return CatalogVersionDelta.builder()
                .version(cambios.getVersion())
                .categories(categorias.presentes)
                .professionals(profesionales.presentes)
                .schedules(horarios.presentes)
                .removedCategoryIds(categorias.ausentes)
                .removedProfessionalIds(profesionales.ausentes)
                .removedScheduleIds(horarios.ausentes)
                .build();
    }

    /**
     * El resultado de leer una coleccion de ids: los que estaban y los que
     * no.
     *
     * <p>Es una clase y no dos listas sueltas porque el reparto es el mismo
     * para las tres colecciones, y escribirlo tres veces seria tres lugares
     * donde confundir una lista con la otra.
     */
    private static final class Resueltos<T> {

        private final List<T> presentes = new ArrayList<>();
        private final List<Long> ausentes = new ArrayList<>();

        private void resolver(List<Long> ids, LongFunction<Optional<T>> leer) {
            for (Long id : ids) {
                leer.apply(id).ifPresentOrElse(presentes::add, () -> ausentes.add(id));
            }
        }
    }
}

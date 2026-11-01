package com.um.catalogsync.sync.infrastructure.mapper;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.TimeOfDay;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.infrastructure.dto.ProfessionalCategoryDto;
import com.um.catalogsync.sync.infrastructure.dto.ProfessionalDto;
import com.um.catalogsync.sync.infrastructure.dto.WeeklyScheduleDto;
import java.util.function.Function;

/**
 * Traduce una entidad del catalogo de la catedra al modelo de dominio.
 *
 * <p><b>Por que existe separada de los dos mappers que la usan.</b> Las tres
 * entidades viajan con la <b>misma forma</b> por los dos caminos del
 * contrato: dentro del snapshot REST (§7) y como valor de los hashes de
 * Redis (§14.4). Son literalmente los mismos campos con los mismos nombres,
 * asi que tener la traduccion dos veces garantizaria que algun dia se
 * arreglen distinto: alguien agrega un campo nuevo en un lado y el otro
 * camino lo ignora en silencio.
 *
 * <p><b>Que no decide esta clase.</b> Dos cosas, a proposito:
 *
 * <ul>
 *   <li><b>Que excepcion se lanza.</b> Entra por el constructor como una
 *       fabrica de mensajes. Un campo faltante en el snapshot y un campo
 *       faltante en un hash de Redis son dos incumplimientos de contratos
 *       distintos —§7 y §14.4—, se diagnostican distinto y el mensaje de
 *       cada uno nombra su seccion. Lo comun es <i>que</i> se exige; lo
 *       propio es <i>como se cuenta</i>.
 *   <li><b>La forma del contenedor.</b> Las colecciones del snapshot y las
 *       claves de Redis se validan en su propio mapper: uno distingue
 *       "coleccion ausente" de "coleccion vacia", el otro "clave ausente" de
 *       "valor invalido". Aca solo se traduce una entidad.
 * </ul>
 *
 * <p>Es una clase comun y no un {@code @Component}: no tiene estado ni
 * dependencias del contexto, y cada mapper necesita la suya porque cada uno
 * trae su propia fabrica de excepciones.
 */
public class CatalogEntityMapper {

    /**
     * Como contar que falta algo. Recibe el detalle —{@code falta el campo
     * "professionals[15].enabled"}— y devuelve la excepcion del contrato que
     * se estaba leyendo.
     */
    private final Function<String, RuntimeException> alFaltarUnDato;

    public CatalogEntityMapper(Function<String, RuntimeException> alFaltarUnDato) {
        this.alFaltarUnDato = alFaltarUnDato;
    }

    /**
     * @param dto la categoria tal como vino en el JSON
     * @param donde el contexto para la ruta del error: el nombre de la
     *     coleccion del snapshot o la clave de Redis de la que salio
     */
    public ProfessionalCategory toDomain(ProfessionalCategoryDto dto, String donde) {
        Long id = exigir(dto.id(), donde + ".id");
        String ruta = donde + "[" + id + "]";
        return ProfessionalCategory.builder()
                .id(id)
                .name(exigir(dto.name(), ruta + ".name"))
                // El unico campo que puede faltar: es el unico que la tabla
                // local no declara NOT NULL.
                .description(dto.description())
                .enabled(exigir(dto.enabled(), ruta + ".enabled"))
                .createdAt(exigir(dto.createdAt(), ruta + ".createdAt"))
                .updatedAt(exigir(dto.updatedAt(), ruta + ".updatedAt"))
                .build();
    }

    public Professional toDomain(ProfessionalDto dto, String donde) {
        Long id = exigir(dto.id(), donde + ".id");
        String ruta = donde + "[" + id + "]";
        return Professional.builder()
                .id(id)
                .categoryId(exigir(dto.categoryId(), ruta + ".categoryId"))
                .firstName(exigir(dto.firstName(), ruta + ".firstName"))
                .lastName(exigir(dto.lastName(), ruta + ".lastName"))
                .enabled(exigir(dto.enabled(), ruta + ".enabled"))
                .createdAt(exigir(dto.createdAt(), ruta + ".createdAt"))
                .updatedAt(exigir(dto.updatedAt(), ruta + ".updatedAt"))
                .build();
    }

    public WeeklySchedule toDomain(WeeklyScheduleDto dto, String donde) {
        Long id = exigir(dto.id(), donde + ".id");
        String ruta = donde + "[" + id + "]";
        return WeeklySchedule.builder()
                .id(id)
                .professionalId(exigir(dto.professionalId(), ruta + ".professionalId"))
                // Las tres traducciones del dominio: el dia y las dos horas.
                // Si alguna falla, falla con su propia excepcion, que dice
                // que valor llego y no solo que el campo estaba mal.
                .dayOfWeek(DayOfWeek.fromContract(exigir(dto.dayOfWeek(), ruta + ".dayOfWeek")))
                .startTime(TimeOfDay.parse(exigir(dto.startTime(), ruta + ".startTime")))
                .endTime(TimeOfDay.parse(exigir(dto.endTime(), ruta + ".endTime")))
                .slotDurationMinutes(
                        exigir(dto.slotDurationMinutes(), ruta + ".slotDurationMinutes"))
                .enabled(exigir(dto.enabled(), ruta + ".enabled"))
                .createdAt(exigir(dto.createdAt(), ruta + ".createdAt"))
                .updatedAt(exigir(dto.updatedAt(), ruta + ".updatedAt"))
                .build();
    }

    /**
     * Devuelve el valor o falla nombrando su ruta en el JSON.
     *
     * <p>Las cadenas en blanco cuentan como ausentes: un {@code firstName}
     * de {@code ""} pasaria el {@code NOT NULL} de la tabla y dejaria un
     * profesional sin nombre en la busqueda, que es el mismo problema con
     * otra forma.
     *
     * <p>Publico porque los dos mappers lo necesitan para sus propios campos
     * —{@code snapshotVersion}, {@code currentVersion}— y repetir la misma
     * guarda en cada uno es como se desincronizan.
     */
    public <T> T exigir(T valor, String ruta) {
        if (valor == null || (valor instanceof String texto && texto.isBlank())) {
            throw alFaltarUnDato.apply("falta el campo \"" + ruta + "\"");
        }
        return valor;
    }
}

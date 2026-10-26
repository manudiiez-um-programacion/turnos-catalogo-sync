package com.um.catalogsync.sync.infrastructure.client.mapper;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.DayOfWeek;
import com.um.catalogsync.schedule.domain.model.TimeOfDay;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.infrastructure.client.dto.CatalogSnapshotDto;
import com.um.catalogsync.sync.infrastructure.client.dto.ProfessionalCategoryDto;
import com.um.catalogsync.sync.infrastructure.client.dto.ProfessionalDto;
import com.um.catalogsync.sync.infrastructure.client.dto.WeeklyScheduleDto;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * Traduce el snapshot del contrato al modelo de dominio.
 *
 * <p>Es el borde donde los DTOs dejan de existir: nada de
 * {@code infrastructure} sigue hacia adentro. Y es donde se decide que un
 * snapshot sirve o no, porque es el ultimo punto donde todavia se puede
 * nombrar el campo exacto que falta.
 *
 * <p><b>Que se exige y por que.</b> Todos los campos del contrato salvo
 * {@code description}. No es celo: las tres tablas de la copia local
 * declaran {@code NOT NULL} todo lo demas, asi que un valor ausente no se
 * puede guardar. La diferencia esta en donde aparece el error: acá, con el
 * nombre del campo y el id de la catedra; o mas tarde, como una violacion de
 * restriccion en medio de la transaccion de #13, sin decir que fila fue.
 *
 * <p><b>Las colecciones ausentes se rechazan; las vacias se aceptan.</b> Es
 * la distincion mas importante de esta clase. Una lista vacia es un estado
 * legitimo —una catedra recien instalada no tiene profesionales— y tratarla
 * como error bloquearia el arranque. Una lista ausente es otra cosa: si se
 * la tomara como vacia, #13 reemplazaria la copia local borrando todas las
 * filas de esa tabla, en silencio y sin que nadie lo pidiera.
 *
 * <p>Clase {@code @Component} y con guarda de {@code null} en la entrada,
 * como el resto de los mappers del proyecto.
 */
@Component
public class CatalogSnapshotMapper {

    /**
     * @param dto lo que vino en el JSON
     * @return el snapshot de dominio, o {@code null} si no vino cuerpo
     * @throws IncompleteCatalogSnapshotException si falta un campo o una
     *     coleccion que el contrato manda
     * @throws com.um.catalogsync.schedule.domain.model.UnknownDayOfWeekException
     *     si un {@code dayOfWeek} no es uno de los siete de §7
     * @throws com.um.catalogsync.schedule.domain.model.InvalidTimeOfDayException
     *     si una hora no respeta {@code HH:mm} ni {@code HH:mm:ss} (§4)
     */
    public CatalogSnapshot toDomain(CatalogSnapshotDto dto) {
        if (dto == null) {
            return null;
        }
        return CatalogSnapshot.builder()
                .snapshotVersion(exigir(dto.snapshotVersion(), "snapshotVersion"))
                .generatedAt(exigir(dto.generatedAt(), "generatedAt"))
                .categories(mapear(
                        dto.professionalCategories(), "professionalCategories", this::toDomain))
                .professionals(mapear(dto.professionals(), "professionals", this::toDomain))
                .schedules(mapear(dto.weeklySchedules(), "weeklySchedules", this::toDomain))
                .build();
    }

    private ProfessionalCategory toDomain(ProfessionalCategoryDto dto, String ruta) {
        Long id = exigir(dto.id(), ruta + ".id");
        String donde = "professionalCategories[" + id + "]";
        return ProfessionalCategory.builder()
                .id(id)
                .name(exigir(dto.name(), donde + ".name"))
                // El unico campo que puede faltar: es el unico que la tabla
                // local no declara NOT NULL.
                .description(dto.description())
                .enabled(exigir(dto.enabled(), donde + ".enabled"))
                .createdAt(exigir(dto.createdAt(), donde + ".createdAt"))
                .updatedAt(exigir(dto.updatedAt(), donde + ".updatedAt"))
                .build();
    }

    private Professional toDomain(ProfessionalDto dto, String ruta) {
        Long id = exigir(dto.id(), ruta + ".id");
        String donde = "professionals[" + id + "]";
        return Professional.builder()
                .id(id)
                .categoryId(exigir(dto.categoryId(), donde + ".categoryId"))
                .firstName(exigir(dto.firstName(), donde + ".firstName"))
                .lastName(exigir(dto.lastName(), donde + ".lastName"))
                .enabled(exigir(dto.enabled(), donde + ".enabled"))
                .createdAt(exigir(dto.createdAt(), donde + ".createdAt"))
                .updatedAt(exigir(dto.updatedAt(), donde + ".updatedAt"))
                .build();
    }

    private WeeklySchedule toDomain(WeeklyScheduleDto dto, String ruta) {
        Long id = exigir(dto.id(), ruta + ".id");
        String donde = "weeklySchedules[" + id + "]";
        return WeeklySchedule.builder()
                .id(id)
                .professionalId(exigir(dto.professionalId(), donde + ".professionalId"))
                // Las tres traducciones del dominio: el dia y las dos horas.
                // Si alguna falla, falla con su propia excepcion, que dice
                // que valor llego y no solo que el campo estaba mal.
                .dayOfWeek(DayOfWeek.fromContract(exigir(dto.dayOfWeek(), donde + ".dayOfWeek")))
                .startTime(TimeOfDay.parse(exigir(dto.startTime(), donde + ".startTime")))
                .endTime(TimeOfDay.parse(exigir(dto.endTime(), donde + ".endTime")))
                .slotDurationMinutes(
                        exigir(dto.slotDurationMinutes(), donde + ".slotDurationMinutes"))
                .enabled(exigir(dto.enabled(), donde + ".enabled"))
                .createdAt(exigir(dto.createdAt(), donde + ".createdAt"))
                .updatedAt(exigir(dto.updatedAt(), donde + ".updatedAt"))
                .build();
    }

    /**
     * Mapea una coleccion del contrato, exigiendo que haya venido.
     *
     * <p>La lista que devuelve es modificable a proposito: la consume #13,
     * que necesita ordenarla antes de aplicarla —las categorias antes que
     * los profesionales, y los horarios al final, por las claves foraneas—.
     * Devolver {@code List.copyOf} obligaria a copiarla de nuevo alla.
     *
     * @param coleccion la lista del DTO, posiblemente {@code null}
     * @param nombre el nombre del campo en el JSON, para el mensaje de error
     * @param aDominio la traduccion de un elemento, que recibe tambien el
     *     nombre del campo para poder nombrar la ruta completa
     */
    private <D, M> List<M> mapear(
            List<D> coleccion, String nombre, BiFunction<D, String, M> aDominio) {
        if (coleccion == null) {
            throw new IncompleteCatalogSnapshotException(
                    "falta la coleccion \"" + nombre + "\". Una coleccion ausente no es una"
                            + " coleccion vacia: tomarla como vacia borraria la copia local de"
                            + " esas entidades al aplicar el snapshot");
        }
        List<M> mapeados = new ArrayList<>(coleccion.size());
        for (D elemento : coleccion) {
            if (elemento == null) {
                throw new IncompleteCatalogSnapshotException(
                        "la coleccion \"" + nombre + "\" trae un elemento nulo");
            }
            mapeados.add(aDominio.apply(elemento, nombre));
        }
        return mapeados;
    }

    /**
     * Devuelve el valor o falla nombrando su ruta en el JSON.
     *
     * <p>Las cadenas en blanco cuentan como ausentes: un {@code firstName}
     * de {@code ""} pasaria el {@code NOT NULL} de la tabla y dejaria un
     * profesional sin nombre en la busqueda, que es el mismo problema con
     * otra forma.
     */
    private static <T> T exigir(T valor, String ruta) {
        if (valor == null || (valor instanceof String texto && texto.isBlank())) {
            throw new IncompleteCatalogSnapshotException("falta el campo \"" + ruta + "\"");
        }
        return valor;
    }
}

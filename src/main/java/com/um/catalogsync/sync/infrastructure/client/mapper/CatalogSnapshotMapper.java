package com.um.catalogsync.sync.infrastructure.client.mapper;

import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.infrastructure.client.dto.CatalogSnapshotDto;
import com.um.catalogsync.sync.infrastructure.mapper.CatalogEntityMapper;
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
 * <p><b>La traduccion de cada entidad no esta aca</b> sino en
 * {@link CatalogEntityMapper}, porque las tres entidades llegan con la misma
 * forma tambien por Redis (§14.4) y tenerla duplicada seria garantizar que
 * algun dia los dos caminos se arreglen distinto. Lo que queda aca es lo
 * propio del snapshot: sus dos campos de cabecera y la regla de las
 * colecciones.
 *
 * <p>Clase {@code @Component} y con guarda de {@code null} en la entrada,
 * como el resto de los mappers del proyecto.
 */
@Component
public class CatalogSnapshotMapper {

    /**
     * Las entidades, con las fallas contadas como incumplimientos de §7:
     * este mapper lee el snapshot, asi que un campo faltante es un problema
     * del snapshot y el mensaje tiene que decir eso.
     */
    private final CatalogEntityMapper entidades =
            new CatalogEntityMapper(IncompleteCatalogSnapshotException::new);

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
                .snapshotVersion(entidades.exigir(dto.snapshotVersion(), "snapshotVersion"))
                .generatedAt(entidades.exigir(dto.generatedAt(), "generatedAt"))
                .categories(mapear(
                        dto.professionalCategories(),
                        "professionalCategories",
                        entidades::toDomain))
                .professionals(mapear(dto.professionals(), "professionals", entidades::toDomain))
                .schedules(mapear(dto.weeklySchedules(), "weeklySchedules", entidades::toDomain))
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
}

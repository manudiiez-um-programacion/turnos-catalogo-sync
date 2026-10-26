package com.um.catalogsync.sync.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * La respuesta de {@code GET /api/synchronization/snapshot} (§7).
 *
 * <p>Es la frontera: los nombres son los del contrato
 * —{@code professionalCategories}, no {@code categories}— y los tipos
 * admiten lo que el JSON puede traer, incluido no traer nada. El modelo de
 * dominio es el que no tolera huecos; eso lo decide el mapper.
 *
 * <p><b>Por que todo envuelto y las listas sin valor por defecto.</b>
 * {@code snapshotVersion} es {@code Long} y no {@code long}: con el
 * primitivo, un campo ausente se deserializaria como {@code 0}, que es una
 * version valida y la mas peligrosa de todas —diria que la copia local esta
 * al dia en el origen de los tiempos. Igual con las listas: una coleccion
 * ausente llega como {@code null} y no como lista vacia, porque "no vino" y
 * "vino vacia" tienen consecuencias opuestas cuando #13 reemplaza la copia
 * local.
 *
 * <p>{@code generatedAt} si entra como {@link Instant}: §4 fija ISO-8601 en
 * UTC y es exactamente lo que Jackson parsea de forma estandar, asi que un
 * parseo propio solo agregaria una forma mas de equivocarse. Las horas de
 * atencion son el caso contrario y por eso viajan como texto en
 * {@link WeeklyScheduleDto}.
 *
 * <p>{@code ignoreUnknown} porque el contrato puede sumar campos
 * compatibles con v1 y este servicio tiene que seguir funcionando (§4). Va
 * anotado en el DTO en lugar de confiar en la configuracion global de
 * Jackson: el {@code RestClient} de la catedra se arma con los conversores
 * por defecto y no con el {@code ObjectMapper} que configura Spring Boot,
 * asi que la tolerancia tiene que estar escrita donde no dependa de eso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSnapshotDto(
        Long snapshotVersion,
        Instant generatedAt,
        List<ProfessionalCategoryDto> professionalCategories,
        List<ProfessionalDto> professionals,
        List<WeeklyScheduleDto> weeklySchedules) {

    /** Los tamaños, no el contenido: un snapshot entero no va a un log. */
    @Override
    public String toString() {
        return "CatalogSnapshotDto(snapshotVersion=" + snapshotVersion
                + ", generatedAt=" + generatedAt
                + ", professionalCategories=" + tamaño(professionalCategories)
                + ", professionals=" + tamaño(professionals)
                + ", weeklySchedules=" + tamaño(weeklySchedules)
                + ")";
    }

    private static String tamaño(List<?> coleccion) {
        return coleccion == null ? "ausente" : String.valueOf(coleccion.size());
    }
}

package com.um.catalogsync.sync.infrastructure.redis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * El JSON de {@code catedra:sync:changes:{version}} (§14.5).
 *
 * <p>Es la frontera, igual que {@code CatalogSnapshotDto} lo es del camino
 * REST: los nombres son los del contrato —{@code professionalCategories}, no
 * {@code categories}— y los tipos admiten lo que el JSON puede traer,
 * incluido no traer nada. Quien no tolera huecos es el modelo de dominio, y
 * eso lo decide el mapper.
 *
 * <p><b>Por que {@code Long} y no {@code long} en la version.</b> Con el
 * primitivo, un {@code version} ausente se deserializaria como {@code 0} y
 * pasaria el control de coherencia contra la clave solo para
 * {@code changes:0}: en cualquier otra version fallaria, pero por el motivo
 * equivocado y con un mensaje que hablaria de una discrepancia inexistente.
 *
 * <p><b>Por que las listas pueden ser {@code null}.</b> Porque una coleccion
 * ausente en el JSON llega asi, y "no vino" tiene que poder distinguirse de
 * "vino vacia". No son lo mismo: una lista vacia es una version que no toco
 * esa coleccion, y una lista ausente es un contrato que cambio sin avisar.
 * Si el DTO las normalizara a vacio, un cambio de nombre en el contrato
 * descartaria cambios en silencio y la version local avanzaria sobre una
 * copia incompleta.
 *
 * <p><b>Por que los ids son {@code List<Long>} y no entidades.</b> Los
 * arrays contienen ids afectados, no las entidades completas (§14.5). El
 * estado vigente de cada uno se lee de los hashes de §14.4.
 *
 * <p>{@code ignoreUnknown} en los dos niveles porque el contrato puede sumar
 * campos compatibles y este servicio tiene que seguir funcionando (§4). Va
 * anotado acá y no solo en la configuracion global de Jackson para que la
 * tolerancia no dependa de con que {@code ObjectMapper} se lea.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogChangesDto(Long version, Changes changes) {

    /**
     * El objeto {@code changes}, con un array por coleccion.
     *
     * <p>Esta anidado porque el contrato lo anida: aplanarlo acá haria que
     * el DTO dejara de ser el espejo del JSON y que la forma del contrato
     * solo se pudiera leer en el mapper.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Changes(
            List<Long> professionalCategories,
            List<Long> professionals,
            List<Long> weeklySchedules) {
    }
}

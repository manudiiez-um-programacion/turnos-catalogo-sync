package com.um.catalogsync.sync.infrastructure.redis.mapper;

import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.domain.model.CatalogChanges;
import com.um.catalogsync.sync.infrastructure.redis.dto.CatalogChangesDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduce el JSON de {@code catedra:sync:changes:{version}} al modelo de
 * dominio (§14.5).
 *
 * <p>Esta separado de {@link PublishedCatalogMapper} por el mismo motivo que
 * los puertos: aquel traduce entidades y versiones, que son datos
 * permanentes, y este traduce la lista de cambios de una version, que vive
 * dentro de la ventana retenida. Lo unico que comparten es el tipo de error.
 *
 * <p><b>Lo que exige y por que.</b> Tres cosas, y ninguna es pedantería:
 *
 * <ul>
 *   <li><b>Que la version del JSON coincida con la de la clave.</b> §14.5
 *       escribe el mismo numero en los dos lugares. Si difieren, aplicar
 *       esos cambios asentaria en la copia local una version que no es la
 *       que se leyo, y la proxima lectura incremental arrancaria desde el
 *       numero equivocado: un hueco silencioso que #26 ya no podria
 *       detectar, porque la version local se veria consistente.
 *   <li><b>Que los tres arrays esten.</b> Tomar un array ausente como vacio
 *       haria que un cambio de nombre en el contrato —o un bug del lado de
 *       la catedra— descartara cambios sin ningun sintoma y dejara la
 *       version local avanzando sobre datos que nunca se aplicaron. Es la
 *       peor falla posible de este camino: no se ve.
 *   <li><b>Que no haya ids nulos.</b> Un {@code null} dentro del array
 *       reventaria recién en #25, al pedirle al hash el field de un id que
 *       no existe, lejos de la causa.
 * </ul>
 *
 * <p><b>Lo que no exige.</b> No valida que los ids existan en los hashes:
 * eso no se puede saber sin leerlos, y que un id de la lista no tenga
 * entidad vigente es un caso que el contrato permite y que #25 tiene que
 * resolver. Tampoco ordena ni deduplica (ver {@link CatalogChanges}).
 *
 * <p>El {@code ObjectMapper} es el de Jackson 3 que configura Spring Boot,
 * igual que en {@link PublishedCatalogMapper}: una segunda configuracion de
 * Jackson seria una segunda cosa que mantener al dia.
 */
@Component
@RequiredArgsConstructor
public class CatalogChangesMapper {

    static final String CAMPO_CATEGORIAS = "professionalCategories";
    static final String CAMPO_PROFESIONALES = "professionals";
    static final String CAMPO_HORARIOS = "weeklySchedules";

    private final ObjectMapper jackson;

    /**
     * Los cambios de una version, a partir del valor que ya se sabe que
     * existe.
     *
     * @param clave la clave de Redis, para poder nombrarla si falla
     * @param version la version que se pidio, contra la que se compara
     * @param json el contenido de la clave
     */
    public CatalogChanges toChanges(String clave, long version, String json) {
        CatalogChangesDto dto = leer(clave, json);
        exigirLaMismaVersion(clave, version, dto.version());

        CatalogChangesDto.Changes cambios = dto.changes();
        if (cambios == null) {
            throw new MalformedPublishedCatalogException(
                    "a la clave \"" + clave + "\" le falta el objeto \"changes\" (§14.5)");
        }
        return CatalogChanges.builder()
                .version(version)
                .categoryIds(ids(clave, CAMPO_CATEGORIAS, cambios.professionalCategories()))
                .professionalIds(ids(clave, CAMPO_PROFESIONALES, cambios.professionals()))
                .scheduleIds(ids(clave, CAMPO_HORARIOS, cambios.weeklySchedules()))
                .build();
    }

    /**
     * Deserializa el contenido de la clave.
     *
     * <p>El mensaje nombra la clave y nunca el contenido: ubicar el dato del
     * otro lado es lo que hace falta, y una version con muchos cambios es
     * una lista larga que no tiene por que terminar en una traza.
     */
    private CatalogChangesDto leer(String clave, String json) {
        try {
            CatalogChangesDto dto = jackson.readValue(json, CatalogChangesDto.class);
            if (dto == null) {
                // Pasa con el valor literal "null", que es JSON valido.
                throw new MalformedPublishedCatalogException(
                        "el valor de la clave \"" + clave + "\" es nulo");
            }
            return dto;
        } catch (JacksonException noEsJson) {
            throw new MalformedPublishedCatalogException(
                    "el valor de la clave \"" + clave
                            + "\" no es un JSON que se pueda leer (§14.5)",
                    noEsJson);
        }
    }

    private void exigirLaMismaVersion(String clave, long version, Long versionDelJson) {
        if (versionDelJson == null) {
            throw new MalformedPublishedCatalogException(
                    "a la clave \"" + clave + "\" le falta el campo \"version\" (§14.5)");
        }
        if (versionDelJson != version) {
            throw new MalformedPublishedCatalogException(
                    "la clave \"" + clave + "\" trae en el JSON la version " + versionDelJson
                            + ": el sufijo de la clave y el campo \"version\" tienen que ser"
                            + " el mismo dato (§14.5)");
        }
    }

    /**
     * Un array de ids, exigiendo que este y que no traiga nulos.
     *
     * <p>Se copia a una lista propia e inmutable: lo que devuelve Jackson es
     * mutable y queda referenciado por el DTO, y el modelo de dominio no
     * deberia poder cambiar debajo de quien lo esta aplicando.
     */
    private List<Long> ids(String clave, String campo, List<Long> delJson) {
        if (delJson == null) {
            throw new MalformedPublishedCatalogException(
                    "a la clave \"" + clave + "\" le falta el array \"" + campo + "\" (§14.5):"
                            + " una version sin cambios en esa coleccion lo trae vacio, no"
                            + " ausente");
        }
        if (delJson.contains(null)) {
            throw new MalformedPublishedCatalogException(
                    "el array \"" + campo + "\" de la clave \"" + clave + "\" trae un id nulo");
        }
        return List.copyOf(delJson);
    }
}

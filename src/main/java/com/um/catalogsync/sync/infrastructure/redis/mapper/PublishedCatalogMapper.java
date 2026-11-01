package com.um.catalogsync.sync.infrastructure.redis.mapper;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.infrastructure.dto.ProfessionalCategoryDto;
import com.um.catalogsync.sync.infrastructure.dto.ProfessionalDto;
import com.um.catalogsync.sync.infrastructure.dto.WeeklyScheduleDto;
import com.um.catalogsync.sync.infrastructure.mapper.CatalogEntityMapper;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduce lo que viene de Redis al modelo de dominio.
 *
 * <p>Es el borde del camino de Redis, igual que {@code CatalogSnapshotMapper}
 * lo es del camino REST: los strings y los hashes de strings dejan de existir
 * aca y lo que sigue hacia adentro es dominio.
 *
 * <p><b>Lo que esta clase aporta sobre el otro mapper.</b> En Redis todo es
 * texto: una version es la cadena {@code "7"} y una entidad es un JSON
 * guardado como valor de un field. Asi que hay dos traducciones que el
 * camino REST no necesita —texto a numero y texto a JSON— y las dos pueden
 * fallar con datos que llegaron perfectamente bien por la red. Eso es lo que
 * se convierte en {@link MalformedPublishedCatalogException}: no es un
 * problema de conexion, es un contrato incumplido.
 *
 * <p><b>Que el field y el id del JSON coincidan se verifica.</b> §14.4 dice
 * que el field es el id, asi que son el mismo dato escrito dos veces. Si
 * difieren, la lectura por id devolveria una entidad que no es la pedida y
 * #25 la aplicaria con el id equivocado: dos filas mezcladas en la copia
 * local, sin ningun error visible. Es el tipo de incoherencia que no vale la
 * pena tolerar.
 *
 * <p>El {@code ObjectMapper} es el de Jackson 3 que configura Spring Boot, el
 * mismo que usa el resto del proyecto: tiene el soporte de {@code java.time}
 * ya armado, que es lo que hace falta para los {@code createdAt}. Un
 * {@code ObjectMapper} propio seria una segunda configuracion de Jackson que
 * habria que mantener al dia.
 */
@Component
@RequiredArgsConstructor
public class PublishedCatalogMapper {

    static final String CAMPO_VERSION_ACTUAL = "currentVersion";
    static final String CAMPO_VERSION_MAS_ANTIGUA = "oldestAvailableVersion";
    static final String CAMPO_PUBLICADO = "publishedAt";

    private final ObjectMapper jackson;

    /**
     * Las entidades, con las fallas contadas como incumplimientos de §14:
     * este mapper lee Redis, y un campo faltante tiene que mandar a mirar la
     * clave y el field, no el snapshot.
     */
    private final CatalogEntityMapper entidades =
            new CatalogEntityMapper(MalformedPublishedCatalogException::new);

    /**
     * Una de las dos claves de version (§14.2), que son strings decimales.
     *
     * @param clave la clave de Redis, para poder nombrarla si falla
     * @param valor el contenido, que ya se sabe que existe
     */
    public long toVersion(String clave, String valor) {
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException noEsUnNumero) {
            throw new MalformedPublishedCatalogException(
                    "la clave \"" + clave + "\" tiene que ser un numero decimal y trae \""
                            + valor + "\"",
                    noEsUnNumero);
        }
    }

    /**
     * El hash de metadata (§14.3), que ya se sabe que no esta vacio.
     *
     * <p>Los tres campos son obligatorios. Un hash al que le falta uno no es
     * "metadata parcial": es un hash que no se puede usar para decidir, y
     * tomar el que falta como cero diria que la ventana de versiones empieza
     * en el origen de los tiempos.
     */
    public PublishedCatalogMetadata toMetadata(Map<String, String> hash) {
        return PublishedCatalogMetadata.builder()
                .currentVersion(versionDelHash(hash, CAMPO_VERSION_ACTUAL))
                .oldestAvailableVersion(versionDelHash(hash, CAMPO_VERSION_MAS_ANTIGUA))
                .publishedAt(instanteDelHash(hash, CAMPO_PUBLICADO))
                .build();
    }

    /** Una categoria del hash {@code catedra:sync:professional-categories}. */
    public ProfessionalCategory toCategory(String clave, long id, String json) {
        ProfessionalCategoryDto dto = leer(json, ProfessionalCategoryDto.class, clave, id);
        exigirElMismoId(clave, id, dto.id());
        return entidades.toDomain(dto, clave);
    }

    /** Un profesional del hash {@code catedra:sync:professionals}. */
    public Professional toProfessional(String clave, long id, String json) {
        ProfessionalDto dto = leer(json, ProfessionalDto.class, clave, id);
        exigirElMismoId(clave, id, dto.id());
        return entidades.toDomain(dto, clave);
    }

    /** Un horario del hash {@code catedra:sync:weekly-schedules}. */
    public WeeklySchedule toSchedule(String clave, long id, String json) {
        WeeklyScheduleDto dto = leer(json, WeeklyScheduleDto.class, clave, id);
        exigirElMismoId(clave, id, dto.id());
        return entidades.toDomain(dto, clave);
    }

    /**
     * Deserializa el valor de un field.
     *
     * <p>El mensaje nombra la clave y el field, nunca el JSON completo:
     * ubicar el dato del otro lado es lo que hace falta, y volcar el valor
     * entero en una traza por un parseo fallido arrastra el catalogo a los
     * logs.
     */
    private <D> D leer(String json, Class<D> tipo, String clave, long id) {
        try {
            D dto = jackson.readValue(json, tipo);
            if (dto == null) {
                // Pasa con el valor literal "null", que es JSON valido.
                throw new MalformedPublishedCatalogException(ubicacion(clave, id) + " es nulo");
            }
            return dto;
        } catch (JacksonException noEsJson) {
            throw new MalformedPublishedCatalogException(
                    ubicacion(clave, id) + " no es un JSON que se pueda leer", noEsJson);
        }
    }

    private void exigirElMismoId(String clave, long field, Long idDelJson) {
        // Un id ausente lo informa el mapper de entidades, que ya nombra el
        // campo: aca solo se compara cuando hay algo que comparar.
        if (idDelJson != null && idDelJson != field) {
            throw new MalformedPublishedCatalogException(
                    ubicacion(clave, field) + " trae en el JSON el id " + idDelJson
                            + ": el field y el id tienen que ser el mismo dato (§14.4)");
        }
    }

    private long versionDelHash(Map<String, String> hash, String campo) {
        return toVersion(campoDelHash(hash, campo), hash.get(campo));
    }

    private Instant instanteDelHash(Map<String, String> hash, String campo) {
        String clave = campoDelHash(hash, campo);
        String valor = hash.get(campo);
        try {
            return Instant.parse(valor.trim());
        } catch (DateTimeParseException noEsUnInstante) {
            throw new MalformedPublishedCatalogException(
                    "la clave \"" + clave + "\" tiene que ser un instante ISO-8601 en UTC"
                            + " (§4) y trae \"" + valor + "\"",
                    noEsUnInstante);
        }
    }

    /**
     * Exige que el campo este en el hash y devuelve su ubicacion legible.
     *
     * <p>Devolver la ubicacion en lugar de un booleano permite que el que
     * llama nombre siempre el mismo texto —{@code catedra:sync:metadata
     * [currentVersion]}— tanto si el campo falta como si esta y no se
     * entiende.
     */
    private String campoDelHash(Map<String, String> hash, String campo) {
        String valor = hash.get(campo);
        if (valor == null || valor.isBlank()) {
            throw new MalformedPublishedCatalogException(
                    "al hash \"" + PublishedCatalogKeys.METADATA + "\" le falta el campo \"" + campo
                            + "\" (§14.3)");
        }
        return PublishedCatalogKeys.METADATA + "[" + campo + "]";
    }

    private static String ubicacion(String clave, long id) {
        return "el valor del field " + id + " del hash \"" + clave + "\"";
    }
}

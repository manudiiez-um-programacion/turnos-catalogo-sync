package com.um.catalogsync.sync.infrastructure.redis.adapter;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.ports.out.PublishedCatalogPort;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisConnection;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import com.um.catalogsync.sync.infrastructure.redis.mapper.PublishedCatalogMapper;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Lectura del catalogo publicado en el Redis de la catedra (§14).
 *
 * <p><b>Solo lee, y solo del namespace que le corresponde.</b> Los seis
 * comandos que usa son {@code GET}, {@code HGET} y {@code HGETALL} sobre las
 * claves de {@link PublishedCatalogKeys}, que todas empiezan con
 * {@code catedra:sync:}. No hay ninguna escritura —la ACL de la catedra no
 * la permitiria (§14.1), asi que el error aparecería en produccion y no en
 * revision— y no se toca {@code catedra:holds:*}, que es interno de la
 * catedra: los holds se manipulan exclusivamente por REST.
 *
 * <p><b>Ausente y malformado no son lo mismo.</b> Una clave que no existe
 * devuelve {@link Optional#empty()}: es un estado legitimo del origen —la
 * catedra todavia no publico eso— y quien lee decide. Un valor que existe y
 * no se entiende es un contrato incumplido y sale como excepcion del mapper.
 * La distincion importa porque las respuestas son opuestas: esperar contra
 * revisar el contrato.
 *
 * <p><b>Las fallas de Redis se traducen una sola vez, aca.</b> Lettuce lanza
 * {@link RedisException} para todo —host inalcanzable, timeout de comando,
 * comando denegado por la ACL, conexion cortada— y ninguna de esas clases
 * puede asomar del puerto: {@code sync} no conoce Lettuce. Se convierten en
 * {@link PublishedCatalogUnavailableException}, que es lo que #26 y #27 van a
 * tratar como "el estado del origen no se pudo verificar" para caer al
 * snapshot (§18.2).
 *
 * <p>No tiene estado: la conexion la administra
 * {@link CatedraRedisConnection}, que la abre tarde y la reusa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPublishedCatalogAdapter implements PublishedCatalogPort {

    private final CatedraRedisConnection redis;
    private final PublishedCatalogMapper mapper;

    @Override
    public Optional<Long> currentVersion() {
        return version(PublishedCatalogKeys.CURRENT_VERSION);
    }

    @Override
    public Optional<Long> oldestAvailableVersion() {
        return version(PublishedCatalogKeys.OLDEST_AVAILABLE_VERSION);
    }

    @Override
    public Optional<PublishedCatalogMetadata> metadata() {
        String clave = PublishedCatalogKeys.METADATA;
        Map<String, String> hash = ejecutar(comandos -> comandos.hgetall(clave), clave);

        // HGETALL de una clave que no existe devuelve un mapa vacio y no
        // null: en Redis no hay diferencia entre un hash inexistente y uno
        // sin campos, asi que vacio es la unica forma que tiene la ausencia.
        if (hash.isEmpty()) {
            log.debug("El hash \"{}\" no existe todavia en Redis", clave);
            return Optional.empty();
        }
        return Optional.of(mapper.toMetadata(hash));
    }

    @Override
    public Optional<ProfessionalCategory> findCategory(long id) {
        String clave = PublishedCatalogKeys.PROFESSIONAL_CATEGORIES;
        return field(clave, id).map(json -> mapper.toCategory(clave, id, json));
    }

    @Override
    public Optional<Professional> findProfessional(long id) {
        String clave = PublishedCatalogKeys.PROFESSIONALS;
        return field(clave, id).map(json -> mapper.toProfessional(clave, id, json));
    }

    @Override
    public Optional<WeeklySchedule> findSchedule(long id) {
        String clave = PublishedCatalogKeys.WEEKLY_SCHEDULES;
        return field(clave, id).map(json -> mapper.toSchedule(clave, id, json));
    }

    /** Una de las dos claves de version (§14.2). */
    private Optional<Long> version(String clave) {
        return Optional.ofNullable(ejecutar(comandos -> comandos.get(clave), clave))
                .map(valor -> mapper.toVersion(clave, valor));
    }

    /**
     * El valor de un field, o vacio si no esta.
     *
     * <p>El id se pasa a texto porque el field es el id decimal (§14.4): es
     * el unico lugar del adaptador donde el formato de la clave importa, y
     * por eso no se delega al mapper.
     */
    private Optional<String> field(String clave, long id) {
        String donde = clave + "[" + id + "]";
        return Optional.ofNullable(
                ejecutar(comandos -> comandos.hget(clave, String.valueOf(id)), donde));
    }

    /**
     * Corre un comando traduciendo las fallas de Lettuce.
     *
     * <p>Las excepciones propias —la de configuracion sin
     * {@code redisPassword}, las del mapper— no descienden de
     * {@link RedisException}, asi que pasan de largo y no quedan envueltas
     * como si fueran un problema de conexion.
     *
     * @param que la clave o el field que se estaba leyendo, para el mensaje
     */
    private <T> T ejecutar(Function<RedisCommands<String, String>, T> comando, String que) {
        try {
            return comando.apply(redis.comandos());
        } catch (RedisException falla) {
            throw new PublishedCatalogUnavailableException("\"" + que + "\"", falla);
        }
    }
}

package com.um.catalogsync.sync.infrastructure.redis.adapter;

import com.um.catalogsync.sync.domain.model.CatalogChanges;
import com.um.catalogsync.sync.domain.ports.out.CatalogChangesPort;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisConnection;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import com.um.catalogsync.sync.infrastructure.redis.mapper.CatalogChangesMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Lectura de los cambios de una version en el Redis de la catedra (§14.5).
 *
 * <p>Un solo comando: {@code GET} sobre
 * {@code catedra:sync:changes:{version}}. La clave es un String cuyo
 * contenido es JSON —no un Hash ni una List— asi que no hay nada que
 * recorrer del lado de Redis: viene entera y el mapper la interpreta.
 *
 * <p><b>La clave ausente se devuelve como ausente, sin interpretarla.</b> Es
 * la decision central de este adaptador y del issue: aca no se sabe si falta
 * porque la version todavia no se publico o porque ya salio de la ventana
 * retenida, y las dos cosas llevan a caminos opuestos —esperar contra
 * reconstruir con snapshot—. Quien tiene las dos versiones del origen
 * delante puede decidir, y eso es #26. Un adaptador que decidiera acá
 * mezclaria la lectura con la politica de recuperacion.
 *
 * <p><b>Lo que si distingue es ausente de vacio.</b> Una clave que existe
 * con los tres arrays vacios es una version publicada que no toco nada:
 * vuelve como un {@link CatalogChanges} con {@link CatalogChanges#isEmpty()}
 * en {@code true}, que #25 puede aplicar y hace avanzar la version local.
 * Colapsar los dos casos en {@code Optional.empty()} convertiria una version
 * valida en una discontinuidad y dispararia un snapshot completo sin motivo.
 *
 * <p>Las fallas de Lettuce las traduce {@link CatedraRedisConnection#leer},
 * igual que en el adaptador del catalogo publicado: el puerto solo ve
 * excepciones de {@code sync}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCatalogChangesAdapter implements CatalogChangesPort {

    private final CatedraRedisConnection redis;
    private final CatalogChangesMapper mapper;

    @Override
    public Optional<CatalogChanges> changesFor(long version) {
        String clave = PublishedCatalogKeys.changes(version);
        String json = redis.leer(comandos -> comandos.get(clave), clave);

        if (json == null) {
            // Sin nivel de aviso: desde acá no se sabe si es un problema.
            // Para la version siguiente a la publicada es lo normal, y para
            // una que hace falta es una discontinuidad que #26 va a
            // registrar con el contexto que tiene.
            log.debug("La clave \"{}\" no existe en Redis", clave);
            return Optional.empty();
        }
        CatalogChanges cambios = mapper.toChanges(clave, version, json);
        log.debug("La version {} cambio {} entidades", version, cambios.totalIds());
        return Optional.of(cambios);
    }
}

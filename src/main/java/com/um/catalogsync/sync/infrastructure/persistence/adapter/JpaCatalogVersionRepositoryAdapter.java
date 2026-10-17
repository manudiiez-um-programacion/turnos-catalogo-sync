package com.um.catalogsync.sync.infrastructure.persistence.adapter;

import com.um.catalogsync.sync.application.exception.ConcurrentVersionAdvanceException;
import com.um.catalogsync.sync.application.exception.VersionRegressionException;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import com.um.catalogsync.sync.infrastructure.persistence.mapper.CatalogVersionMapper;
import com.um.catalogsync.sync.infrastructure.persistence.repository.JpaCatalogVersionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el puerto de la version local con JPA.
 *
 * <p><b>La propagacion es {@code REQUIRED}, la de por defecto, y eso es el
 * corazon del issue.</b> No es una eleccion de estilo: es lo que hace
 * cumplir la regla no negociable del proyecto. Cuando la sincronizacion
 * aplica datos y avanza la version dentro de una misma transaccion, las dos
 * escrituras comparten destino. Si algo falla despues de aplicar los datos,
 * el rollback se lleva tambien el avance de version, y la copia local
 * queda descrita por la version que de verdad tiene. Con
 * {@code REQUIRES_NEW} la version se commitearia sola y quedaria
 * afirmando que tenemos datos que nunca se guardaron.
 *
 * <p>No hay nada en esta clase que "implemente" esa garantia; se consigue
 * por no hacer nada que la rompa. La prueba que lo demuestra abre una
 * transaccion, aplica una categoria, avanza la version y falla a proposito.
 */
@Component
@RequiredArgsConstructor
public class JpaCatalogVersionRepositoryAdapter implements CatalogVersionRepository {

    private final JpaCatalogVersionRepository jpaRepository;
    private final CatalogVersionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogVersion> findCurrent() {
        return jpaRepository.findById(CatalogVersionEntity.ID_UNICA).map(mapper::toDomain);
    }

    /**
     * Avanza la version en tres pasos: bloquea, decide, escribe.
     *
     * <p>El orden importa. Primero {@code lockCurrent}, que lee la fila
     * tomando un lock de escritura: a partir de ahi ninguna otra
     * transaccion que pase por el mismo metodo puede tocarla hasta que esta
     * termine. Despues la
     * decision, que es del dominio y no de esta clase:
     * {@code CatalogVersion.allowsAdvanceTo}. Recien al final la escritura.
     * Si la decision se tomara antes del lock, dos sincronizaciones podrian
     * aprobar avances incompatibles leyendo el mismo estado viejo.
     *
     * <p>Hay un caso que el lock no puede cubrir: la <b>primera</b> version
     * de una base recien inicializada. No se puede bloquear una fila que no
     * existe, asi que dos transacciones pueden llegar las dos a insertar. La
     * clave primaria —con el CHECK {@code id = 1}— deja pasar una sola, y la
     * otra recibe una violacion de integridad que se traduce a
     * {@code ConcurrentVersionAdvanceException}. Por eso la invariante "una
     * sola fila vigente" se cumple en los dos caminos: por el lock cuando la
     * fila existe, por la clave primaria cuando todavia no.
     */
    @Override
    @Transactional
    public CatalogVersion advanceTo(long version, CatalogVersionSource source) {
        Optional<CatalogVersionEntity> vigente = jpaRepository.lockCurrent();

        vigente.map(mapper::toDomain).ifPresent(actual -> {
            if (!actual.allowsAdvanceTo(version, source)) {
                throw new VersionRegressionException(actual.getVersion(), version);
            }
        });

        CatalogVersionEntity aGuardar = CatalogVersionEntity.builder()
                .id(CatalogVersionEntity.ID_UNICA)
                .appliedVersion(version)
                .appliedAt(ahora())
                .source(source.name())
                .build();
        try {
            // saveAndFlush y no save: sin el flush, la violacion de clave
            // primaria del caso concurrente estallaria al commitear, fuera
            // del try, y no se podria traducir. Es el mismo motivo que en
            // los adaptadores de profesionales y horarios.
            return mapper.toDomain(jpaRepository.saveAndFlush(aGuardar));
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrentVersionAdvanceException(version, e);
        }
    }

    /**
     * El instante en que se asienta la version, truncado a microsegundos.
     *
     * <p>El truncado no es cosmetico: {@code TIMESTAMPTZ} de PostgreSQL
     * tiene resolucion de microsegundos y {@code Instant.now()} puede traer
     * nanosegundos. Sin truncar, lo que devuelve este metodo y lo que se
     * lee despues de la base no serian el mismo valor, y el llamador que
     * compare las dos cosas encontraria una diferencia que no existe.
     *
     * <p>Es nuestro reloj y no un dato de la catedra: el contrato no manda
     * cuando aplicamos una version.
     */
    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

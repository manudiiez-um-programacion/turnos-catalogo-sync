package com.um.catalogsync.category.infrastructure.persistence.adapter;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.category.domain.ports.out.CategoryRepository;
import com.um.catalogsync.category.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import com.um.catalogsync.category.infrastructure.persistence.mapper.ProfessionalCategoryMapper;
import com.um.catalogsync.category.infrastructure.persistence.repository.JpaProfessionalCategoryRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el puerto de salida del dominio con JPA.
 *
 * <p>Nunca devuelve una {@code ProfessionalCategoryEntity}: todo sale
 * mapeado a {@code ProfessionalCategory}. Si esta clase filtrara la
 * entidad, la regla de dependencias quedaria rota sin que el compilador
 * dijera nada.
 *
 * <p>Las escrituras son transaccionales con propagacion por defecto
 * ({@code REQUIRED}), asi que cada una es atomica por si sola pero se suma
 * a la transaccion de la sincronizacion cuando esta corre dentro de una.
 * Eso es lo que permite que la version local avance solo si todo el trabajo
 * termino bien.
 */
@Component
@RequiredArgsConstructor
public class JpaCategoryRepositoryAdapter implements CategoryRepository {

    private final JpaProfessionalCategoryRepository jpaRepository;
    private final ProfessionalCategoryMapper mapper;

    /**
     * {@code save} de Spring Data resuelve el upsert solo: con el id ya
     * puesto la entidad no es nueva, asi que hace {@code merge} en lugar de
     * {@code persist}, y eso inserta o actualiza segun exista o no.
     *
     * <p>Tiene un costo: {@code merge} hace un SELECT previo por cada
     * entidad. Para aplicar un snapshot entero eso son N consultas de mas.
     * Si llega a pesar, el lugar para optimizarlo es #13, con un upsert en
     * lote; la firma del puerto no cambia.
     */
    @Override
    @Transactional
    public ProfessionalCategory upsert(ProfessionalCategory category) {
        ProfessionalCategoryEntity guardada = jpaRepository.save(mapper.toEntity(category));
        return mapper.toDomain(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalCategory> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalCategory> findAll() {
        return mapper.toDomain(jpaRepository.findAll());
    }

    /**
     * Borra todo y vuelve a insertar, en una sola transaccion: o queda el
     * contenido nuevo completo, o no cambia nada.
     *
     * <p>{@code deleteAllInBatch} y no {@code deleteAll}: el segundo carga
     * cada entidad para borrarla una por una, y aca no hace falta porque no
     * hay cascadas que JPA deba resolver.
     */
    @Override
    @Transactional
    public List<ProfessionalCategory> replaceAll(Collection<ProfessionalCategory> categories) {
        jpaRepository.deleteAllInBatch();
        List<ProfessionalCategoryEntity> guardadas =
                jpaRepository.saveAll(mapper.toEntity(categories));
        return mapper.toDomain(guardadas);
    }

    @Override
    @Transactional
    public long deleteByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        return jpaRepository.deleteByIdIn(ids);
    }
}

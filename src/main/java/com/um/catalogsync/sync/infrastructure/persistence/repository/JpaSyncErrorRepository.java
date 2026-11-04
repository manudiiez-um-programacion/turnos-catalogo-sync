package com.um.catalogsync.sync.infrastructure.persistence.repository;

import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data de los errores de sincronizacion.
 *
 * <p>No tiene un puerto de dominio propio y no le falta uno: los errores no
 * son una entidad con vida independiente de su corrida —la FK es
 * {@code ON DELETE CASCADE} justamente por eso—, asi que se escriben y se leen
 * a traves de {@code SyncRunRepository}, que es el puerto del historial, y no
 * por uno aparte.
 */
public interface JpaSyncErrorRepository extends JpaRepository<SyncErrorEntity, Long> {

    /**
     * Los errores mas nuevos primero, tantos como pida el {@link Pageable}.
     *
     * <p>El {@code Pageable} se usa solo para limitar, no para paginar: quien
     * pregunta por el estado quiere los ultimos errores, no recorrer el
     * historial. Desempata por id por lo mismo que las corridas: dos errores
     * de la misma corrida comparten el {@code occurred_at} al microsegundo
     * —se escriben con el mismo instante— y sin el desempate el orden
     * dependeria del plan.
     */
    List<SyncErrorEntity> findAllByOrderByOccurredAtDescIdDesc(Pageable limite);
}

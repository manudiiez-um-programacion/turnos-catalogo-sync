package com.um.catalogsync.sync.infrastructure.persistence.repository;

import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data de los errores de sincronizacion.
 *
 * <p>No tiene un puerto de dominio propio y no le falta uno: los errores no
 * son una entidad con vida independiente de su corrida —la FK es
 * {@code ON DELETE CASCADE} justamente por eso—, asi que se escriben a
 * traves de {@code SyncRunRepository.fail} y no por un puerto aparte.
 */
public interface JpaSyncErrorRepository extends JpaRepository<SyncErrorEntity, Long> {
}

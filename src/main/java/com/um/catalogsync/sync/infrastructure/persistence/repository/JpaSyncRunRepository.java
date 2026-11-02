package com.um.catalogsync.sync.infrastructure.persistence.repository;

import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data de las corridas. Detalle de infraestructura, no el
 * puerto: el puerto del dominio es {@code SyncRunRepository}.
 *
 * <p>Sin metodos propios todavia. Las consultas del endpoint de estado
 * —las ultimas corridas, para las que la migracion ya dejo el indice
 * {@code ix_sync_run_started_at}— son de #28.
 */
public interface JpaSyncRunRepository extends JpaRepository<SyncRunEntity, Long> {
}

package com.um.catalogsync.sync.infrastructure.persistence.repository;

import com.um.catalogsync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Repositorio Spring Data. Detalle de infraestructura, no el puerto: el
 * puerto del dominio es {@code CatalogVersionRepository}.
 */
public interface JpaCatalogVersionRepository extends JpaRepository<CatalogVersionEntity, Short> {

    /**
     * Lee la fila unica tomando un lock de escritura.
     *
     * <p>En PostgreSQL, Hibernate traduce {@code PESSIMISTIC_WRITE} a
     * {@code SELECT ... FOR NO KEY UPDATE}, no a {@code FOR UPDATE}. La
     * diferencia es real pero no afecta lo que necesitamos: ese modo entra
     * en conflicto consigo mismo, asi que dos transacciones que pidan la
     * fila por este metodo se serializan igual. Lo que {@code FOR NO KEY
     * UPDATE} permite en paralelo es tomar una clave foranea contra esta
     * fila, y nadie la referencia.
     *
     * <p>Es lo que hace segura la decision de avanzar. Sin el lock habria
     * una ventana entre leer la version vigente y escribir la nueva: dos
     * sincronizaciones podrian leer las dos la version 5, las dos concluir
     * que avanzar es valido, y la segunda sobreescribir a la primera. Con
     * {@code FOR UPDATE}, la segunda espera a que la primera termine y
     * despues vuelve a leer, asi que decide contra el estado real.
     *
     * <p>Se consulta por {@code id = 1} y no con {@code findAll} porque la
     * tabla tiene exactamente una fila por construccion: pedirla por su
     * clave es lo que permite bloquearla.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from CatalogVersionEntity v where v.id = 1")
    Optional<CatalogVersionEntity> lockCurrent();
}

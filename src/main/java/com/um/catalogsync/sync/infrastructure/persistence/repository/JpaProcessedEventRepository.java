package com.um.catalogsync.sync.infrastructure.persistence.repository;

import com.um.catalogsync.sync.infrastructure.persistence.entity.ProcessedEventEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data. Detalle de infraestructura, no el puerto: el
 * puerto del dominio es {@code ProcessedEventRepository}.
 */
public interface JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {

    /**
     * Inserta el evento, o no hace nada si el {@code eventId} ya estaba.
     *
     * <p><b>Por que es SQL nativo y no un {@code save()}.</b> Hay tres
     * caminos para deduplicar y dos no sirven:
     *
     * <ul>
     *   <li>{@code existsById} y despues {@code save}: entre las dos
     *       llamadas cabe un duplicado. Es la carrera que la deduplicacion
     *       tiene que cerrar, no abrir.
     *   <li>{@code save} y atajar la violacion de clave: una violacion de
     *       integridad deja la sesion de Hibernate inservible y marca la
     *       transaccion como <i>rollback-only</i>. Atajar la excepcion no
     *       alcanza: la transaccion de quien llama ya no puede commitear, y
     *       justamente lo que se busca es que el duplicado termine bien y
     *       confirme el offset. Lo descubrio la prueba de esta clase.
     *   <li>{@code ON CONFLICT DO NOTHING}: la base resuelve el conflicto
     *       sin levantar ningun error, la transaccion sigue viva y la
     *       cantidad de filas afectadas responde la pregunta —1 es nuevo, 0
     *       es duplicado—.
     * </ul>
     *
     * <p>Es sintaxis de PostgreSQL, que es la unica base de este proyecto
     * por la decision D3. No se busca portabilidad: se busca que la
     * deduplicacion sea atomica, y eso lo da el motor.
     *
     * @return 1 si el evento era nuevo, 0 si el {@code eventId} ya estaba
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_event
                (event_id, event_type, new_version, occurred_at, source, processed_at)
            VALUES (:eventId, :eventType, :newVersion, :occurredAt, :source, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("newVersion") long newVersion,
            @Param("occurredAt") Instant occurredAt,
            @Param("source") String source,
            @Param("processedAt") Instant processedAt);

    /**
     * Borra lo procesado antes de un instante.
     *
     * <p>Un {@code DELETE} en una sola sentencia y no un
     * {@code deleteAll(findAll(...))}: la purga puede tocar muchas filas, y
     * traerlas todas a memoria para borrarlas una por una seria el camino
     * caro de hacer exactamente lo mismo.
     *
     * @return cuantas filas se borraron
     */
    @Modifying
    @Query("delete from ProcessedEventEntity e where e.processedAt < :limite")
    int deleteByProcessedAtBefore(@Param("limite") Instant limite);
}

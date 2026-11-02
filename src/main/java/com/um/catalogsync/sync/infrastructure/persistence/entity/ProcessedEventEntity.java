package com.um.catalogsync.sync.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representacion JPA de un {@code CatalogUpdated} ya procesado.
 *
 * <p><b>El {@code eventId} es la clave primaria</b>, y eso no es una eleccion
 * de modelado sino el mecanismo de deduplicacion: insertar es lo que decide
 * si el evento es nuevo. Con un id sintetico y un indice unico aparte habria
 * que consultar antes de insertar, y entre la consulta y el insert cabe un
 * duplicado.
 *
 * <p>Viene de la catedra y es texto libre del contrato (§15.1), asi que el
 * largo acompaña al {@code VARCHAR(255)} de la migracion sin interpretar
 * nada de su forma: los ejemplos son del estilo
 * {@code catalog-event-7-4f3b}, pero el contrato no promete ese formato.
 *
 * <p><b>Nada escribe esta entidad.</b> El insert es SQL nativo con
 * {@code ON CONFLICT DO NOTHING} —el motivo esta en
 * {@code JpaProcessedEventRepository}— asi que la clase existe por otras
 * dos razones, y las dos valen: le da nombre a la tabla en la consulta JPQL
 * de la purga, y hace que {@code ddl-auto: validate} compare este mapeo
 * contra el esquema que crearon las migraciones. Si la tabla y esta clase se
 * separan, las pruebas de integracion no arrancan.
 *
 * <p>Una fila de esta tabla tampoco se actualiza nunca: se inserta cuando el
 * evento se procesa y se borra cuando vence la retencion.
 */
@Entity
@Table(name = "processed_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "new_version", nullable = false)
    private long newVersion;

    /** El reloj de la catedra: cuando genero el aviso. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * El origen que informa la catedra ({@code MANUAL}, {@code SCHEDULED},
     * ...). Nulo si el evento no lo trajo: la migracion no lo exige porque
     * §15.3 lo describe con ejemplos y no como lista cerrada.
     */
    @Column(name = "source", length = 64)
    private String source;

    /** Nuestro reloj: cuando lo procesamos. Es el que usa la retencion. */
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

}

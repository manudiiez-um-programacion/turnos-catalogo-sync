package com.um.catalogsync.sync.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representacion JPA del motivo por el que fallo una corrida.
 *
 * <p><b>La corrida se referencia por {@code syncRunId} y no por una relacion
 * {@code @ManyToOne}.</b> Es el mismo criterio que usan los modelos de
 * catalogo con {@code categoryId} y {@code professionalId}: la columna
 * cruda, sin que JPA administre el grafo. Acá suma una razon mas, y es la
 * que decide: esta fila se escribe <b>en la transaccion del registro</b>,
 * que es distinta de la que aplica los datos, y una relacion administrada
 * invitaria a cargar la corrida entera para poder escribir su error.
 *
 * <p>No hay un modelo de dominio {@code SyncError} todavia. El puerto
 * {@code SyncRunRepository.fail} recibe el codigo y el detalle sueltos
 * porque es todo lo que hace falta para escribir; cuando #28 necesite
 * leerlos para informarlos, ahi aparecera el modelo.
 */
@Entity
@Table(name = "sync_error")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncErrorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sync_run_id", nullable = false)
    private Long syncRunId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** El {@code SyncErrorCode} como texto, por el mismo criterio que los demas enums. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /**
     * Texto libre para diagnosticar, nulable.
     *
     * <p>Es el mensaje de la excepcion que fallo. Nunca lleva secretos, y
     * eso no es casualidad: las excepciones del proyecto estan escritas para
     * no incluirlos —el adaptador del snapshot ni siquiera lee el
     * {@code detail} de la respuesta de error— y hay una prueba que verifica
     * que el JWT tecnico no aparece en sus trazas.
     */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;
}

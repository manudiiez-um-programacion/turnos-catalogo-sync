package com.um.catalogsync.sync.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un error de sincronizacion, como lo informa el estado del servicio.
 *
 * <p>Existe porque el reporte de estado tiene que devolver errores y hasta
 * ahora los errores solo existian como filas: {@code sync_error} se escribia
 * desde {@code SyncRunRepository.fail} y nadie los leia. Leerlos es la
 * ultima responsabilidad de §4.1, y para eso hacen falta como modelo.
 *
 * <p><b>El {@link #code} es lo que se lee y el {@link #detail} lo que se
 * mira despues.</b> Es la misma regla que §13 fija para los errores HTTP: el
 * codigo es estable y se puede comparar, el texto se reescribe. Quien
 * consuma el estado deberia decidir por {@code code}.
 *
 * <p><b>Nunca lleva secretos.</b> No porque se filtren acá, sino porque el
 * {@code detail} es el mensaje de las excepciones del proyecto, que estan
 * escritas para no incluirlos: el adaptador del snapshot ni siquiera lee el
 * {@code detail} de la respuesta de error de la catedra, y las excepciones de
 * Redis no nombran el host ni el usuario. Hay una prueba que verifica que el
 * JWT tecnico y la clave de Redis no aparezcan en ninguna traza.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncError {

    /** La corrida en la que ocurrio. */
    private Long syncRunId;

    /** Cuando ocurrio. Nuestro reloj. */
    private Instant occurredAt;

    /** Codigo estable: lo que se puede comparar sin parsear nada. */
    private SyncErrorCode code;

    /** Texto para diagnosticar. Puede ser nulo. */
    private String detail;
}

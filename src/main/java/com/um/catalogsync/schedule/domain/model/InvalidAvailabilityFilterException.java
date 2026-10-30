package com.um.catalogsync.schedule.domain.model;

/**
 * El filtro de disponibilidad que pidio el cliente no se puede interpretar.
 *
 * <p>Cubre los cuatro casos en los que el pedido no tiene sentido: un dia
 * que no es uno de los siete del contrato, una hora que no respeta
 * {@code HH:mm} ni {@code HH:mm:ss}, un rango a medias —una sola de las dos
 * horas— y un rango invertido o de duracion cero.
 *
 * <p><b>Por que un solo tipo para los cuatro y no uno por caso.</b> Lo que
 * el cliente tiene que hacer es idéntico en todos: corregir los parametros
 * de disponibilidad antes de reintentar. Un codigo por subcaso convertiria
 * agregar un parametro al filtro en un cambio de contrato, cuando la
 * decision del otro lado no cambia. El mensaje dice cual de los cuatro fue,
 * y viaja en el {@code detail}; es el mismo criterio con el que se definio
 * {@code VALIDATION_ERROR}.
 *
 * <p><b>Por que no se reusa {@code VALIDATION_ERROR}.</b> Esto no lo detecta
 * el binding de Spring: un dia inexistente o un rango invertido son cadenas
 * perfectamente bien formadas que solo este dominio sabe rechazar. Tener su
 * propio {@code code} le permite al cliente distinguir "mande mal el filtro
 * de disponibilidad" de "mande mal el paginado", que son dos pantallas
 * distintas de la app.
 *
 * <p>Vive en {@code domain/model} junto a {@link UnknownDayOfWeekException} y
 * {@link InvalidTimeOfDayException}, que son las dos que envuelve: es la
 * misma clase de invariante y el mismo lugar.
 */
public class InvalidAvailabilityFilterException extends RuntimeException {

    public InvalidAvailabilityFilterException(String mensaje) {
        super(mensaje);
    }

    public InvalidAvailabilityFilterException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

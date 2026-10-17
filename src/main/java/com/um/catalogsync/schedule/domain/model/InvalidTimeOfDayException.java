package com.um.catalogsync.schedule.domain.model;

/**
 * Llego una hora de atencion que no respeta {@code HH:mm} ni
 * {@code HH:mm:ss} (§4).
 *
 * <p>Vive en {@code domain/model} por el mismo motivo que
 * {@link UnknownDayOfWeekException}: la lanza
 * {@link TimeOfDay#parse(String)}, que es dominio, y {@code domain} no
 * puede importar {@code application}.
 *
 * <p>Envuelve el {@code DateTimeParseException} original como causa, asi
 * que el detalle tecnico no se pierde, pero el tipo que ve quien sincroniza
 * habla del contrato y no de la API de fechas de Java.
 */
public class InvalidTimeOfDayException extends RuntimeException {

    private final String valorRecibido;

    public InvalidTimeOfDayException(String valorRecibido, Throwable cause) {
        super("La hora " + describir(valorRecibido)
                + " no respeta el formato HH:mm ni HH:mm:ss del contrato", cause);
        this.valorRecibido = valorRecibido;
    }

    /** El valor tal como llego, {@code null} incluido, para diagnosticar. */
    public String getValorRecibido() {
        return valorRecibido;
    }

    private static String describir(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }
}

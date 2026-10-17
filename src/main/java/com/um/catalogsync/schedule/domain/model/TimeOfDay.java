package com.um.catalogsync.schedule.domain.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Parsea las horas de atencion del contrato.
 *
 * <p>§4 de la referencia de integracion dice que las horas son locales de
 * atencion y que <b>los clientes deben aceptar {@code HH:mm} y
 * {@code HH:mm:ss}</b>. Las dos formas aparecen de verdad: el snapshot
 * manda {@code "09:00:00"} (§7) y el contrato de holds acepta
 * {@code "09:30"}. Tolerar solo una de las dos rompe contra datos validos.
 *
 * <p>Vive en el dominio por el mismo motivo que {@code SearchableName} en el
 * slice de profesionales: define que significa una hora de atencion en este
 * servicio, no como se guarda. Es Java puro y estatico, y se prueba solo.
 *
 * <p><b>Por que un formato propio y no {@code LocalTime.parse}.</b> El
 * parseo por defecto usa {@code ISO_LOCAL_TIME}, que ya acepta las dos
 * formas del contrato — pero tambien {@code "09:00:00.123"},
 * {@code "09:00:00.123456789"} y el offset de la variante extendida. Eso
 * convertiria un cambio de contrato en algo que absorbemos en silencio. El
 * formato de abajo acepta exactamente las dos formas documentadas: los
 * segundos son opcionales y no hay nada despues. Si alguna vez llega una
 * tercera forma, falla con un error atribuible en lugar de pasar
 * desapercibida, que es el mismo criterio que con los dias desconocidos.
 */
public final class TimeOfDay {

    /** {@code HH:mm} con {@code :ss} opcional, y nada mas. */
    private static final DateTimeFormatter FORMATO_DEL_CONTRATO =
            new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.HOUR_OF_DAY, 2)
                    .appendLiteral(':')
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                    .optionalEnd()
                    .toFormatter(Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private TimeOfDay() {
    }

    /**
     * Convierte una hora del contrato en {@link LocalTime}.
     *
     * <p>Es {@code LocalTime} y no {@code OffsetTime} ni
     * {@code ZonedDateTime} a proposito: §4 dice que son horas locales de
     * atencion. Una regla semanal no ocurre en un instante, asi que no
     * tiene zona que aplicar; resolverla contra un dia concreto es
     * responsabilidad del servicio de turnos (§4.2).
     *
     * @param hora la hora como {@code HH:mm} o {@code HH:mm:ss}
     * @throws InvalidTimeOfDayException si es {@code null}, esta vacia o no
     *     respeta ninguno de los dos formatos
     */
    public static LocalTime parse(String hora) {
        if (hora == null || hora.isBlank()) {
            throw new InvalidTimeOfDayException(hora, null);
        }
        try {
            return LocalTime.parse(hora, FORMATO_DEL_CONTRATO);
        } catch (DateTimeParseException e) {
            throw new InvalidTimeOfDayException(hora, e);
        }
    }
}

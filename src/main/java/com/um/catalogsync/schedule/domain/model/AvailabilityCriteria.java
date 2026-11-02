package com.um.catalogsync.schedule.domain.model;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Que se le pide a la agenda local para considerar que un profesional esta
 * "disponible".
 *
 * <p><b>Que significa disponibilidad en este servicio.</b> Es el cuarto
 * filtro obligatorio de §4.1 y el que habia que definir, porque aca no hay
 * ocupaciones, bloqueos ni reservas: eso vive en el servicio de turnos
 * (§4.2 y §7 del enunciado). El criterio que se implementa es este, y no
 * otro:
 *
 * <blockquote>Un profesional esta disponible cuando <b>esta habilitado</b> y
 * tiene <b>al menos un horario habilitado</b> que cae dentro del dia o del
 * rango pedido.</blockquote>
 *
 * <p><b>Esto responde "tiene agenda", no "tiene cupo libre".</b> La
 * distincion no es una formalidad: un profesional que atiende los lunes de
 * 09:00 a 13:00 y tiene los cuatro turnos de esa mañana tomados sigue
 * cumpliendo este filtro. Decir lo contrario exigiria conocer las
 * ocupaciones, que este servicio no tiene y no debe tener. Es un prefiltro
 * sobre reglas de agenda: sirve para no ofrecerle al usuario profesionales
 * que no atienden nunca ese dia, y el cupo concreto lo resuelve turnos
 * cruzando esta agenda con sus propias reservas.
 *
 * <p><b>El rango se cruza por solapamiento, no por contencion.</b> Un
 * horario de 09:00 a 13:00 cumple un pedido de 10:00 a 11:00: el
 * profesional atiende en esa franja. Exigir que el horario estuviera
 * <i>contenido</i> en el rango seria preguntar algo que nadie quiere saber
 * —"atiende exclusivamente dentro de esta franja"— y dejaria fuera al
 * profesional que atiende toda la mañana cuando se busca a las diez.
 *
 * <p>El solapamiento se mide con desigualdades estrictas, asi que un
 * horario que termina a las 09:00 no cumple un pedido que empieza a las
 * 09:00: la interseccion seria de duracion cero y no hay atencion posible
 * ahi.
 *
 * <p><b>Las tres formas validas del filtro.</b> El criterio del issue dice
 * "el dia <i>o</i> el rango", asi que las tres combinaciones se aceptan:
 * solo el dia ("atiende los lunes"), solo el rango ("atiende por la
 * mañana, cualquier dia") o las dos ("atiende los lunes por la mañana").
 * Lo que no se acepta es media franja: ver {@link #de}.
 *
 * <p>Vive en el {@code domain} de {@code schedule} porque define que
 * significa la agenda local, aunque el filtro lo use la busqueda de
 * profesionales. Es la misma razon por la que {@code CurrentAgenda} vive
 * aca: el dueño del concepto es este slice.
 *
 * @param dayOfWeek dia pedido, o {@code null} para cualquier dia
 * @param from inicio del rango, o {@code null} si no se pidio rango
 * @param to fin del rango, o {@code null} si no se pidio rango
 */
public record AvailabilityCriteria(DayOfWeek dayOfWeek, LocalTime from, LocalTime to) {

    /**
     * El ultimo segundo de la jornada: el tope cuando no se pidio franja.
     *
     * <p><b>No es {@link LocalTime#MAX}</b>, y la diferencia se paga caro. Ese
     * vale {@code 23:59:59.999999999}, y en el camino hasta PostgreSQL hay dos
     * redondeos que lo llevan a {@code 24:00:00}, valor que el tipo
     * {@code time} normaliza a {@code 00:00:00}. El parametro termina valiendo
     * medianoche, la condicion {@code start_time < 00:00:00} no la cumple
     * ningun horario y una busqueda sin franja no devuelve a nadie. Los dos
     * redondeos, los dos verificados:
     *
     * <ul>
     *   <li>la columna {@code TIME} de PostgreSQL guarda microsegundos, asi
     *       que los nanosegundos se redondean al pasar;</li>
     *   <li>y antes que eso, Hibernate liga un {@code LocalTime} con
     *       precision de <i>milisegundos</i>, asi que incluso
     *       {@code 23:59:59.999999} ya sube a {@code 24:00:00}. Es lo que hace
     *       que el problema no se vea probando la consulta con JDBC a mano,
     *       donde el valor viaja entero.</li>
     * </ul>
     *
     * <p>De ahi que el tope este en segundos enteros y no un instante antes de
     * medianoche: es la misma precision que tienen los datos. Las horas del
     * contrato llegan como {@code HH:mm} o {@code HH:mm:ss} (§4), asi que no
     * existe un horario que empiece dentro del ultimo segundo del dia, y
     * quedarse en {@code 23:59:59} no deja nada afuera.
     */
    private static final LocalTime FIN_DE_LA_JORNADA = LocalTime.of(23, 59, 59);

    public AvailabilityCriteria {
        if (dayOfWeek == null && from == null && to == null) {
            throw new InvalidAvailabilityFilterException(
                    "el filtro de disponibilidad necesita al menos un dia o un rango de horas");
        }
        if ((from == null) != (to == null)) {
            throw new InvalidAvailabilityFilterException(
                    "el rango de horas se pide completo: hacen falta las dos horas, la de"
                            + " inicio y la de fin");
        }
        if (from != null && !to.isAfter(from)) {
            throw new InvalidAvailabilityFilterException(
                    "la hora de fin tiene que ser posterior a la de inicio");
        }
    }

    /**
     * Arma el criterio a partir de los valores de la consulta, tal como
     * llegan.
     *
     * <p>Devuelve vacio cuando no se pidio nada, que es lo normal: la
     * disponibilidad es un filtro opcional como los otros tres. Un
     * {@code Optional} y no {@code null} para que quien llama tenga que
     * decidir explicitamente que hace con la ausencia.
     *
     * <p><b>El parseo vive aca y no en el borde web</b> por lo mismo que la
     * normalizacion del nombre vive en el criterio de busqueda: define que
     * forma tiene una hora de atencion en este servicio, y tenerlo en un
     * solo lugar evita que dos caminos acepten formatos distintos. Reusa
     * {@link DayOfWeek#fromContract} y {@link TimeOfDay#parse}, que son las
     * mismas funciones con las que se leyo el snapshot.
     *
     * <p>Las fallas de las dos se traducen a
     * {@link InvalidAvailabilityFilterException}. Es a proposito: un dia o
     * una hora que no se entienden significan cosas muy distintas segun de
     * donde vengan. Si vienen del snapshot, el contrato de la catedra
     * cambio y la sincronizacion tiene que registrarlo; si vienen de una
     * query string, el cliente escribio mal el filtro y lo unico que
     * corresponde es un 400. Conservar la excepcion original haria que un
     * error del cliente apareciera con el vocabulario de un problema de
     * integracion.
     *
     * @param dia el dia como lo enumera el contrato, o {@code null}
     * @param desde la hora de inicio en {@code HH:mm} o {@code HH:mm:ss}, o
     *     {@code null}
     * @param hasta la hora de fin, en el mismo formato, o {@code null}
     * @throws InvalidAvailabilityFilterException si el dia no es uno de los
     *     siete, una hora no respeta el formato, el rango viene a medias o
     *     el fin no es posterior al inicio
     */
    public static Optional<AvailabilityCriteria> de(String dia, String desde, String hasta) {
        if (enBlanco(dia) && enBlanco(desde) && enBlanco(hasta)) {
            return Optional.empty();
        }
        return Optional.of(
                new AvailabilityCriteria(diaDe(dia), horaDe(desde, "desde"), horaDe(hasta, "hasta")));
    }

    /** @return si hay que restringir por dia de la semana */
    public boolean filtraPorDia() {
        return dayOfWeek != null;
    }

    /** @return si hay que restringir por franja horaria */
    public boolean filtraPorRango() {
        return from != null;
    }

    /**
     * Los dias que satisfacen el pedido.
     *
     * <p>Uno solo si se pidio un dia, y <b>los siete</b> si no: "cualquier
     * dia" es el conjunto completo, no la ausencia de condicion. Expresarlo
     * asi evita que quien consulta tenga que inventar una rama para el caso
     * sin dia, y deja la consulta con una sola forma —un {@code IN} sobre un
     * conjunto que nunca esta vacio— en lugar de dos.
     */
    public Set<DayOfWeek> diasPedidos() {
        return filtraPorDia() ? EnumSet.of(dayOfWeek) : EnumSet.allOf(DayOfWeek.class);
    }

    /**
     * Inicio de la franja pedida, o el comienzo del dia si no se pidio rango.
     *
     * <p>Por el mismo criterio que {@link #diasPedidos()}: "a cualquier hora"
     * es la jornada entera y no una condicion ausente. Cualquier horario se
     * solapa con la jornada completa, asi que el resultado es el mismo que no
     * filtrar, y a cambio la consulta queda con una sola forma y todos sus
     * parametros tipados.
     */
    public LocalTime franjaDesde() {
        return filtraPorRango() ? from : LocalTime.MIN;
    }

    /**
     * Fin de la franja pedida, o el final del dia. Ver {@link #franjaDesde()}
     * y, para el valor del tope, {@link #FIN_DE_LA_JORNADA}.
     */
    public LocalTime franjaHasta() {
        return filtraPorRango() ? to : FIN_DE_LA_JORNADA;
    }

    private static DayOfWeek diaDe(String dia) {
        if (enBlanco(dia)) {
            return null;
        }
        try {
            return DayOfWeek.fromContract(dia);
        } catch (UnknownDayOfWeekException error) {
            throw new InvalidAvailabilityFilterException(
                    "'" + dia + "' no es un dia de la semana valido", error);
        }
    }

    private static LocalTime horaDe(String hora, String cual) {
        if (enBlanco(hora)) {
            return null;
        }
        try {
            return TimeOfDay.parse(hora);
        } catch (InvalidTimeOfDayException error) {
            throw new InvalidAvailabilityFilterException(
                    "la hora '" + cual + "' tiene que venir como HH:mm o HH:mm:ss", error);
        }
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }
}

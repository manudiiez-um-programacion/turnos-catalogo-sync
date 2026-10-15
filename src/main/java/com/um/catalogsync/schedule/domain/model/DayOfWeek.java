package com.um.catalogsync.schedule.domain.model;

/**
 * Dia de la semana de un horario semanal, con los siete valores que el
 * contrato enumera de forma cerrada (§7 de la referencia de integracion).
 *
 * <p><b>Por que un enum propio y no {@link java.time.DayOfWeek}.</b> El de
 * la plataforma tiene los mismos siete nombres en el mismo orden, asi que
 * la tentacion es usarlo. Se descarta por dos razones. Primero, arrastra
 * una API de calendario — {@code plus}, {@code getDisplayName},
 * {@code TemporalAccessor} — que no tiene sentido para una regla semanal
 * recurrente: aca el dia no es una fecha, es una etiqueta del contrato.
 * Segundo, y mas importante, {@code valueOf} de la plataforma falla con un
 * {@code IllegalArgumentException} generico, y este slice necesita que un
 * dia desconocido se distinga de cualquier otro error de programacion para
 * que la sincronizacion lo registre como inconsistencia del contrato
 * (#13, #26).
 *
 * <p>El orden de declaracion es el orden cronologico de la semana ISO, de
 * lunes a domingo. No es decorativo: {@link WeeklySchedule#BY_DAY_AND_START}
 * ordena por el {@code ordinal} de este enum, asi que reordenar las
 * constantes cambiaria el orden de la agenda.
 */
public enum DayOfWeek {

    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    /**
     * Convierte el valor que trae el contrato —el snapshot, Redis o la
     * columna {@code day_of_week}— al enum de dominio.
     *
     * <p>Es la unica puerta de entrada: no hay forma de construir un
     * horario con un dia que no sea uno de los siete. Un valor desconocido
     * no se mapea a {@code null}, ni al lunes, ni se descarta la fila en
     * silencio: se lanza {@link UnknownDayOfWeekException}. Un dia que el
     * contrato no enumera significa que el contrato cambio, y eso tiene que
     * verse.
     *
     * <p>La comparacion es exacta, sensible a mayusculas. El contrato manda
     * {@code "MONDAY"} y la columna tiene un CHECK con esos mismos siete
     * literales, asi que aceptar {@code "monday"} seria tolerar algo que
     * nunca deberia llegar y que, si llega, es una senal.
     *
     * @throws UnknownDayOfWeekException si el valor es {@code null} o no es
     *     uno de los siete dias del contrato
     */
    public static DayOfWeek fromContract(String valor) {
        if (valor != null) {
            for (DayOfWeek dia : values()) {
                if (dia.name().equals(valor)) {
                    return dia;
                }
            }
        }
        throw new UnknownDayOfWeekException(valor);
    }
}

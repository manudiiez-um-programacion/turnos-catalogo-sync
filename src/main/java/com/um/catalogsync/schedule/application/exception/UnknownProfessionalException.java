package com.um.catalogsync.schedule.application.exception;

/**
 * Se intento guardar un horario cuyo profesional no esta en la copia local.
 *
 * <p>Es el analogo exacto de {@code UnknownCategoryException} del slice de
 * profesionales, un nivel mas abajo en la cadena de FK del catalogo
 * (categoria → profesional → horario), y la decision es la misma: <b>se
 * rechaza</b>. No se acepta el horario con el profesional colgando ni se lo
 * marca como pendiente.
 *
 * <p>Por que rechazar:
 *
 * <ul>
 *   <li>Para datos validos nunca pasa. Los cambios de una version se
 *       aplican en una sola unidad de trabajo, asi que basta con aplicar
 *       primero categorias, despues profesionales y al final horarios. Ese
 *       orden lo pone quien orquesta (#13 y #25) y hace falta igual —en
 *       sentido inverso— para los borrados, porque las FK son RESTRICT.
 *   <li>Cuando si pasa, es una inconsistencia real, y fallar es lo que
 *       corresponde: la version local no avanza, que es la regla no
 *       negociable del proyecto, y eso deriva en la deteccion de
 *       discontinuidad y la reconstruccion por snapshot (#26).
 *   <li>Un horario huerfano es peor que un profesional huerfano: la agenda
 *       que este servicio le entrega al servicio de turnos (#18) tendria
 *       horarios de alguien que no existe, y el filtro de disponibilidad
 *       (#16) los contaria como disponibilidad real.
 * </ul>
 *
 * <p>Existe para que el error llegue con sentido de dominio. Sin ella,
 * quien sincroniza recibiria una violacion de integridad generica y tendria
 * que interpretar un mensaje de PostgreSQL para saber que falto.
 */
public class UnknownProfessionalException extends RuntimeException {

    private final Long professionalId;

    public UnknownProfessionalException(Long professionalId, Throwable cause) {
        super("No se puede guardar un horario del profesional " + professionalId
                + ": ese profesional no esta en la copia local", cause);
        this.professionalId = professionalId;
    }

    public Long getProfessionalId() {
        return professionalId;
    }
}

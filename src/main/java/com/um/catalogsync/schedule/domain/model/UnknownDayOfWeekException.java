package com.um.catalogsync.schedule.domain.model;

/**
 * Llego un {@code dayOfWeek} que no es uno de los siete del contrato.
 *
 * <p><b>Por que vive en {@code domain/model} y no en
 * {@code application/exception}</b>, donde estan las demas excepciones de
 * los slices. Porque la lanza {@link DayOfWeek#fromContract(String)}, que
 * es dominio, y la regla de dependencias del proyecto es que {@code domain}
 * no depende de nada: ni de Spring, ni de JPA, ni de {@code application}.
 * Poner esta clase en {@code application/exception} haria que el dominio
 * importara la capa de arriba, que es exactamente lo que la regla prohibe.
 *
 * <p>La asimetria con {@code UnknownProfessionalException} es intencional y
 * tiene una causa concreta: esa la lanza el adaptador de persistencia, que
 * es {@code infrastructure} y si puede ver {@code application}. Esta la
 * lanza el propio modelo, que no puede ver nada.
 *
 * <p>Es una excepcion con nombre y no un {@code IllegalArgumentException}
 * para que la sincronizacion pueda distinguir "el contrato trajo un dia que
 * no conozco" —una inconsistencia que se registra y que impide avanzar la
 * version local— de un error de programacion cualquiera.
 */
public class UnknownDayOfWeekException extends RuntimeException {

    private final String valorRecibido;

    public UnknownDayOfWeekException(String valorRecibido) {
        super("El dia de la semana " + describir(valorRecibido)
                + " no es uno de los siete del contrato");
        this.valorRecibido = valorRecibido;
    }

    /** El valor tal como llego, {@code null} incluido, para diagnosticar. */
    public String getValorRecibido() {
        return valorRecibido;
    }

    /** Entrecomilla el valor para que un vacio o un espacio se vean. */
    private static String describir(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }
}

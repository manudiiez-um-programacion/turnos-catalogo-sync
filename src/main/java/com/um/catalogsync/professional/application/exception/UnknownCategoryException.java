package com.um.catalogsync.professional.application.exception;

/**
 * Se intento guardar un profesional cuya categoria no esta en la copia
 * local.
 *
 * <p><b>La decision:</b> se rechaza. No se acepta el profesional con la
 * categoria colgando ni se lo marca como pendiente de resolver.
 *
 * <p>Por que rechazar:
 *
 * <ul>
 *   <li>Para datos validos nunca pasa. Los cambios de una version se
 *       aplican en una sola unidad de trabajo, asi que si la categoria
 *       llega en la misma version que el profesional basta con aplicar
 *       primero las categorias. Ese orden lo pone quien orquesta (#13 y
 *       #25) y hace falta igual para los borrados, porque las FK son
 *       RESTRICT en los dos sentidos.
 *   <li>Cuando si pasa, es una inconsistencia real, y fallar es lo que
 *       corresponde: la version local no avanza, que es la regla no
 *       negociable del proyecto, y eso deriva en la deteccion de
 *       discontinuidad y la reconstruccion por snapshot (#26).
 *   <li>"Aceptar y marcar pendiente" obligaria a inventar una columna de
 *       estado que el contrato no tiene, y dejaria la copia local
 *       observablemente inconsistente: la busqueda podria devolver un
 *       profesional cuya categoria no existe. Para un modelo de lectura
 *       que alimenta la busqueda y el contrato de agenda, eso es peor que
 *       un error ruidoso.
 * </ul>
 *
 * <p>Se considero hacer la FK {@code DEFERRABLE INITIALLY DEFERRED}, que
 * haria irrelevante el orden dentro de una transaccion. Se descarto: el
 * orden sigue siendo necesario para los borrados, asi que resolveria la
 * mitad del problema, y moveria el error al COMMIT, donde es mas dificil
 * de atribuir a una fila concreta.
 *
 * <p>Esta excepcion existe para que el error llegue con sentido de dominio.
 * Sin ella, quien sincroniza recibiria una violacion de integridad generica
 * y tendria que interpretar un mensaje de PostgreSQL para saber que paso.
 */
public class UnknownCategoryException extends RuntimeException {

    private final Long categoryId;

    public UnknownCategoryException(Long categoryId, Throwable cause) {
        super("No se puede guardar un profesional de la categoria " + categoryId
                + ": esa categoria no esta en la copia local", cause);
        this.categoryId = categoryId;
    }

    public Long getCategoryId() {
        return categoryId;
    }
}

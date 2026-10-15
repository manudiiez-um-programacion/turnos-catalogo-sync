package com.um.catalogsync.sync.application.exception;

/**
 * Dos sincronizaciones intentaron asentar la <b>primera</b> version al
 * mismo tiempo y esta es la que perdio.
 *
 * <p>Solo puede pasar sobre una base recien inicializada. Cuando ya hay una
 * fila, el adaptador la bloquea antes de decidir y los avances
 * concurrentes se serializan sin que ninguno falle; pero una fila que
 * todavia no existe no se puede bloquear, asi que las dos transacciones
 * llegan a insertar y la clave primaria deja pasar una sola.
 *
 * <p><b>Perder esta carrera es seguro</b>, y por eso se la deja fallar en
 * lugar de reintentar por dentro. La sincronizacion que pierde no avanza su
 * version, su transaccion se deshace completa —datos incluidos— y queda
 * registrada como corrida fallida. La que gana dejo la copia local y la
 * version consistentes entre si. Reintentar automaticamente escondería que
 * dos sincronizaciones completas corrieron en paralelo, que es algo que
 * conviene ver.
 */
public class ConcurrentVersionAdvanceException extends RuntimeException {

    private final long versionPedida;

    public ConcurrentVersionAdvanceException(long versionPedida, Throwable cause) {
        super("Otra sincronizacion asento la primera version de catalogo al mismo tiempo; "
                + "no se pudo asentar la version " + versionPedida, cause);
        this.versionPedida = versionPedida;
    }

    public long getVersionPedida() {
        return versionPedida;
    }
}

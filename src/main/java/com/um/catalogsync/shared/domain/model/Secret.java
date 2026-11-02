package com.um.catalogsync.shared.domain.model;

import java.util.Objects;

/**
 * Un valor que no debe aparecer nunca escrito: el JWT tecnico de la catedra
 * y {@code redisPassword} (§5.1, §5.3).
 *
 * <p><b>Para que existe un tipo en lugar de un {@code String}.</b> La regla
 * del proyecto es que los secretos no aparezcan en logs, en respuestas HTTP
 * ni en el mensaje de ninguna excepcion. Con un {@code String} esa regla
 * depende de que nadie se olvide: basta un {@code log.debug("config: " +
 * config)} o un objeto devuelto por un controlador para filtrarlo, y el
 * descuido no se nota hasta que el secreto ya esta en un archivo de log.
 * Envolviendolo en un tipo cuyo {@link #toString()} devuelve una mascara, el
 * descuido deja de ser posible: el camino facil —interpolar, loguear,
 * serializar— imprime {@value #MASCARA}, y para llegar al valor real hay que
 * pedirlo explicitamente con {@link #value()}, que es visible en revision de
 * codigo y facil de buscar.
 *
 * <p><b>Por que es una clase final y no un {@code record}.</b> Un record
 * genera {@code toString()} con todos sus componentes, que es justo lo que
 * hay que evitar: habria que sobrescribirlo igual. Y genera un accesor
 * {@code value()} que Jackson <i>si</i> detecta como propiedad en los
 * records, asi que un secreto dentro de un objeto serializado terminaria en
 * el JSON. En una clase comun, un metodo {@code value()} sin prefijo
 * {@code get} no es una propiedad para Jackson: si alguien devolviera por
 * HTTP un objeto que contiene un {@code Secret}, el JSON no lleva el valor.
 * Las dos protecciones tienen una prueba que las fija.
 *
 * <p>Vive en {@code shared} porque no es de ningun slice: lo usan la
 * credencial tecnica y la configuracion de Redis, y lo va a usar cualquier
 * secreto que aparezca despues. Java puro, sin framework.
 */
public final class Secret {

    /** Lo que se ve en cualquier representacion textual. */
    public static final String MASCARA = "***";

    private final String valor;

    private Secret(String valor) {
        this.valor = valor;
    }

    /**
     * @param valor el secreto, no vacio
     * @throws IllegalArgumentException si viene {@code null} o en blanco. Un
     *     secreto vacio no es un secreto: si la catedra lo mandara asi,
     *     queremos enterarnos al construir el objeto y no mas tarde, cuando
     *     Redis rechace la conexion sin explicar por que.
     */
    public static Secret of(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Un secreto no puede ser nulo ni vacio");
        }
        return new Secret(valor);
    }

    /**
     * Igual que {@link #of(String)} pero tolera la ausencia.
     *
     * <p>Hace falta por el contrato: {@code redisPassword} solo se entrega
     * cuando {@code provisioningStatus} es {@code PROVISIONED} (§5.1), asi
     * que en los demas estados su ausencia es lo esperado y no un error de
     * parseo. Quien decide si esa ausencia es aceptable es
     * {@code IntegrationConfig}, que sabe en que estado esta la integracion.
     *
     * @return el secreto, o {@code null} si no vino
     */
    public static Secret ofNullable(String valor) {
        return valor == null || valor.isBlank() ? null : new Secret(valor);
    }

    /**
     * El valor real, para entregarselo al cliente que lo necesita.
     *
     * <p>Toda llamada a este metodo es un punto donde el secreto sale del
     * envoltorio. Deberia haber pocas y todas con destino claro: la cabecera
     * {@code Authorization} y la conexion a Redis. Si aparece en un
     * {@code log}, en un mensaje de excepcion o en un DTO de respuesta, es
     * un error.
     */
    public String value() {
        return valor;
    }

    /** Siempre la mascara, nunca el valor. Es el punto de toda la clase. */
    @Override
    public String toString() {
        return MASCARA;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Secret secret && Objects.equals(valor, secret.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(valor);
    }
}

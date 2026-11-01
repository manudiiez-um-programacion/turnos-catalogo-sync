package com.um.catalogsync.integration.application.exception;

/**
 * La catedra respondio 401 a una llamada que llevaba el JWT tecnico: el
 * token no sirve.
 *
 * <p>Distinta de {@link TechnicalAuthenticationFailedException}, que es el
 * rechazo de las credenciales al loguearse. La diferencia no es cosmetica,
 * decide que hacer: si el rechazado es el <i>token</i>, reautenticarse
 * puede resolverlo y vale intentarlo una vez; si las rechazadas son las
 * <i>credenciales</i>, reintentar es inutil y lo unico que haria es repetir
 * el error.
 *
 * <p>Tampoco encadena la causa, por el mismo motivo que la otra: el mensaje
 * de la excepcion HTTP arrastra el cuerpo de la respuesta, y la cabecera del
 * pedido llevaba el JWT.
 */
public class TechnicalTokenRejectedException extends RuntimeException {

    public TechnicalTokenRejectedException(String ruta) {
        super("La catedra rechazo el JWT tecnico al llamar a " + ruta
                + ": el token esta vencido o revocado");
    }
}

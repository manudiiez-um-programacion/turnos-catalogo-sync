package com.um.catalogsync.integration.application.exception;

/**
 * La catedra rechazo las credenciales de la cuenta tecnica con 401 (§5.2).
 *
 * <p><b>No encadena la excepcion HTTP que la origino, y es a proposito.</b>
 * El cuerpo de la respuesta de un 401 entra en el mensaje de
 * {@code HttpClientErrorException}, y lo que se envio en ese pedido incluye
 * la contraseña tecnica. Encadenar la causa pondria ese cuerpo —y cualquier
 * eco que el servidor haga del pedido— en la traza, que es exactamente lo
 * que la regla de secretos prohibe. Se pierde algo de detalle para
 * diagnosticar y se gana la garantia; para un 401 el detalle sobra, porque
 * la causa es siempre la misma: usuario o contraseña equivocados.
 *
 * <p>Las fallas de transporte si encadenan la causa: ahi no hay cuerpo de
 * respuesta, solo un error de conexion. Ver
 * {@link CatedraIntegrationUnavailableException}.
 */
public class TechnicalAuthenticationFailedException extends RuntimeException {

    public TechnicalAuthenticationFailedException(String usuario) {
        super("La catedra rechazo las credenciales de la cuenta tecnica \"" + usuario + "\":"
                + " revisar CATEDRA_TECHNICAL_USERNAME y CATEDRA_TECHNICAL_PASSWORD en .env."
                + " Si la cuenta todavia no existe, hay que darla de alta una unica vez"
                + " con Postman contra POST /api/student/register (§5.1)");
    }
}

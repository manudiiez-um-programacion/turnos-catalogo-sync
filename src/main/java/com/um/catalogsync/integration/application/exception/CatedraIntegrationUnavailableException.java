package com.um.catalogsync.integration.application.exception;

/**
 * No se pudo hablar con la API de la catedra, o respondio algo que el
 * contrato no prevee.
 *
 * <p>Es la excepcion de los problemas de infraestructura, no de los de
 * credenciales: el servidor no esta en internet y se llega por una red
 * privada ZeroTier (D6), asi que la causa mas comun de verla es no estar en
 * la red o que la IP haya cambiado. El mensaje lo dice, porque es el primer
 * lugar donde mirar.
 *
 * <p><b>Esta si encadena la causa.</b> Una falla de conexion o un 5xx no
 * tienen un cuerpo que pueda contener el secreto que mandamos: la traza
 * aporta el detalle que hace falta —timeout, host inalcanzable, codigo de
 * estado— sin arrastrar nada sensible.
 */
public class CatedraIntegrationUnavailableException extends RuntimeException {

    public CatedraIntegrationUnavailableException(String ruta, Throwable causa) {
        super("No se pudo completar " + ruta + " contra la API de la catedra."
                + " El servidor se alcanza solo por la red privada ZeroTier:"
                + " verificar que la maquina este unida a la red y que"
                + " CATEDRA_API_BASE_URL apunte a la IP asignada, sin /api al final",
                causa);
    }

    public CatedraIntegrationUnavailableException(String mensaje) {
        super(mensaje);
    }
}

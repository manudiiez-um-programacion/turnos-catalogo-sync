package com.um.catalogsync.sync.application.exception;

/**
 * La catedra respondio correctamente pero el snapshot no cumple el contrato
 * de §7.
 *
 * <p><b>Por que fallar en lugar de aplicar lo que llego.</b> Este snapshot
 * va a reemplazar la copia local entera (#13). Un campo obligatorio ausente
 * terminaria en una violacion de {@code NOT NULL} al insertar —las tres
 * tablas declaran {@code NOT NULL} todo salvo {@code description}— y el
 * error aparecería lejos de la causa, sin decir que fila ni que campo. Peor
 * todavia es una coleccion ausente: tratarla como vacia borraria todas las
 * filas de esa tabla en silencio. Asi que se corta en el borde, donde
 * todavia se puede nombrar exactamente lo que falta.
 *
 * <p>El mensaje nombra el campo con su ruta en el JSON
 * —{@code professionals[15].enabled}— y no un indice de la lista: el id de
 * la catedra es lo que sirve para ir a mirar el dato del otro lado.
 *
 * <p><b>No incluye el cuerpo de la respuesta.</b> Nombrar el campo ya
 * localiza el problema, y volcar el JSON entero en una traza seria arrastrar
 * el catalogo completo a los logs por un campo faltante.
 */
public class IncompleteCatalogSnapshotException extends RuntimeException {

    public IncompleteCatalogSnapshotException(String detalle) {
        super("El snapshot de la catedra no cumple el contrato de §7: " + detalle);
    }
}

package com.um.catalogsync.integration.domain.model;

/**
 * Estado de aprovisionamiento de la cuenta tecnica, tal como lo informa la
 * catedra (§5.1).
 *
 * <p>Importa porque condiciona todo lo demas: solo con {@code PROVISIONED}
 * se entrega {@code redisPassword}, y sin esa clave este servicio no puede
 * leer los cambios incrementales. Por eso el arranque se niega a seguir en
 * cualquier otro estado, en lugar de arrancar y fallar mas tarde con un
 * error de autenticacion de Redis que no explicaria la causa real.
 */
public enum ProvisioningStatus {

    /** El alta se registro pero la catedra todavia no termino de habilitarla. */
    PENDING,

    /** Habilitada. Es el unico estado en el que se entrega {@code redisPassword}. */
    PROVISIONED,

    /** El aprovisionamiento fallo del lado de la catedra. */
    FAILED,

    /** La cuenta fue dada de baja. Hay que crear otra (§5.4). */
    REVOKED;

    /**
     * Traduce el valor que vino en el JSON.
     *
     * <p><b>Un valor desconocido es un error y no un estado nuevo.</b> La
     * tentacion seria agregar un {@code UNKNOWN} y seguir, pero este enum
     * decide si el servicio arranca: tratar lo desconocido como "algo que no
     * es PROVISIONED" y parar es lo correcto, y la excepcion con nombre deja
     * el motivo escrito. Si la catedra agrega un estado nuevo, esto falla
     * ruidosamente al arrancar —que es cuando se puede actuar— en vez de
     * convertirse en un comportamiento raro meses despues.
     *
     * @param valor el texto del contrato, sensible a mayusculas segun §5.1
     * @throws UnknownProvisioningStatusException si no es uno de los cuatro
     */
    public static ProvisioningStatus fromContract(String valor) {
        if (valor == null) {
            throw new UnknownProvisioningStatusException(null);
        }
        for (ProvisioningStatus estado : values()) {
            if (estado.name().equals(valor)) {
                return estado;
            }
        }
        throw new UnknownProvisioningStatusException(valor);
    }
}

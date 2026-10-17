package com.um.catalogsync.sync.application.exception;

/**
 * Se intento asentar una version anterior a la vigente por un camino que no
 * lo permite.
 *
 * <p>El nombre no habla de "rollback" a proposito, para no confundirlo con
 * el rollback de una transaccion: lo que se rechaza es que el <i>numero</i>
 * de version retroceda.
 *
 * <p>El unico camino valido para retroceder es la reconstruccion por
 * snapshot. El motivo completo —por que SNAPSHOT si y INCREMENTAL no—
 * esta en {@code CatalogVersion.allowsAdvanceTo}, que es donde vive la
 * regla; aca no se repite para que no haya dos versiones de la misma
 * explicacion que puedan quedar desincronizadas.
 *
 * <p>Que esto falle es lo correcto y no un inconveniente: si una
 * sincronizacion incremental pide retroceder, lo que hay es un evento
 * reordenado o un bug, y la version local tiene que quedarse donde esta
 * hasta que una sincronizacion completa reconstruya la copia.
 */
public class VersionRegressionException extends RuntimeException {

    private final long versionVigente;
    private final long versionPedida;

    public VersionRegressionException(long versionVigente, long versionPedida) {
        super("No se puede retroceder la version local de " + versionVigente
                + " a " + versionPedida
                + " por sincronizacion incremental: solo un snapshot puede hacerlo");
        this.versionVigente = versionVigente;
        this.versionPedida = versionPedida;
    }

    public long getVersionVigente() {
        return versionVigente;
    }

    public long getVersionPedida() {
        return versionPedida;
    }
}

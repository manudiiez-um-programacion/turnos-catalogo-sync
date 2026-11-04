package com.um.catalogsync.sync.infrastructure.reconciliation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cada cuanto se compara la version local con la que publica la catedra.
 *
 * <p><b>Por que existe este chequeo.</b> §16 lo dice directo: Kafka es una
 * notificacion y no la fuente del catalogo, y si una notificacion se pierde,
 * la comparacion de versiones tiene que permitir detectar y recuperar el
 * atraso. Depender solo del consumidor significaria que un mensaje perdido
 * —o una sincronizacion que fallo despues de registrar el evento— deja la
 * copia local atrasada para siempre.
 *
 * <p><b>Que valor poner.</b> El intervalo es el peor caso de cuanto puede
 * tardar en detectarse un atraso que Kafka no aviso. Cinco minutos es un
 * compromiso: suficientemente seguido para que un aviso perdido no se note,
 * y suficientemente espaciado para que el chequeo no sea una lectura
 * constante contra el Redis de la catedra. Cuando no hay nada que hacer, el
 * costo es una sola lectura de la metadata y ningun log.
 *
 * @param enabled si el chequeo corre. Se apaga en pruebas y sirve para
 *     levantar el servicio sin la red privada sin que el log se llene
 * @param interval cada cuanto se compara
 * @param initialDelay cuanto se espera antes del primer chequeo. No arranca
 *     en cero a proposito: el arranque ya tiene su propia reconciliacion, y
 *     dos pedidos simultaneos solo lograrian que uno se encuentre el lock
 *     tomado
 */
@ConfigurationProperties(prefix = "sync.reconciliation")
public record CatalogReconciliationProperties(
        boolean enabled, Duration interval, Duration initialDelay) {

    public CatalogReconciliationProperties {
        exigirPositivo(interval, "sync.reconciliation.interval");
        exigirPositivo(initialDelay, "sync.reconciliation.initial-delay");
    }

    private static void exigirPositivo(Duration valor, String propiedad) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException(
                    propiedad + " tiene que ser una duracion positiva, y llego \"" + valor
                            + "\". En cero el chequeo correria sin pausa y la catedra veria"
                            + " una lectura continua");
        }
    }
}

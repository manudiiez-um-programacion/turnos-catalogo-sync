package com.um.catalogsync.sync.infrastructure.health;

import com.um.catalogsync.sync.application.service.CatalogVersionService;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Dice en {@code /actuator/health} si la copia local esta servible o
 * degradada.
 *
 * <p>Existe por el criterio de #14 que pide que, cuando la catedra no
 * responde, el servicio "informe estado degradado" en lugar de reiniciarse
 * en ciclo. El runner de arranque ya evita el ciclo; esta clase es la mitad
 * que lo hace observable desde afuera sin tener que leer el log.
 *
 * <p><b>Lo que informa es un hecho, no un recuerdo.</b> Lee la version
 * aplicada cada vez en lugar de guardar como salio la sincronizacion de
 * arranque. Asi la respuesta sigue siendo cierta cuando la sincronizacion
 * que fallo al arrancar termina bien mas tarde, por reintento o por evento,
 * y no hace falta acordarse de actualizar ningun estado en memoria.
 *
 * <p><b>Por que nunca reporta DOWN.</b> Seria lo primero que uno escribe, y
 * rompe dos cosas a la vez. El healthcheck del compose exige
 * {@code "status":"UP"} para dar el contenedor por sano, y dentro del
 * contenedor la catedra es inalcanzable por el conflicto de subred con
 * {@code docker0} (D6): el arranque sin catalogo no es una anomalia ahi, es
 * lo esperado hasta que el enrutamiento este resuelto. Un DOWN dejaria
 * {@code docker compose up} en "unhealthy" para siempre, contra la regla no
 * negociable de que todo levanta con Compose. Y, mas de fondo: el servicio
 * sin catalogo <i>funciona</i> —atiende, valida tokens, responde vacio y
 * puede sincronizar cuando la catedra vuelva—, asi que declararlo caido
 * seria falso. El dato va en el detalle, que es donde se lee sin que nadie
 * lo confunda con una caida.
 *
 * <p><b>Que no hace: el estado de sincronizacion.</b> Las corridas, sus
 * errores y sus codigos son de #28, que tiene su propio endpoint y no un
 * campo de health. Aca va una sola pregunta, la que decide si vale la pena
 * consultar este servicio: hay catalogo o no.
 *
 * <p>El detalle solo se publica donde esta configurado mostrarlo: el perfil
 * base deja {@code show-details: never} y es {@code local} el que lo abre.
 * Fuera de local, {@code /actuator/health} —que es la unica ruta publica sin
 * token— sigue respondiendo el {@code UP} pelado y no cuenta nada.
 */
@Component("catalogSync")
@RequiredArgsConstructor
public class CatalogSyncHealthIndicator implements HealthIndicator {

    /** Hay catalogo local y se puede responder busquedas con el. */
    static final String SINCRONIZADO = "SINCRONIZADO";

    /** No hay version aplicada: la copia local todavia no sirve. */
    static final String SIN_CATALOGO = "SIN_CATALOGO";

    private final CatalogVersionService versiones;

    @Override
    public Health health() {
        Optional<CatalogVersion> aplicada = versiones.getCurrent();
        if (aplicada.isEmpty()) {
            return Health.up()
                    .withDetail("estado", SIN_CATALOGO)
                    .withDetail(
                            "detalle",
                            "No se aplico ningun catalogo todavia. Las busquedas responden"
                                    + " vacio hasta que una sincronizacion completa termine"
                                    + " bien.")
                    .build();
        }
        CatalogVersion version = aplicada.get();
        return Health.up()
                .withDetail("estado", SINCRONIZADO)
                .withDetail("catalogVersion", version.getVersion())
                .withDetail("appliedAt", version.getAppliedAt())
                .withDetail("source", version.getSource())
                .build();
    }
}

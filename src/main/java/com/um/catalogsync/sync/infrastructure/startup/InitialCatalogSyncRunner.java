package com.um.catalogsync.sync.infrastructure.startup;

import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.application.service.CatalogVersionService;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import java.util.Optional;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pone la copia local al dia cuando el servicio arranca (§6.1, §8).
 *
 * <p>Es el componente que decide <i>cuando</i> sincronizar al arrancar. El
 * que sabe <i>como</i> —y si corresponde una completa o una incremental— es
 * {@code CatalogSyncService.synchronize}, y por eso aca no aparecen ni
 * {@code CatalogSnapshotPort} ni ningun repositorio: esta clase elige el
 * momento y delega la decision.
 *
 * <p><b>Reconcilia siempre, no solo con la base vacia.</b> Hasta #26 este
 * runner miraba si habia version local y, si la habia, no hacia nada. Eso
 * dejaba un agujero: un servicio que estuvo apagado un rato vuelve con una
 * version local vieja y ninguna notificacion de Kafka que le avise del
 * atraso —los avisos que se publicaron mientras estaba caido no se le
 * reenvian—. §8 pide comportamiento correcto ante reinicios con operaciones
 * pendientes, y §16 pide que la comparacion de versiones detecte el atraso.
 * Asi que ahora el arranque compara y sincroniza lo que haga falta: la base
 * vacia es un caso particular de esa comparacion, el que termina en
 * {@code NO_LOCAL_VERSION}.
 *
 * <p><b>Por que no bloquea el arranque.</b> El trabajo se lanza en un hilo
 * aparte y {@link #run(ApplicationArguments)} vuelve enseguida. Traer el
 * snapshot es una llamada REST por ZeroTier, con ~228 ms de ida y vuelta
 * medidos (D6) y un {@code read-timeout} de 20 s: hacerla en el hilo
 * principal atrasaria el {@code ApplicationReadyEvent} y, con el, la
 * se&ntilde;al de "listo" que lee cualquier orquestador. Hacerla aparte deja
 * el servicio aceptando peticiones desde el primer segundo, que es lo que
 * corresponde: un catalogo vacio se responde como catalogo vacio, no como
 * un servicio caido.
 *
 * <p>Es la diferencia deliberada con
 * {@code IntegrationConfigStartupVerifier}, que si es sincrono y si detiene
 * el arranque. No se contradicen: sin configuracion tecnica no hay nada que
 * este servicio pueda hacer nunca, mientras que sin catalogo todavia puede
 * atender, informar su estado y sincronizar mas tarde.
 *
 * <p><b>Por que una falla no tumba el servicio.</b> El escenario mas
 * probable —la catedra inalcanzable, porque solo existe dentro de la red
 * privada (D6)— no es un error de programacion ni de configuracion: es el
 * estado normal mientras el enrutamiento no este resuelto. Propagar la
 * excepcion mataria el proceso y, con {@code restart: unless-stopped} en el
 * compose, lo dejaria reiniciandose en ciclo contra un servidor que no va a
 * contestar. La falla ya quedo registrada en {@code sync_run} y
 * {@code sync_error} por la propia corrida, el estado se puede consultar en
 * {@code /actuator/health} y #28 va a exponerlo en detalle: no hace falta
 * ademas tirar el servicio abajo para que se note.
 *
 * <p><b>Un reinicio a mitad de la primera sincronizacion no deja nada roto
 * y vuelve a intentar.</b> No porque esta clase haga algo al respecto, sino
 * porque no hay nada que limpiar: aplicar el snapshot es una unica unidad de
 * trabajo (#13), asi que una caida en medio se va entera en el rollback y la
 * version local no llega a existir. Al volver a arrancar, {@code findCurrent}
 * sigue vacio y este runner pide el snapshot otra vez. Lo unico que
 * sobrevive es la fila de {@code sync_run} en {@code RUNNING}, que es
 * informacion buscada —dice que el servicio se cayo sincronizando— y no un
 * resto a barrer.
 *
 * <p><b>Por que se puede apagar.</b> Cada {@code @SpringBootTest} levanta el
 * contexto completo; con esto encendido, todos intentarian sincronizar
 * contra un servidor inalcanzable y las pruebas de integracion dejarian de
 * controlar cuando corre la sincronizacion, que es justamente lo que #21
 * necesita poder decidir. El perfil {@code test} lo apaga. El valor por
 * defecto es {@code true}: olvidarse de configurarlo deja el arranque
 * sincronizando, que es el lado seguro del olvido.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "sync.startup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InitialCatalogSyncRunner implements ApplicationRunner {

    private final CatalogVersionService versiones;
    private final CatalogSyncService sincronizacion;

    /**
     * Donde corre el trabajo.
     *
     * <p>Es un campo y no una llamada directa a {@code Thread.ofVirtual()}
     * para poder observar en una prueba que {@link #run(ApplicationArguments)}
     * delega en lugar de trabajar. Sin esta costura, "no bloquea el arranque"
     * solo se podria comprobar midiendo tiempos, que es la clase de prueba
     * que falla sola en una maquina cargada.
     */
    private final Executor hilo;

    @Autowired
    public InitialCatalogSyncRunner(
            CatalogVersionService versiones, CatalogSyncService sincronizacion) {
        // Hilo virtual: no hay pool que configurar ni que apagar, y como
        // siempre es daemon, un shutdown en medio de la sincronizacion no
        // queda esperandolo. La transaccion que estuviera abierta se va en
        // el rollback, que es el comportamiento que ya se describio arriba.
        this(
                versiones,
                sincronizacion,
                tarea -> Thread.ofVirtual().name("sincronizacion-inicial").start(tarea));
    }

    InitialCatalogSyncRunner(
            CatalogVersionService versiones, CatalogSyncService sincronizacion, Executor hilo) {
        this.versiones = versiones;
        this.sincronizacion = sincronizacion;
        this.hilo = hilo;
    }

    @Override
    public void run(ApplicationArguments args) {
        hilo.execute(this::sincronizarSiHaceFalta);
    }

    /**
     * Compara el estado local con el del origen y sincroniza lo que haga
     * falta.
     *
     * <p>Visible para las pruebas, que la llaman directo: lo que hay que
     * fijar es la decision y el manejo de la falla, no el hilo donde
     * ocurren.
     *
     * <p>La version local se lee solo para el log: la decision la toma
     * {@code synchronize}, que la vuelve a leer. Leerla dos veces no es un
     * problema —entre las dos lecturas nada la cambio, porque el arranque es
     * el unico que sincroniza en ese momento— y tener el numero a mano hace
     * que el log diga desde donde se partio.
     */
    void sincronizarSiHaceFalta() {
        Optional<CatalogVersion> aplicada = versiones.getCurrent();
        log.info(aplicada.isPresent()
                        ? "Catalogo local en la version {}: se verifica si hay atraso"
                        : "Sin catalogo local: se pide el catalogo completo a la catedra",
                aplicada.map(CatalogVersion::getVersion).orElse(null));

        try {
            sincronizacion.synchronize(SyncTrigger.STARTUP)
                    .ifPresentOrElse(
                            corrida -> log.info(
                                    "Arranque sincronizado: corrida {} ({}, {}) → version {}",
                                    corrida.getId(), corrida.getMode(),
                                    corrida.getDecisionReason(), corrida.getToVersion()),
                            () -> log.info("El catalogo local ya estaba al dia"));
        } catch (RuntimeException falla) {
            // No se relanza a proposito: ver el javadoc de la clase. La
            // corrida fallida ya quedo registrada, asi que aca no se pierde
            // nada mas que el proceso. Y el atraso que no se recupero ahora
            // lo vuelve a intentar la reconciliacion periodica (#27).
            log.warn(
                    "No se pudo sincronizar al arrancar. El servicio queda arriba con el"
                            + " catalogo que tenga: si no tenia ninguno, las busquedas no"
                            + " van a devolver resultados hasta que una sincronizacion"
                            + " termine bien. El motivo quedo en sync_run y sync_error, y"
                            + " el chequeo periodico va a reintentar.",
                    falla);
        }
    }
}

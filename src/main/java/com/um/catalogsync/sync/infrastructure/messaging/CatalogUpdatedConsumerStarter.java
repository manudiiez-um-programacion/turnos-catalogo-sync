package com.um.catalogsync.sync.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantiene al consumidor enganchado al topic.
 *
 * <p><b>Por que existe en lugar de arrancar el consumidor al crear el
 * bean.</b> Arrancar requiere la configuracion de integracion, y pedirla
 * dispara un login contra la catedra. Si eso pasara al construir el bean, el
 * contexto no levantaria cuando la red privada no esta (D6) y el servicio
 * entraria en ciclo de reinicios por algo que no es su culpa. Acá el
 * arranque del servicio no depende de nada externo: el primer intento ocurre
 * despues, y si falla hay otro.
 *
 * <p><b>Por que un intervalo y no un reintento con backoff.</b> Lo que se
 * espera no es una operacion que falla, es una condicion que todavia no se
 * da: la cuenta tecnica puede no estar dada de alta, o la red puede estar
 * caida. No hay nada que acelerar reintentando antes, y un intervalo fijo es
 * mas facil de entender en los logs que una progresion. Los reintentos con
 * backoff son los del mensaje individual, que si son de una operacion que
 * fallo.
 *
 * <p>Tambien cubre el caso de que el consumidor se haya detenido solo: ante
 * reintentos agotados, {@code CatalogUpdatedConsumer} se detiene a proposito
 * sin confirmar el offset, y este reintento es el que lo vuelve a levantar
 * para que Kafka reentregue el mensaje.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogUpdatedConsumerStarter {

    private final CatalogUpdatedConsumer consumidor;

    /**
     * Intenta enganchar el consumidor, la primera vez y cada tanto.
     *
     * <p>El primer disparo ocurre apenas el contexto quedo listo
     * ({@code initialDelay = 0}), asi que en condiciones normales el
     * consumidor esta escuchando desde el arranque y el intervalo nunca
     * hace falta.
     *
     * <p>Las fallas se tragan a proposito: este metodo lo invoca el
     * planificador y no hay nadie a quien propagarle nada. Lo que no se
     * traga es la informacion —queda un WARN con la causa— y el proximo
     * disparo vuelve a intentar.
     */
    @Scheduled(
            initialDelay = 0,
            fixedDelayString = "${catedra.kafka.startup-retry-interval}")
    public void mantenerEnganchado() {
        try {
            consumidor.arrancarSiHaceFalta();
        } catch (RuntimeException noSePudo) {
            log.warn("Todavia no se pudo enganchar el consumidor del topic de catalogo: {}."
                            + " Se reintenta en el proximo ciclo",
                    noSePudo.getMessage());
            log.debug("Detalle del intento fallido", noSePudo);
        }
    }
}

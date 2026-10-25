package com.um.catalogsync.integration.application.session;

import com.um.catalogsync.integration.application.exception.IncompleteIntegrationConfigException;
import com.um.catalogsync.integration.application.exception.IntegrationNotProvisionedException;
import com.um.catalogsync.integration.application.exception.TechnicalTokenRejectedException;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.TechnicalSession;
import com.um.catalogsync.integration.domain.ports.out.IntegrationConfigPort;
import com.um.catalogsync.integration.domain.ports.out.TechnicalAuthPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * La sesion tecnica vigente, viva solo en memoria.
 *
 * <p><b>Por que en memoria y en ningun otro lado.</b> El JWT tecnico no se
 * persiste en la base ni en Redis. No es una optimizacion, es la regla: la
 * base la administra este servicio pero no es un lugar seguro para una
 * credencial de un año, y Redis es infraestructura de la catedra, donde el
 * namespace de escritura es visible para la propia catedra (§14.6).
 * Guardarlo solo en memoria tiene un costo aceptado —cada reinicio del
 * proceso vuelve a loguearse, un viaje de ~228 ms (D6)— y evita que el
 * secreto quede escrito en algun lado.
 *
 * <p><b>Por que hay cache y no se pide el token en cada llamada.</b> Un
 * login por cada consulta a la catedra multiplicaria los viajes sobre un
 * enlace lento, y el contrato entrega un token valido un año justamente para
 * que se reuse (§4).
 *
 * <p><b>Por que vive en {@code application} y no detras de un puerto.</b>
 * Un puerto de salida seria la forma habitual de modelar "donde se guarda
 * algo", y aca es lo que no se quiere: el punto es que este estado no tenga
 * un adaptador intercambiable, porque el unico almacenamiento aceptable es
 * la memoria del proceso. Un puerto {@code TechnicalSessionStore} invitaria
 * a escribir manana el adaptador que lo guarda en la base, que es
 * exactamente lo prohibido. Es un colaborador de los casos de uso, no una
 * dependencia del dominio, asi que el paquete {@code session/} se suma a los
 * tres que describe la skill.
 *
 * <p>Nada de lo que esta clase guarda sale de ella sin pasar por
 * {@link #validar(IntegrationConfig)}: una configuracion que no esta
 * {@code PROVISIONED} o a la que le faltan campos nunca queda cacheada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechnicalSessionCache {

    private final TechnicalAuthPort technicalAuth;
    private final IntegrationConfigPort integrationConfig;

    /**
     * La sesion vigente, o {@code null} si todavia no se autentico.
     *
     * <p>{@code AtomicReference} y no un campo comun: la lectura ocurre sin
     * tomar el lock —es el camino caliente, lo que hace el cliente del
     * snapshot en cada llamada— y necesita ver una publicacion segura del
     * valor que escribio otro hilo. Las escrituras si se serializan con
     * {@code synchronized}, que es lo que evita dos logins simultaneos.
     */
    private final AtomicReference<TechnicalSession> vigente = new AtomicReference<>();

    /**
     * La sesion vigente, autenticandose la primera vez.
     *
     * <p>Sin lock en el caso normal: si ya hay sesion, se devuelve y listo.
     */
    public TechnicalSession current() {
        TechnicalSession actual = vigente.get();
        return actual != null ? actual : autenticarSiSigueVacia();
    }

    /**
     * Se autentica de nuevo y reemplaza la sesion, haya o no una.
     *
     * <p>Es el camino del token rechazado y el de la primera carga.
     */
    public synchronized TechnicalSession renew() {
        TechnicalSession nueva = technicalAuth.authenticate();
        return guardar(nueva);
    }

    /**
     * Relee la configuracion con el token que ya se tiene y la reemplaza.
     *
     * <p>Si la catedra rechaza el token, se reautentica una vez y devuelve la
     * configuracion que vino con el login nuevo. <b>Una sola vez, no un
     * reintento en bucle:</b> si el token recien emitido tambien fuera
     * rechazado, el problema no es el token y repetir solo gastaria viajes.
     * La excepcion de credenciales rechazadas sale sin atrapar, que es lo
     * correcto: no hay nada que reintentar.
     */
    public synchronized IntegrationConfig refreshConfig() {
        TechnicalSession actual = vigente.get();
        if (actual == null) {
            return renew().getIntegration();
        }
        try {
            IntegrationConfig releida = integrationConfig.fetchCurrent(actual.getTechnicalToken());
            validar(releida);
            vigente.set(actual.withIntegration(releida));
            log.info("Configuracion de integracion refrescada: {}", releida);
            return releida;
        } catch (TechnicalTokenRejectedException tokenVencido) {
            log.info("El JWT tecnico fue rechazado al refrescar; reautenticando");
            return renew().getIntegration();
        }
    }

    /**
     * Segundo chequeo bajo el lock.
     *
     * <p>Hace falta porque {@link #current()} lee sin lock: dos hilos pueden
     * ver {@code null} a la vez y los dos entrar aca. El que llega primero
     * autentica; el segundo encuentra la sesion ya guardada y la devuelve sin
     * repetir el login. Sin este chequeo, el arranque podria disparar dos
     * logins contra la catedra por nada.
     */
    private synchronized TechnicalSession autenticarSiSigueVacia() {
        TechnicalSession actual = vigente.get();
        return actual != null ? actual : renew();
    }

    private TechnicalSession guardar(TechnicalSession sesion) {
        validar(sesion.getIntegration());
        vigente.set(sesion);
        log.info("Sesion tecnica obtenida: {}", sesion.getIntegration());
        return sesion;
    }

    /**
     * Acepta la configuracion solo si sirve para trabajar.
     *
     * <p>El orden de los dos chequeos importa: primero el estado, despues
     * los campos. Con {@code PENDING} falta {@code redisPassword} por
     * contrato (§5.1), asi que preguntar por los campos primero informaria
     * un campo faltante cuando la causa real —y lo unico accionable— es el
     * estado de la cuenta.
     */
    private static void validar(IntegrationConfig config) {
        if (!config.isProvisioned()) {
            throw new IntegrationNotProvisionedException(
                    config.getProvisioningStatus(), config.getGroupId());
        }
        List<String> faltantes = config.missingRequiredValues();
        if (!faltantes.isEmpty()) {
            throw new IncompleteIntegrationConfigException(config.getGroupId(), faltantes);
        }
    }
}

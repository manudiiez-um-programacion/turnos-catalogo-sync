package com.um.catalogsync.integration.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.application.exception.IncompleteIntegrationConfigException;
import com.um.catalogsync.integration.application.exception.IntegrationNotProvisionedException;
import com.um.catalogsync.integration.application.exception.TechnicalAuthenticationFailedException;
import com.um.catalogsync.integration.application.exception.TechnicalTokenRejectedException;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.integration.domain.model.TechnicalSession;
import com.um.catalogsync.integration.domain.ports.out.IntegrationConfigPort;
import com.um.catalogsync.integration.domain.ports.out.TechnicalAuthPort;
import com.um.catalogsync.shared.domain.model.Secret;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pruebas de la sesion tecnica en memoria.
 *
 * <p>Los puertos se reemplazan por lambdas, como en el resto del proyecto:
 * son interfaces de un solo metodo y lo que hay que observar es cuantas
 * veces se llamo a cada uno, que es justamente lo que define el
 * comportamiento de una cache.
 */
class TechnicalSessionCacheTest {

    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger lecturasDeConfig = new AtomicInteger();

    /** Login que siempre funciona y devuelve la configuracion habilitada. */
    private final TechnicalAuthPort loginCorrecto = () -> {
        logins.incrementAndGet();
        return IntegrationFixtures.sesion();
    };

    /** Lectura de configuracion que siempre funciona. */
    private final IntegrationConfigPort configCorrecta = token -> {
        lecturasDeConfig.incrementAndGet();
        return IntegrationFixtures.provisionada();
    };

    @Test
    @DisplayName("La primera llamada autentica y las siguientes usan la copia en memoria")
    void autenticaUnaVezYCachea() {
        TechnicalSessionCache cache = new TechnicalSessionCache(loginCorrecto, configCorrecta);

        TechnicalSession primera = cache.current();
        TechnicalSession segunda = cache.current();

        assertThat(logins).hasValue(1);
        assertThat(segunda).isSameAs(primera);
        assertThat(primera.getTechnicalToken()).isEqualTo(Secret.of(IntegrationFixtures.TOKEN_FALSO));
    }

    @Test
    @DisplayName("renew vuelve a autenticarse aunque ya hubiera sesion")
    void renewFuerzaUnLoginNuevo() {
        TechnicalSessionCache cache = new TechnicalSessionCache(loginCorrecto, configCorrecta);

        cache.current();
        cache.renew();

        assertThat(logins).hasValue(2);
    }

    @Test
    @DisplayName("El refresco relee la configuracion sin volver a loguearse")
    void elRefrescoNoGastaUnLogin() {
        TechnicalSessionCache cache = new TechnicalSessionCache(loginCorrecto, configCorrecta);
        cache.current();

        IntegrationConfig refrescada = cache.refreshConfig();

        assertThat(logins).hasValue(1);
        assertThat(lecturasDeConfig).hasValue(1);
        assertThat(refrescada.getGroupId()).isEqualTo(IntegrationFixtures.GROUP_ID);
    }

    @Test
    @DisplayName("El refresco deja la configuracion nueva como vigente")
    void elRefrescoReemplazaLaVigente() {
        IntegrationConfig conTopicNuevo = IntegrationConfig.builder()
                .groupId(IntegrationFixtures.GROUP_ID)
                .redisHost("redis.catedra.internal")
                .redisPort(6379)
                .redisUsername("grp_" + IntegrationFixtures.GROUP_ID)
                .redisPassword(Secret.of(IntegrationFixtures.REDIS_PASSWORD_FALSA))
                .redisReadNamespace("catedra:sync:*")
                .redisWriteNamespace("alumnos:x:*")
                .kafkaBootstrapServers("kafka.catedra.internal:9092")
                .kafkaConsumerGroupId("alumnos-x")
                .kafkaCatalogTopic("catedra.catalog.topic-reasignado")
                .provisioningStatus(ProvisioningStatus.PROVISIONED)
                .build();
        TechnicalSessionCache cache =
                new TechnicalSessionCache(loginCorrecto, token -> conTopicNuevo);
        cache.current();

        cache.refreshConfig();

        assertThat(cache.current().getIntegration().getKafkaCatalogTopic())
                .isEqualTo("catedra.catalog.topic-reasignado");
        // El token no cambio: solo se releyo la configuracion.
        assertThat(logins).hasValue(1);
    }

    @Test
    @DisplayName("Refrescar sin sesion previa se autentica primero")
    void refrescarSinSesionSeAutentica() {
        TechnicalSessionCache cache = new TechnicalSessionCache(loginCorrecto, configCorrecta);

        IntegrationConfig config = cache.refreshConfig();

        assertThat(logins).hasValue(1);
        assertThat(lecturasDeConfig).hasValue(0);
        assertThat(config.isProvisioned()).isTrue();
    }

    @Test
    @DisplayName("Si la catedra rechaza el token, se reautentica una sola vez")
    void reautenticaUnaVezAnteTokenRechazado() {
        IntegrationConfigPort tokenVencido = token -> {
            lecturasDeConfig.incrementAndGet();
            throw new TechnicalTokenRejectedException("/api/student/integration");
        };
        TechnicalSessionCache cache = new TechnicalSessionCache(loginCorrecto, tokenVencido);
        cache.current();

        IntegrationConfig config = cache.refreshConfig();

        assertThat(lecturasDeConfig).hasValue(1);
        assertThat(logins).as("un login inicial y uno de recuperacion").hasValue(2);
        assertThat(config.isProvisioned()).isTrue();
    }

    @Test
    @DisplayName("Credenciales rechazadas no se reintentan: la excepcion sale tal cual")
    void lasCredencialesRechazadasNoSeReintentan() {
        TechnicalAuthPort credencialesMalas = () -> {
            logins.incrementAndGet();
            throw new TechnicalAuthenticationFailedException("juan.perez");
        };
        TechnicalSessionCache cache = new TechnicalSessionCache(credencialesMalas, configCorrecta);

        assertThatThrownBy(cache::current)
                .isInstanceOf(TechnicalAuthenticationFailedException.class);

        assertThat(logins).hasValue(1);
    }

    @ParameterizedTest
    @EnumSource(value = ProvisioningStatus.class, names = "PROVISIONED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Una cuenta que no esta habilitada no se acepta ni se cachea")
    void rechazaLasCuentasNoHabilitadas(ProvisioningStatus estado) {
        TechnicalAuthPort sinHabilitar = () -> {
            logins.incrementAndGet();
            return new TechnicalSession(
                    Secret.of(IntegrationFixtures.TOKEN_FALSO),
                    IntegrationFixtures.conEstado(estado));
        };
        TechnicalSessionCache cache = new TechnicalSessionCache(sinHabilitar, configCorrecta);

        assertThatThrownBy(cache::current)
                .isInstanceOf(IntegrationNotProvisionedException.class)
                .hasMessageContaining(estado.name())
                .hasMessageContaining(IntegrationFixtures.GROUP_ID);

        // Y no quedo cacheada: el siguiente intento vuelve a preguntar.
        assertThatThrownBy(cache::current).isInstanceOf(IntegrationNotProvisionedException.class);
        assertThat(logins).hasValue(2);
    }

    @Test
    @DisplayName("PROVISIONED con campos faltantes es otro error, y nombra los campos")
    void rechazaLaConfiguracionIncompleta() {
        IntegrationConfig incompleta = IntegrationConfig.builder()
                .groupId(IntegrationFixtures.GROUP_ID)
                .provisioningStatus(ProvisioningStatus.PROVISIONED)
                .build();
        TechnicalSessionCache cache = new TechnicalSessionCache(
                () -> new TechnicalSession(Secret.of(IntegrationFixtures.TOKEN_FALSO), incompleta),
                configCorrecta);

        assertThatThrownBy(cache::current)
                .isInstanceOf(IncompleteIntegrationConfigException.class)
                .hasMessageContaining("redisPassword")
                .hasMessageContaining("kafkaCatalogTopic");
    }
}

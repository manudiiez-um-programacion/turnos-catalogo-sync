package com.um.catalogsync.sync.infrastructure.redis.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.integration.IntegrationFixtures;
import com.um.catalogsync.integration.domain.model.IntegrationConfig;
import com.um.catalogsync.integration.domain.model.ProvisioningStatus;
import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import com.um.catalogsync.sync.domain.model.CatalogChanges;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisConnection;
import com.um.catalogsync.sync.infrastructure.redis.CatedraRedisForTests;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogFixtures;
import com.um.catalogsync.sync.infrastructure.redis.PublishedCatalogKeys;
import com.um.catalogsync.sync.infrastructure.redis.mapper.CatalogChangesMapper;
import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * La lectura de los cambios de una version contra un Redis real.
 *
 * <p>Comparte contenedor y ACL con la prueba del catalogo publicado, por
 * {@link CatedraRedisForTests}. Lo que un doble en memoria no podria probar
 * es precisamente el criterio del issue: que {@code GET} sobre una clave
 * inexistente se distinga de una clave que existe con los tres arrays
 * vacios. En Redis esa diferencia es {@code nil} contra un string, y es la
 * que decide entre aplicar la version y reconstruir con snapshot.
 *
 * <p>La version de referencia es la 7, la del ejemplo de §14.5.
 */
class RedisCatalogChangesAdapterTest {

    private static final long VERSION = PublishedCatalogFixtures.VERSION_ACTUAL;

    /** La configuracion que el adaptador va a ver. Las pruebas la cambian. */
    private IntegrationConfig configuracion;

    private CatedraRedisConnection conexion;
    private RedisCatalogChangesAdapter adaptador;

    @BeforeEach
    void prepararAdaptador() {
        CatedraRedisForTests.limpiar();
        configuracion = CatedraRedisForTests.apuntandoA(
                CatedraRedisForTests.puerto(), CatedraRedisForTests.CLAVE);
        conexion = CatedraRedisForTests.conexionCon(() -> configuracion);
        adaptador = new RedisCatalogChangesAdapter(
                conexion, new CatalogChangesMapper(new ObjectMapper()));
    }

    @AfterEach
    void cerrarConexion() {
        conexion.destroy();
    }

    // --- Lo que la catedra publica ----------------------------------------

    @Test
    @DisplayName("Lee la clave del ejemplo de §14.5 con los ids afectados")
    void leeLosCambiosDeLaReferencia() {
        dadosLosCambios(VERSION, PublishedCatalogFixtures.cambios());

        CatalogChanges cambios = adaptador.changesFor(VERSION).orElseThrow();

        assertThat(cambios.getVersion()).isEqualTo(VERSION);
        assertThat(cambios.getProfessionalIds())
                .containsExactly(PublishedCatalogFixtures.PROFESIONAL_ID);
        assertThat(cambios.getCategoryIds()).isEmpty();
        assertThat(cambios.getScheduleIds()).isEmpty();
    }

    @Test
    @DisplayName("Los arrays traen ids, no entidades")
    void losArraysTraenIds() {
        // Es la razon de ser del issue: con el id no alcanza para aplicar
        // nada, hay que ir al hash de §14.4 por el estado vigente. El
        // modelo no tiene lugar donde poner una entidad, y esta prueba deja
        // dicho que eso es a proposito y no una omision.
        dadosLosCambios(VERSION, PublishedCatalogFixtures.cambios());

        assertThat(adaptador.changesFor(VERSION)).get()
                .satisfies(cambios -> assertThat(cambios.getProfessionalIds())
                        .containsOnly(PublishedCatalogFixtures.PROFESIONAL_ID));
    }

    @Test
    @DisplayName("Cada version tiene su clave y no se mezclan")
    void cadaVersionTieneSuClave() {
        dadosLosCambios(7L, PublishedCatalogFixtures.cambios());
        dadosLosCambios(8L, PublishedCatalogFixtures.cambiosDeLasTres(8L));

        // El sufijo de la clave es lo unico que separa una version de otra,
        // asi que armarlo mal haria que #25 aplicara los cambios de la
        // version equivocada sin ningun error visible.
        assertThat(adaptador.changesFor(7L).orElseThrow().totalIds()).isEqualTo(1);
        assertThat(adaptador.changesFor(8L).orElseThrow().totalIds()).isEqualTo(6);
    }

    // --- Ausente no es lo mismo que vacio ---------------------------------

    @Test
    @DisplayName("Una version con los tres arrays vacios es una version valida")
    void losTresArraysVaciosSonUnaVersionValida() {
        dadosLosCambios(VERSION, PublishedCatalogFixtures.cambiosVacios(VERSION));

        // La catedra publico la version y no cambio nada. #25 la aplica y la
        // version local avanza. Si esto volviera vacio, se confundiria con
        // una discontinuidad y dispararia un snapshot completo sin motivo.
        assertThat(adaptador.changesFor(VERSION)).get()
                .satisfies(cambios -> {
                    assertThat(cambios.isEmpty()).isTrue();
                    assertThat(cambios.getVersion()).isEqualTo(VERSION);
                });
    }

    @Test
    @DisplayName("Una clave que no existe es vacio, y no se interpreta aca")
    void laClaveAusenteEsVacio() {
        // La otra mitad del criterio. Desde el adaptador no se sabe si la
        // version todavia no se publico o si ya salio de la ventana
        // retenida: la primera es normal y la segunda es una discontinuidad
        // que fuerza snapshot (§18.2). Quien tiene las dos versiones del
        // origen delante decide, y eso es #26.
        assertThat(adaptador.changesFor(VERSION)).isEmpty();
    }

    @Test
    @DisplayName("Las dos respuestas se distinguen en la misma corrida")
    void vacioYAusenteSeDistinguen() {
        dadosLosCambios(7L, PublishedCatalogFixtures.cambiosVacios(7L));

        // Las dos lecturas, una al lado de la otra: la 7 existe sin cambios
        // y la 8 no existe. Son los dos resultados que el issue pide no
        // mezclar.
        assertThat(adaptador.changesFor(7L)).isPresent();
        assertThat(adaptador.changesFor(8L)).isEmpty();
    }

    @Test
    @DisplayName("Una version anterior que falta tambien es vacio, sin excepcion")
    void unaVersionVieja() {
        // El caso de la ventana retenida: la 4 se publico alguna vez y su
        // clave ya no esta. No puede ser una excepcion, porque #27 necesita
        // poder preguntar por una version vieja para decidir si reconstruye.
        dadosLosCambios(7L, PublishedCatalogFixtures.cambios());

        assertThat(adaptador.changesFor(PublishedCatalogFixtures.VERSION_MAS_ANTIGUA)).isEmpty();
    }

    // --- Lo que existe y no cumple el contrato ----------------------------

    @Test
    @DisplayName("Un JSON que no se puede leer es un error y nombra la clave")
    void unJsonRotoEsUnError() {
        dadosLosCambios(VERSION, "{esto no es json");

        assertThatThrownBy(() -> adaptador.changesFor(VERSION))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining(PublishedCatalogKeys.changes(VERSION));
    }

    @Test
    @DisplayName("Si la version del JSON no es la de la clave, se rechaza")
    void laVersionTieneQueCoincidir() {
        dadosLosCambios(VERSION, PublishedCatalogFixtures.cambiosVacios(99L));

        assertThatThrownBy(() -> adaptador.changesFor(VERSION))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("99");
    }

    // --- Los permisos de §14.1 --------------------------------------------

    @Test
    @DisplayName("La ACL no permite escribir una clave de cambios")
    void noSePuedeEscribirUnaClaveDeCambios() {
        // El puerto no ofrece escrituras, asi que esto va con un comando
        // crudo sobre la misma conexion. No vigila al adaptador: vigila que
        // el permiso que justifica no haber escrito nunca sea real.
        assertThatThrownBy(() -> conexion.comandos()
                        .set(PublishedCatalogKeys.changes(VERSION), "{}"))
                .isInstanceOf(RedisCommandExecutionException.class)
                .hasMessageContaining("NOPERM");
    }

    // --- Cuando Redis no esta ---------------------------------------------

    @Test
    @DisplayName("Si no se llega a Redis, es un problema de disponibilidad")
    void siNoSeLlegaARedisEsUnErrorDeDisponibilidad() {
        // Puerto cerrado. No puede volver vacio: "no se pudo preguntar" y
        // "la version no esta" llevan a decisiones distintas, y confundirlos
        // haria que un corte de red se viera como una discontinuidad y
        // disparara un snapshot completo sobre un enlace que no anda.
        CatedraRedisConnection inalcanzable = CatedraRedisForTests.conexionCon(
                () -> CatedraRedisForTests.apuntandoA(1, CatedraRedisForTests.CLAVE));

        assertThatThrownBy(() -> new RedisCatalogChangesAdapter(
                        inalcanzable, new CatalogChangesMapper(new ObjectMapper()))
                        .changesFor(VERSION))
                .isInstanceOf(PublishedCatalogUnavailableException.class)
                .hasMessageContaining("ZeroTier");
    }

    @Test
    @DisplayName("Sin redisPassword, el mensaje manda al alta de la cuenta tecnica")
    void sinClaveAvisaDelAlta() {
        CatedraRedisConnection sinClave = CatedraRedisForTests.conexionCon(
                () -> IntegrationFixtures.conEstado(ProvisioningStatus.PENDING));

        assertThatThrownBy(() -> new RedisCatalogChangesAdapter(
                        sinClave, new CatalogChangesMapper(new ObjectMapper()))
                        .changesFor(VERSION))
                .isInstanceOf(PublishedCatalogUnavailableException.class)
                .hasMessageContaining("§5.1");
    }

    // --- Auxiliares -------------------------------------------------------

    private void dadosLosCambios(long version, String json) {
        CatedraRedisForTests.cli("SET", PublishedCatalogKeys.changes(version), json);
    }
}

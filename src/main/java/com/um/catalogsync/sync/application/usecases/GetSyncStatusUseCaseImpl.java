package com.um.catalogsync.sync.application.usecases;

import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.application.exception.PublishedCatalogUnavailableException;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.PublishedCatalogMetadata;
import com.um.catalogsync.sync.domain.model.SyncError;
import com.um.catalogsync.sync.domain.model.SyncOriginState;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatusReport;
import com.um.catalogsync.sync.domain.ports.in.GetSyncStatusUseCase;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import com.um.catalogsync.sync.domain.ports.out.PublishedCatalogPort;
import com.um.catalogsync.sync.domain.ports.out.SyncRunRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Junta las tres lecturas que componen el reporte de estado.
 *
 * <p>Lo unico que hace es leer: la version local, el historial y el estado
 * del origen. Las reglas que derivan el estado y el atraso viven en
 * {@link SyncStatusReport}, asi que lo que decide esta clase es <b>que se lee
 * y que pasa cuando una lectura no se puede hacer</b>.
 *
 * <p><b>El origen se consulta en vivo, y es la decision que hay que
 * justificar.</b> La version remota podria recordarse de la ultima
 * sincronizacion y evitar una lectura de Redis por consulta. Se descarta
 * porque convertiria el estado en un recuerdo: con la catedra publicando
 * versiones nuevas, un valor guardado diria "al dia" sobre una comparacion
 * vieja, que es exactamente el error que este endpoint tiene que detectar. Y
 * sin leer el origen no habria forma de distinguir {@code BEHIND} de
 * {@code DEGRADED}, que es el criterio central del issue: uno se recupera
 * solo y el otro necesita que vuelva la catedra.
 *
 * <p><b>El costo esta acotado y la falla tambien.</b> Es una sola lectura del
 * hash {@code catedra:sync:metadata} —la misma que hace el chequeo periodico
 * cada pocos minutos— con el {@code command-timeout} configurado, asi que lo
 * peor que puede pasar es que la consulta tarde ese timeout y conteste
 * {@code UNREACHABLE}. No consulta el catalogo: §6.2 prohibe resolver
 * <i>busquedas</i> contra la catedra, y esto no es una busqueda sino el
 * estado de la integracion.
 *
 * <p><b>No es transaccional.</b> Las tres lecturas no tienen que ser
 * consistentes entre si: la copia local puede avanzar entre una y otra, y una
 * transaccion no evitaria nada —el origen esta en Redis, fuera de cualquier
 * transaccion de la base—. El reporte es una foto y lo dice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetSyncStatusUseCaseImpl implements GetSyncStatusUseCase {

    /**
     * Cuantos errores entran en el reporte.
     *
     * <p>Constante y no configuracion, al revés que la ventana de retencion:
     * esto no depende del entorno ni es destructivo, es cuanto se muestra.
     * Diez alcanza para ver si lo que falla es siempre lo mismo, que es la
     * pregunta que se le hace al reporte, y el historial completo esta en la
     * tabla para quien necesite mas.
     */
    static final int ERRORES_EN_EL_REPORTE = 10;

    private final CatalogVersionRepository versions;
    private final SyncRunRepository runs;
    private final PublishedCatalogPort publishedCatalog;

    @Override
    public SyncStatusReport getStatus() {
        CatalogVersion local = versions.findCurrent().orElse(null);
        SyncRun ultima = runs.findLast().orElse(null);
        List<SyncError> errores = runs.findRecentErrors(ERRORES_EN_EL_REPORTE);

        LecturaDelOrigen origen = leerOrigen();

        return new SyncStatusReport(
                local, ultima, origen.estado(), origen.metadata(), errores);
    }

    /**
     * Lee el estado del origen sin dejar que su falla se escape.
     *
     * <p><b>La falla se traga a proposito, y es la unica de todo el slice que
     * se traga sin registrar una corrida.</b> En cualquier otro lugar, un
     * Redis caido es un hecho que cambia lo que el servicio hace —§6.2 manda
     * sincronizacion completa— y tiene que quedar en el historial. Acá no se
     * esta sincronizando nada: alguien pregunto como esta el servicio, y "no
     * puedo hablar con la catedra" <b>es</b> la respuesta a esa pregunta. Que
     * preguntar por el estado ensuciara el historial con corridas fallidas
     * seria al revés: observar no deberia cambiar lo observado.
     *
     * <p>Las dos excepciones que se capturan son las mismas dos que
     * {@code SynchronizeCatalogUseCaseImpl} trata como "estado no
     * verificable", y por el mismo motivo: no poder hablar con Redis y que
     * Redis conteste algo que no cumple §14.3 dejan al servicio igual de
     * ciego. Cualquier otra excepcion se propaga: seria un error de
     * programacion, y taparlo haria que el endpoint informara un estado
     * tranquilizador sobre un servicio roto.
     */
    private LecturaDelOrigen leerOrigen() {
        try {
            return publishedCatalog.metadata()
                    .map(LecturaDelOrigen::publicado)
                    .orElseGet(LecturaDelOrigen::sinPublicar);
        } catch (PublishedCatalogUnavailableException | MalformedPublishedCatalogException
                noSePudoVerificar) {
            log.debug("No se pudo leer el estado del origen para el reporte: {}",
                    noSePudoVerificar.getMessage());
            return LecturaDelOrigen.inalcanzable();
        }
    }

    /**
     * El resultado de preguntarle al origen: los datos y por que faltan si
     * faltan.
     *
     * <p>Existe para que la distincion entre "la catedra no publico nada" y
     * "no pude preguntarle" se resuelva <b>donde se sabe</b>, que es en el
     * mismo {@code try}. Las dos dejan el reporte sin version remota, pero son
     * problemas de otra gente —una la arregla la catedra publicando, la otra
     * la red privada— y un unico {@code null} las confundiria. Deducirlo
     * despues obligaria a una segunda lectura de Redis solo para preguntar si
     * contesta.
     *
     * <p>Privado y del tamaño de lo que hace: no es un modelo de dominio, es
     * el valor de retorno de un metodo de esta clase.
     *
     * @param estado que se sabe del origen
     * @param metadata la metadata, o {@code null} si no hay
     */
    private record LecturaDelOrigen(SyncOriginState estado, PublishedCatalogMetadata metadata) {

        static LecturaDelOrigen publicado(PublishedCatalogMetadata metadata) {
            return new LecturaDelOrigen(SyncOriginState.KNOWN, metadata);
        }

        static LecturaDelOrigen sinPublicar() {
            return new LecturaDelOrigen(SyncOriginState.NOT_PUBLISHED, null);
        }

        static LecturaDelOrigen inalcanzable() {
            return new LecturaDelOrigen(SyncOriginState.UNREACHABLE, null);
        }
    }
}

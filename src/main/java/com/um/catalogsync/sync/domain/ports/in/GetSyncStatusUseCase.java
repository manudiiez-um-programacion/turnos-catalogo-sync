package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.SyncStatusReport;

/**
 * Informar el estado y los errores de sincronizacion (§4.1).
 *
 * <p>Es un caso de uso y no una consulta armada en el controlador, y la
 * diferencia no es de estilo: el reporte se compone de <b>tres lecturas
 * distintas</b> —la version local, el historial y el estado del origen—, de
 * la decision de que hacer cuando el origen no se puede leer, y de las reglas
 * que derivan el estado y el atraso. Nada de eso es traduccion a HTTP, asi
 * que nada de eso puede vivir en la capa web: ahi quedaria fuera del alcance
 * de las pruebas que no levantan un servidor, y cualquier otra forma de
 * informar el mismo estado tendria que repetirlo.
 *
 * <p><b>Nunca falla por el estado que informa.</b> Es la diferencia con los
 * demas casos de uso de lectura del servicio: la busqueda y la agenda
 * responden 503 cuando no hay catalogo aplicado, porque sin catalogo no
 * tienen nada que contestar. Este tiene <i>justamente entonces</i> su
 * respuesta mas util. Una copia local vacia, la catedra caida o diez
 * sincronizaciones fallidas son contenido del reporte, no excepciones.
 */
public interface GetSyncStatusUseCase {

    /**
     * Arma el reporte con lo que se sabe en este momento.
     *
     * @return el estado de la sincronizacion, nunca {@code null}
     */
    SyncStatusReport getStatus();
}

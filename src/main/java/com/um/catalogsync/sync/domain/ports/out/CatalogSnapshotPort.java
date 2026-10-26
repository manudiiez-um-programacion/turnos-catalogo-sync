package com.um.catalogsync.sync.domain.ports.out;

import com.um.catalogsync.sync.domain.model.CatalogSnapshot;

/**
 * Puerto de salida del snapshot completo de catalogo (§7).
 *
 * <p>Es la fuente de la sincronizacion completa, que corre en dos
 * situaciones: cuando la base esta vacia (#14) y cuando hay que reconstruir
 * la copia local por discontinuidad (§18.2, #26). Las dos necesitan lo
 * mismo —el catalogo entero y la version que le corresponde— asi que el
 * puerto tiene una sola operacion.
 *
 * <p><b>Sin parametros, y menos el JWT tecnico.</b> El token no entra por
 * firma: lo resuelve el adaptador pidiendoselo al slice
 * {@code integration}. Si estuviera en la firma, la credencial tendria que
 * atravesar la capa de aplicacion de {@code sync} para llegar hasta acá, y
 * el ADR es explicito en que ningun otro slice la conoce.
 *
 * <p><b>Sin paginado.</b> El contrato devuelve el catalogo completo en una
 * respuesta y no ofrece paginar (§7). Inventar un parametro de pagina seria
 * agregar algo que la referencia no da.
 */
public interface CatalogSnapshotPort {

    /**
     * Trae el catalogo completo de la catedra.
     *
     * <p>Nunca devuelve {@code null} ni un snapshot a medias: si la
     * respuesta no cumple el contrato, falla. Un snapshot incompleto es
     * peor que ninguno, porque #13 lo aplicaria reemplazando la copia local
     * por datos parciales.
     *
     * @return la foto completa del catalogo, con su version
     * @throws com.um.catalogsync.sync.application.exception.CatalogSnapshotUnauthorizedException
     *     si la catedra rechazo el JWT tecnico (401)
     * @throws com.um.catalogsync.sync.application.exception.TechnicalIntegrationMissingException
     *     si la cuenta tecnica no tiene una integracion asociada (403 con
     *     {@code STUDENT_INTEGRATION_NOT_FOUND})
     * @throws com.um.catalogsync.sync.application.exception.CatalogSnapshotForbiddenException
     *     si la catedra prohibio la operacion por otro motivo (403)
     * @throws com.um.catalogsync.sync.application.exception.CatalogSnapshotUnavailableException
     *     si no se pudo hablar con la catedra o respondio un error de
     *     infraestructura
     * @throws com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException
     *     si la respuesta fue exitosa pero no cumple el contrato
     */
    CatalogSnapshot fetch();
}

package com.um.catalogsync.professional.domain.model;

import com.um.catalogsync.sync.domain.model.CatalogVersion;

/**
 * El resultado de una busqueda: la pagina y la version de catalogo con la que
 * se resolvio.
 *
 * <p>Las dos cosas van juntas por el mismo motivo que en {@code CurrentAgenda}:
 * un listado de profesionales sin decir de que copia salen obliga al cliente a
 * suponerlo. Devolver la version es un criterio de aceptacion de #15 y le sirve
 * a la app KMP para saber si dos pantallas estan mirando el mismo catalogo.
 *
 * <p><b>Que la version este en el resultado y no se consulte aparte</b> es lo
 * que hace que el dato sea cierto: el caso de uso lee la version y la pagina en
 * una sola transaccion, asi que el numero describe exactamente los datos que
 * viajan al lado. Dos llamadas separadas podrian cruzarse con una
 * sincronizacion y devolver la version de una copia con las filas de otra.
 *
 * @param page los profesionales que matchean, paginados
 * @param catalogVersion la version local vigente al resolver la busqueda
 */
public record ProfessionalSearchResult(ProfessionalPage page, CatalogVersion catalogVersion) {}

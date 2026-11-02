package com.um.catalogsync.shared.infrastructure.web.dto;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * La version local de catalogo con la que se resolvio la respuesta.
 *
 * <p>Es lo que le permite a quien consulta saber sobre que copia esta
 * trabajando (§4.2): si entre dos consultas el numero cambio, los datos pueden
 * no ser comparables, y lo sabe sin preguntar nada.
 *
 * <p><b>Vive en {@code shared} porque lo devuelven las dos APIs</b> —la agenda
 * vigente que consume el servicio de turnos (#18) y la busqueda que consume la
 * app KMP (#15)— y es el mismo dato con el mismo significado. Duplicarlo por
 * slice daria dos esquemas con el mismo nombre en el documento OpenAPI, y la
 * primera vez que uno de los dos cambiara, el contrato empezaria a describir
 * dos cosas distintas con una sola palabra.
 *
 * <p>Es la excepcion y no la regla: un DTO se duplica antes que compartirse,
 * porque compartirlo acopla dos APIs que pueden tener que evolucionar por
 * separado. Lo que la justifica aca es que el dato no es de ningun slice en
 * particular: describe el estado de la copia local entera.
 *
 * @param version numero de version del catalogo de la catedra
 * @param appliedAt cuando esta copia termino de aplicarla, en UTC
 * @param source si llego por snapshot completo o por cambios incrementales
 */
@Schema(name = "AppliedCatalogVersion", description = "Version local de catalogo aplicada")
public record CatalogVersionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "128") long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-27T18:30:00Z")
                Instant appliedAt,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"SNAPSHOT", "INCREMENTAL"},
                        example = "SNAPSHOT")
                String source) {

    /**
     * Del modelo de dominio al DTO.
     *
     * <p>Fabrica estatica y no un {@code @Component} mapper: es una
     * conversion de tres campos sin ninguna decision, y tenerla al lado del
     * record evita que los dos mappers web que la necesitan la escriban cada
     * uno a su manera. Que el {@code source} viaje como texto y no como enum
     * es parte del contrato: el cliente decide por el valor, no por una clase
     * de Java.
     */
    public static CatalogVersionResponse from(CatalogVersion version) {
        return new CatalogVersionResponse(
                version.getVersion(), version.getAppliedAt(), version.getSource().name());
    }
}

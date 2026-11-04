package com.um.catalogsync.sync.infrastructure.web.dto;

import com.um.catalogsync.shared.infrastructure.web.dto.CatalogVersionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El estado y los errores de sincronizacion (§4.1).
 *
 * <p>Responde cinco preguntas en una sola lectura: en que situacion esta el
 * servicio, que version tiene la copia local, que version publica la catedra,
 * cuanto falta, y que viene fallando.
 *
 * <p><b>El {@code health} es lo que hay que leer primero</b>, y los cuatro
 * valores no son grados de una misma escala: cada uno pide una reaccion
 * distinta. {@code BEHIND} se recupera solo —el chequeo periodico lo trae— y
 * {@code DEGRADED} necesita que vuelva la catedra. Confundirlos es
 * exactamente el error que este campo evita.
 *
 * <p><b>Todo lo nulable es informacion y no un hueco.</b> Sin
 * {@code localVersion} la base esta recien inicializada; sin {@code lastSync}
 * nunca se intento sincronizar; sin {@code versionsBehind} no hay con que
 * calcularlo. Un cero o un objeto vacio en su lugar inventaria tranquilidad.
 *
 * @param health en que situacion esta la sincronizacion
 * @param localVersion la version aplicada, o nulo si todavia no se aplico
 *     ninguna
 * @param remote lo que se sabe del catalogo que publica la catedra
 * @param versionsBehind cuantas versiones le faltan a la copia local, o nulo
 *     si no se puede calcular
 * @param lastSync el ultimo intento de sincronizacion, o nulo si no hubo
 *     ninguno
 * @param recentErrors los ultimos errores, del mas nuevo al mas viejo. Vacia
 *     si no hubo ninguno
 */
@Schema(
        name = "SyncStatus",
        description =
                "Estado y errores de la sincronizacion con la catedra. Responde siempre 200,"
                        + " incluso sin catalogo aplicado o con la catedra caida: ese es"
                        + " justamente el contenido mas util del reporte.")
public record SyncStatusResponse(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"UP_TO_DATE", "BEHIND", "REBUILDING", "DEGRADED"},
                        description =
                                "UP_TO_DATE: la copia local esta en la version publicada."
                                        + " BEHIND: faltan versiones, se recupera solo."
                                        + " REBUILDING: hay una sincronizacion en curso."
                                        + " DEGRADED: no se pudo verificar el origen o el"
                                        + " ultimo intento fallo; necesita intervencion.",
                        example = "UP_TO_DATE")
                String health,
        CatalogVersionResponse localVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RemoteCatalogStateResponse remote,
        @Schema(
                        description =
                                "Atraso en versiones. Nulo si no hay version local o no se"
                                        + " conoce la del origen",
                        example = "0")
                Long versionsBehind,
        LastSyncResponse lastSync,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SyncErrorResponse> recentErrors) {}

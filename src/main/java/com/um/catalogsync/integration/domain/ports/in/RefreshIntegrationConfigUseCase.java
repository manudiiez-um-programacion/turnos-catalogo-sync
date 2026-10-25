package com.um.catalogsync.integration.domain.ports.in;

import com.um.catalogsync.integration.domain.model.IntegrationConfig;

/**
 * Vuelve a leer la configuracion de la catedra y reemplaza la que estaba en
 * memoria.
 *
 * <p>Separado de {@link GetIntegrationConfigUseCase} a proposito, y es la
 * diferencia que pide el issue: la lectura normal usa la copia en memoria y
 * no gasta una llamada; este caso de uso es el refresco <b>explicito</b>,
 * para cuando algo cambio del lado de la catedra y hay que enterarse ahora
 * —una cuenta que paso de {@code PENDING} a {@code PROVISIONED}, un topic
 * reasignado—. Si la lectura normal refrescara sola, cada consulta de un
 * topic seria un viaje de ~228 ms (D6) a la catedra.
 */
public interface RefreshIntegrationConfigUseCase {

    /**
     * @return la configuracion recien leida, ya guardada en memoria
     */
    IntegrationConfig refresh();
}

package com.um.catalogsync.sync.domain.ports.in;

import com.um.catalogsync.sync.domain.model.CatalogSnapshot;
import com.um.catalogsync.sync.domain.model.CatalogVersion;

/**
 * Aplica un snapshot ya obtenido a la copia local, en una sola unidad de
 * trabajo.
 *
 * <p><b>Por que es un puerto aparte de {@link RunFullSyncUseCase}</b> y no
 * un metodo privado adentro. Dos razones, y la segunda es la que obliga:
 *
 * <ul>
 *   <li>Aplicar un snapshot que ya se tiene es una operacion con sentido
 *       propio: la reconstruccion por discontinuidad (#26) va a querer
 *       exactamente esto.</li>
 *   <li>La unidad de trabajo necesita una frontera transaccional real.
 *       Spring abre la transaccion al entrar a un bean por su proxy, asi que
 *       un metodo {@code @Transactional} llamado desde otro metodo de la
 *       <i>misma</i> clase no abre nada: la anotacion no haria absolutamente
 *       nada y la regla central del proyecto quedaria sin cumplirse, en
 *       silencio. Separandolo en dos beans, la llamada pasa por el proxy y
 *       la transaccion existe.</li>
 * </ul>
 */
public interface ApplyCatalogSnapshotUseCase {

    /**
     * Reemplaza las tres colecciones locales por las del snapshot y, recien
     * al final, avanza la version local a {@code snapshotVersion}.
     *
     * <p>Todo ocurre en una transaccion: o queda aplicado entero, o no queda
     * nada y la copia anterior sigue consistente con la version anterior.
     *
     * @param snapshot la foto completa del catalogo
     * @return la version local que quedo asentada
     * @throws com.um.catalogsync.professional.application.exception.UnknownCategoryException
     *     si un profesional del snapshot referencia una categoria que el
     *     snapshot no trae
     * @throws com.um.catalogsync.schedule.application.exception.UnknownProfessionalException
     *     si un horario referencia un profesional que el snapshot no trae
     */
    CatalogVersion apply(CatalogSnapshot snapshot);
}

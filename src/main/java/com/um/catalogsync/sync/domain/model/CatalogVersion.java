package com.um.catalogsync.sync.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La version de catalogo que este servicio tiene efectivamente aplicada.
 *
 * <p>Es un unico valor para todo el servicio, no una entidad por fila: hay
 * una sola version local, y la tabla lo garantiza con un CHECK sobre un id
 * fijo. Vive en el slice {@code sync} porque es estado del proceso de
 * sincronizacion, no un dato del catalogo.
 *
 * <p><b>La regla central del proyecto</b> es que este valor avanza solo
 * cuando la unidad de trabajo termino completa: un snapshot aplicado entero
 * o todos los cambios de una version (§6.1 y §6.2). Esa garantia no se
 * programa aca, se consigue por construccion: el adaptador escribe en la
 * misma transaccion que los datos, asi que si el commit falla, la version
 * no avanzo. La prueba que lo demuestra esta en
 * {@code JpaCatalogVersionRepositoryAdapterTest}.
 *
 * <p>POJO puro, igual que los modelos de los slices de catalogo: sin JPA,
 * sin validacion, sin Jackson.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogVersion {

    /**
     * Numero de version de la catedra que corresponde a los datos que hoy
     * tiene la copia local.
     */
    private long version;

    /**
     * Cuando este servicio termino de aplicar esa version. Es nuestro
     * reloj, no un dato de la catedra: el contrato no manda este campo.
     */
    private Instant appliedAt;

    private CatalogVersionSource source;

    /**
     * Si desde esta version se puede pasar a la pedida.
     *
     * <p><b>La regla:</b> la version no retrocede, salvo que el origen sea
     * {@link CatalogVersionSource#SNAPSHOT}.
     *
     * <p><b>Por que SNAPSHOT es la excepcion.</b> Es el camino de la
     * reconstruccion por discontinuidad (§6.2, §18.2). Cuando la copia
     * local se reemplaza entera por un snapshot, la version local pasa a
     * ser la {@code snapshotVersion} que vino con esos datos, y esa version
     * puede ser menor que la que teniamos anotada: por ejemplo si una
     * notificacion nos dejo en una version que despues no pudimos verificar.
     * Negarse a retroceder ahi seria peor que retroceder: dejaria la
     * version mintiendo sobre datos que ya se reemplazaron. La version tiene
     * que describir lo que la copia local <i>tiene</i>, no lo mas alto que
     * alguna vez supimos.
     *
     * <p><b>Por que INCREMENTAL nunca retrocede.</b> Un incremento hacia
     * atras no tiene ningun significado valido: los cambios se aplican en
     * orden, asi que una version anterior solo puede venir de un evento
     * reordenado, un reproceso mal hecho o un bug. Aceptarlo haria que la
     * copia local quedara descrita por una version que no corresponde a sus
     * datos, y a partir de ahi la deteccion de discontinuidad decidiria con
     * informacion falsa.
     *
     * <p><b>Avanzar a la misma version esta permitido</b> y no cuenta como
     * retroceder. Es deliberado: el enunciado pide que reprocesar una
     * notificacion no altere el resultado final (§6.2, §18.1), y la version
     * no cambia. Convertir un duplicado inofensivo en un error haria
     * fracasar una corrida de sincronizacion por nada.
     *
     * @param version numero de version al que se quiere pasar
     * @param source como se llegaria a esa version
     */
    public boolean allowsAdvanceTo(long version, CatalogVersionSource source) {
        return source == CatalogVersionSource.SNAPSHOT || version >= this.version;
    }
}

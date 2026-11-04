package com.um.catalogsync.sync.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lo que la catedra declara sobre el catalogo que tiene publicado en Redis
 * (§14.3).
 *
 * <p>No es el catalogo ni la version local: es el estado del <b>origen</b>.
 * Describe hasta donde llego la catedra y desde donde se puede reconstruir
 * con cambios incrementales, que son las dos cosas que hacen falta para
 * decidir como sincronizar.
 *
 * <p><b>Para que sirve cada campo.</b> {@link #currentVersion} es la version
 * a la que habria que llegar. {@link #oldestAvailableVersion} es el borde de
 * la ventana que la catedra retiene: si la version local quedo por debajo,
 * los incrementos que faltan ya no estan y hay que pedir un snapshot
 * completo (§14.2, §18.2). {@link #publishedAt} es el reloj de la catedra, y
 * sirve para diagnosticar —"la catedra no publica nada desde ayer"— no para
 * decidir: las decisiones se toman con los numeros de version.
 *
 * <p><b>Por que esta repetido con las dos claves sueltas.</b> §14 publica los
 * mismos dos numeros en {@code catedra:sync:current-version} y
 * {@code catedra:sync:oldest-available-version} y tambien dentro de este
 * hash. Son dos lecturas distintas del mismo dato y el contrato no promete
 * que esten sincronizadas en un instante dado: las claves sueltas son el
 * camino barato para chequear una version, y este hash es la foto con el
 * {@code publishedAt} incluido. Quien compare los dos tiene que tolerar que
 * difieran si la catedra publico entre una lectura y la otra.
 *
 * <p>POJO puro, como el resto de los modelos del slice: sin JPA, sin
 * validacion, sin Jackson. Esto no se persiste —es estado del origen, no de
 * la copia local—, asi que no tiene tabla ni migracion.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedCatalogMetadata {

    /** La ultima version que la catedra publico. */
    private long currentVersion;

    /**
     * La version mas antigua desde la que todavia se puede avanzar con
     * cambios incrementales. Por debajo de esta, la unica salida es el
     * snapshot.
     */
    private long oldestAvailableVersion;

    /** Cuando la catedra publico esa version. Su reloj, no el nuestro. */
    private Instant publishedAt;

    /**
     * Si una version local puede ponerse al dia leyendo cambios
     * incrementales, sin pedir el snapshot completo.
     *
     * <p>La regla es la de §14.2 y §18.2: alcanza con que la version local
     * no haya quedado por debajo de la ventana retenida. Una version local
     * <b>igual</b> a {@link #oldestAvailableVersion} todavia sirve, porque
     * lo que hace falta son los cambios <i>posteriores</i> a ella y esos
     * estan.
     *
     * <p>Vive en el modelo y no en el servicio que la consulta porque es una
     * regla del contrato, no del proceso: #26 y #27 la van a necesitar los
     * dos y no deberian escribir la comparacion cada uno.
     *
     * @param localVersion la version que la copia local tiene aplicada
     */
    public boolean canCatchUpFrom(long localVersion) {
        return localVersion >= oldestAvailableVersion;
    }
}

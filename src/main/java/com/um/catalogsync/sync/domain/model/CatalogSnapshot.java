package com.um.catalogsync.sync.domain.model;

import com.um.catalogsync.category.domain.model.ProfessionalCategory;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.schedule.domain.model.WeeklySchedule;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El catalogo completo de la catedra en un momento dado, tal como lo
 * devuelve {@code GET /api/synchronization/snapshot} (§7).
 *
 * <p>Es la unidad de la sincronizacion completa: o se aplica entero o no se
 * aplica nada. Por eso las tres colecciones viajan juntas en un solo objeto
 * y no en tres llamadas: la consistencia de la copia local depende de que
 * las tres correspondan al mismo {@code snapshotVersion}, y tres lecturas
 * separadas no podrian garantizarlo.
 *
 * <p>POJO puro, igual que el resto de los modelos del proyecto: sin JPA, sin
 * validacion, sin Jackson. Los DTOs del contrato viven en
 * {@code infrastructure.client.dto} y no cruzan hasta acá.
 *
 * <p><b>Por que reusa los modelos de los otros slices.</b> Las tres
 * colecciones son {@code ProfessionalCategory}, {@code Professional} y
 * {@code WeeklySchedule} de {@code category}, {@code professional} y
 * {@code schedule}, y no copias propias de {@code sync}. Es la dependencia
 * que el ADR ya declara: {@code sync} es el unico slice que escribe en los
 * otros tres y lo hace contra sus puertos de salida, asi que tiene que
 * hablar su mismo lenguaje. Un modelo paralelo obligaria a un segundo mapeo
 * en #13 y abriria la puerta a que las dos versiones de "profesional" se
 * separen.
 *
 * <p><b>Lo que este objeto no incluye</b> es historial incremental: el
 * snapshot es una foto, no una secuencia de cambios (§7). Los cambios
 * incrementales se leen de Redis y son del issue #25.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSnapshot {

    /**
     * La version de catalogo que describe esta foto.
     *
     * <p>Es el valor que la copia local pasa a tener <b>solo</b> cuando las
     * tres colecciones quedaron aplicadas (§7). Quien lo asiente es el
     * puerto de la version local, dentro de la misma transaccion (#13).
     */
    private long snapshotVersion;

    /**
     * Cuando la catedra genero la foto. Dato del contrato en UTC (§4), no
     * nuestro reloj: {@code appliedAt} de {@code CatalogVersion} es el
     * nuestro y son dos instantes distintos.
     */
    private Instant generatedAt;

    /**
     * Las categorias de la foto, habilitadas y deshabilitadas.
     *
     * <p>El snapshot trae las dos (§7) y eso no es un detalle: una entidad
     * deshabilitada es una baja logica que la copia local tiene que
     * conservar (§14.4), no una fila que se descarte al leer. Filtrar acá
     * haria que la busqueda por {@code enabled} no tuviera nada que
     * filtrar.
     *
     * <p>Nunca es {@code null} cuando el objeto lo construye el mapper del
     * adaptador: una coleccion ausente en el JSON es una violacion del
     * contrato y falla antes de llegar acá. Una coleccion vacia, en cambio,
     * es un valor legitimo.
     */
    private List<ProfessionalCategory> categories;

    private List<Professional> professionals;

    private List<WeeklySchedule> schedules;
}

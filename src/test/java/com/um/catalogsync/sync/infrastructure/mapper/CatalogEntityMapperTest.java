package com.um.catalogsync.sync.infrastructure.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.application.exception.IncompleteCatalogSnapshotException;
import com.um.catalogsync.sync.application.exception.MalformedPublishedCatalogException;
import com.um.catalogsync.sync.infrastructure.dto.ProfessionalDto;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo propio de la traduccion compartida: que la misma exigencia se cuente
 * distinto segun por donde llego la entidad.
 *
 * <p>El resto de la clase ya esta ejercido por los dos mappers que la usan
 * —{@code CatalogSnapshotMapperTest} campo por campo sobre §7 y
 * {@code PublishedCatalogMapperTest} sobre §14.4—, asi que repetir aca esas
 * aserciones solo agregaria un tercer lugar donde tocar cuando el contrato
 * cambie. Lo que no se ve desde ninguno de los dos es el motivo por el que
 * la fabrica de excepciones entra por el constructor, y eso es lo que se
 * prueba.
 */
class CatalogEntityMapperTest {

    private static final String DONDE = "professionals";

    @Test
    @DisplayName("El mismo campo faltante se cuenta como falla de §7 o de §14 segun quien lea")
    void cadaContratoCuentaLaFallaComoPropia() {
        ProfessionalDto sinApellido = sinApellido();

        // Dos lectores, el mismo dato incompleto, dos diagnosticos: uno manda
        // a mirar el snapshot y el otro la clave de Redis. Si la excepcion
        // estuviera fija en esta clase, uno de los dos caminos mentiria sobre
        // donde esta el problema.
        assertThatThrownBy(() -> paraElSnapshot().toDomain(sinApellido, DONDE))
                .isInstanceOf(IncompleteCatalogSnapshotException.class)
                .hasMessageContaining("§7")
                .hasMessageContaining("professionals[15].lastName");

        assertThatThrownBy(() -> paraRedis().toDomain(sinApellido, DONDE))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("§14")
                .hasMessageContaining("professionals[15].lastName");
    }

    @Test
    @DisplayName("La ruta del error usa el id de la catedra y no un indice")
    void laRutaUsaElId() {
        // El id es lo que sirve para ir a mirar el dato del otro lado; un
        // indice de lista no significa nada en Redis, donde no hay lista.
        assertThatThrownBy(() -> paraRedis().toDomain(sinApellido(), "catedra:sync:professionals"))
                .hasMessageContaining("catedra:sync:professionals[15].lastName");
    }

    @Test
    @DisplayName("Un id ausente se informa antes de poder nombrar la ruta completa")
    void sinIdNoHayRuta() {
        ProfessionalDto sinId = new ProfessionalDto(
                null, 1L, "Ana", "Gomez", true, Instant.now(), Instant.now());

        assertThatThrownBy(() -> paraRedis().toDomain(sinId, DONDE))
                .isInstanceOf(MalformedPublishedCatalogException.class)
                .hasMessageContaining("professionals.id");
    }

    @Test
    @DisplayName("Una cadena en blanco cuenta como ausente")
    void elBlancoCuentaComoAusente() {
        ProfessionalDto conNombreEnBlanco = new ProfessionalDto(
                15L, 1L, "   ", "Gomez", true, Instant.now(), Instant.now());

        // Un firstName vacio pasaria el NOT NULL de la tabla y dejaria un
        // profesional sin nombre en la busqueda: es el mismo problema que un
        // campo faltante con otra forma.
        assertThatThrownBy(() -> paraRedis().toDomain(conNombreEnBlanco, DONDE))
                .hasMessageContaining("professionals[15].firstName");
    }

    @Test
    @DisplayName("Con todos los campos, los dos caminos dan el mismo dominio")
    void elMismoDatoDaElMismoDominio() {
        ProfessionalDto completo = new ProfessionalDto(
                15L, 1L, "Ana", "Gomez", true,
                Instant.parse("2026-07-01T12:00:00Z"),
                Instant.parse("2026-09-10T13:40:00Z"));

        assertThat(paraElSnapshot().toDomain(completo, DONDE))
                .usingRecursiveComparison()
                .isEqualTo(paraRedis().toDomain(completo, DONDE));
    }

    private static ProfessionalDto sinApellido() {
        return new ProfessionalDto(15L, 1L, "Ana", null, true, Instant.now(), Instant.now());
    }

    private static CatalogEntityMapper paraElSnapshot() {
        return new CatalogEntityMapper(IncompleteCatalogSnapshotException::new);
    }

    private static CatalogEntityMapper paraRedis() {
        return new CatalogEntityMapper(MalformedPublishedCatalogException::new);
    }
}

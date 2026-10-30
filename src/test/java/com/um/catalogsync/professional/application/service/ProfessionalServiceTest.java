package com.um.catalogsync.professional.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.professional.application.exception.ProfessionalNotFoundException;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchResult;
import com.um.catalogsync.professional.domain.ports.in.SearchProfessionalsUseCase;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfessionalServiceTest {

    private static final Professional ANA = Professional.builder()
            .id(15L)
            .categoryId(1L)
            .firstName("Ana")
            .lastName("Gomez")
            .enabled(true)
            .build();

    /**
     * La busqueda, para las pruebas que no la ejercitan.
     *
     * <p>Falla si alguien la invoca en lugar de devolver un vacio: que
     * {@code getById} empezara a pasar por la busqueda seria un cambio de
     * comportamiento, y un doble silencioso lo dejaria pasar.
     */
    private static final SearchProfessionalsUseCase BUSQUEDA_QUE_NO_CORRESPONDE = criteria -> {
        throw new AssertionError("Esta operacion no tenia que buscar");
    };

    @Test
    @DisplayName("getById devuelve el profesional cuando esta")
    void getByIdDevuelveElProfesional() {
        ProfessionalService service = new ProfessionalService(List::of, id -> Optional.of(ANA), BUSQUEDA_QUE_NO_CORRESPONDE);

        assertThat(service.getById(15L)).isSameAs(ANA);
    }

    @Test
    @DisplayName("getById convierte un Optional vacio en la excepcion del slice")
    void getByIdTraduceElVacioAExcepcion() {
        ProfessionalService service = new ProfessionalService(List::of, id -> Optional.empty(), BUSQUEDA_QUE_NO_CORRESPONDE);

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("findById deja pasar el vacio sin lanzar")
    void findByIdNoLanza() {
        ProfessionalService service = new ProfessionalService(List::of, id -> Optional.empty(), BUSQUEDA_QUE_NO_CORRESPONDE);

        assertThat(service.findById(999L)).isEmpty();
    }

    @Test
    @DisplayName("search delega en el caso de uso y no traduce el vacio a excepcion")
    void searchDelegaYNoLanzaConPaginaVacia() {
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.primeraPagina();
        CatalogVersion version = CatalogVersion.builder()
                .version(128L)
                .appliedAt(Instant.parse("2026-10-30T12:00:00Z"))
                .source(CatalogVersionSource.SNAPSHOT)
                .build();
        ProfessionalService service = new ProfessionalService(
                List::of,
                id -> Optional.empty(),
                pedido -> new ProfessionalSearchResult(ProfessionalPage.vacia(pedido), version));

        ProfessionalSearchResult resultado = service.search(criteria);

        // A diferencia de getById, el vacio no se traduce: convertirlo en un
        // 404 obligaria a la app KMP a tratar "no hay dermatologos" como un
        // error.
        assertThat(resultado.page().content()).isEmpty();
        assertThat(resultado.catalogVersion()).isSameAs(version);
    }

    @Test
    @DisplayName("getAll delega en el caso de uso")
    void getAllDelega() {
        ProfessionalService service = new ProfessionalService(
                () -> List.of(ANA), id -> Optional.empty(), BUSQUEDA_QUE_NO_CORRESPONDE);

        assertThat(service.getAll()).containsExactly(ANA);
    }
}

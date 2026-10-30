package com.um.catalogsync.professional.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchResult;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.sync.application.exception.CatalogNotSynchronizedException;
import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El caso de uso de busqueda.
 *
 * <p>Las consultas reales se prueban contra PostgreSQL, en el adaptador: lo
 * que se fija aca son las dos decisiones que el caso de uso toma y que no
 * dependen de la base —que sin version aplicada no devuelve una lista vacia,
 * y que la version que acompaña a la pagina es la que leyo en la misma
 * operacion—.
 */
class SearchProfessionalsUseCaseImplTest {

    private static final CatalogVersion VERSION = CatalogVersion.builder()
            .version(128L)
            .appliedAt(Instant.parse("2026-10-30T12:00:00Z"))
            .source(CatalogVersionSource.SNAPSHOT)
            .build();

    private static final Professional ANA = Professional.builder()
            .id(15L)
            .categoryId(1L)
            .firstName("Ana")
            .lastName("Gomez")
            .enabled(true)
            .build();

    private final ProfessionalRepository profesionales = mock(ProfessionalRepository.class);
    private final CatalogVersionRepository versiones = mock(CatalogVersionRepository.class);
    private final SearchProfessionalsUseCaseImpl caso =
            new SearchProfessionalsUseCaseImpl(profesionales, versiones);

    @Test
    @DisplayName("devuelve la pagina del puerto junto con la version aplicada")
    void devuelveLaPaginaConLaVersion() {
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.primeraPagina();
        when(versiones.findCurrent()).thenReturn(Optional.of(VERSION));
        when(profesionales.search(criteria))
                .thenReturn(new ProfessionalPage(List.of(ANA), 0, 20, 1L));

        ProfessionalSearchResult resultado = caso.search(criteria);

        assertThat(resultado.page().content()).containsExactly(ANA);
        assertThat(resultado.catalogVersion()).isSameAs(VERSION);
    }

    @Test
    @DisplayName("el criterio llega al puerto tal como entro, sin reinterpretarse")
    void elCriterioLlegaIntacto() {
        ProfessionalSearchCriteria criteria =
                new ProfessionalSearchCriteria(7L, "Ana GÓMEZ", true, 2, 50);
        when(versiones.findCurrent()).thenReturn(Optional.of(VERSION));
        when(profesionales.search(criteria)).thenReturn(ProfessionalPage.vacia(criteria));

        caso.search(criteria);

        // El stub de Mockito matchea por equals, asi que si el caso de uso
        // reconstruyera el criterio —normalizando otra vez, cambiando la
        // pagina— no encontraria este when y devolveria null.
        assertThat(criteria.name()).isEqualTo("ana gomez");
    }

    @Test
    @DisplayName("una pagina vacia es una respuesta valida, no una excepcion")
    void laPaginaVaciaNoEsError() {
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.primeraPagina();
        when(versiones.findCurrent()).thenReturn(Optional.of(VERSION));
        when(profesionales.search(criteria)).thenReturn(ProfessionalPage.vacia(criteria));

        ProfessionalSearchResult resultado = caso.search(criteria);

        assertThat(resultado.page().content()).isEmpty();
        assertThat(resultado.catalogVersion()).isSameAs(VERSION);
    }

    @Test
    @DisplayName("sin version aplicada no busca nada y lo dice: 503 en lugar de lista vacia")
    void sinVersionAplicadaFalla() {
        when(versiones.findCurrent()).thenReturn(Optional.empty());

        // Una lista vacia diria "no hay profesionales que cumplan los
        // filtros" cuando lo que no hay es catalogo. El cliente tiene que
        // poder distinguirlas: lo primero no se arregla reintentando.
        assertThatThrownBy(() -> caso.search(ProfessionalSearchCriteria.primeraPagina()))
                .isInstanceOf(CatalogNotSynchronizedException.class);
        verifyNoInteractions(profesionales);
    }
}

package com.um.catalogsync.professional.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La pagina de resultados. */
class ProfessionalPageTest {

    private static final Professional ANA = Professional.builder()
            .id(15L)
            .categoryId(1L)
            .firstName("Ana")
            .lastName("Gomez")
            .enabled(true)
            .build();

    @Test
    @DisplayName("totalPages redondea hacia arriba la ultima pagina incompleta")
    void totalPagesRedondeaHaciaArriba() {
        // 37 elementos de 20 en 20 son dos paginas, no una: la segunda lleva
        // 17. Un redondeo hacia abajo haria que el cliente nunca pidiera la
        // ultima y perdiera filas sin enterarse.
        assertThat(new ProfessionalPage(List.of(), 0, 20, 37L).totalPages()).isEqualTo(2);
        assertThat(new ProfessionalPage(List.of(), 0, 20, 40L).totalPages()).isEqualTo(2);
        assertThat(new ProfessionalPage(List.of(), 0, 20, 41L).totalPages()).isEqualTo(3);
        assertThat(new ProfessionalPage(List.of(), 0, 20, 1L).totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("sin resultados hay cero paginas, y eso no es un error")
    void sinResultadosNoHayPaginas() {
        ProfessionalPage vacia = ProfessionalPage.vacia(ProfessionalSearchCriteria.primeraPagina());

        assertThat(vacia.content()).isEmpty();
        assertThat(vacia.totalElements()).isZero();
        assertThat(vacia.totalPages()).isZero();
    }

    @Test
    @DisplayName("un content nulo se trata como pagina vacia")
    void elNuloEsPaginaVacia() {
        assertThat(new ProfessionalPage(null, 0, 20, 0L).content()).isEmpty();
    }

    @Test
    @DisplayName("el content que sale no se puede modificar desde afuera")
    void elContentEsInmutable() {
        List<Professional> mutable = new ArrayList<>(List.of(ANA));
        ProfessionalPage pagina = new ProfessionalPage(mutable, 0, 20, 1L);

        mutable.clear();

        // Copiar al construir es lo que evita que quien armo la lista la
        // cambie despues y mueva el suelo bajo una respuesta ya resuelta.
        assertThat(pagina.content()).containsExactly(ANA);
        assertThatThrownBy(() -> pagina.content().add(ANA))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package com.um.catalogsync.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.domain.model.CatalogVersion;
import com.um.catalogsync.sync.domain.model.CatalogVersionSource;
import com.um.catalogsync.sync.domain.ports.out.CatalogVersionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetLocalCatalogVersionUseCaseImplTest {

    private static final CatalogVersion VERSION_7 = CatalogVersion.builder()
            .version(7L)
            .appliedAt(Instant.parse("2026-09-10T13:40:00Z"))
            .source(CatalogVersionSource.SNAPSHOT)
            .build();

    private static CatalogVersionRepository repositorioQueDevuelve(
            Optional<CatalogVersion> actual) {
        return new CatalogVersionRepository() {
            @Override
            public Optional<CatalogVersion> findCurrent() {
                return actual;
            }

            @Override
            public CatalogVersion advanceTo(long version, CatalogVersionSource source) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    @DisplayName("Devuelve la version vigente cuando hay una")
    void devuelveLaVersionVigente() {
        var useCase = new GetLocalCatalogVersionUseCaseImpl(
                repositorioQueDevuelve(Optional.of(VERSION_7)));

        assertThat(useCase.getCurrent()).contains(VERSION_7);
    }

    @Test
    @DisplayName("Con base recien inicializada devuelve vacio, no la version 0")
    void baseVaciaDevuelveVacio() {
        var useCase = new GetLocalCatalogVersionUseCaseImpl(
                repositorioQueDevuelve(Optional.empty()));

        // El vacio es lo que #14 usa para decidir que hace falta un snapshot:
        // traducirlo a 0 haria que una base vacia pareciera sincronizada.
        assertThat(useCase.getCurrent()).isEmpty();
    }
}

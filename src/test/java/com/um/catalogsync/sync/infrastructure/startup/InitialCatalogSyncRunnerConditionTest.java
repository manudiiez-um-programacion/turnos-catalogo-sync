package com.um.catalogsync.sync.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.sync.application.service.CatalogSyncService;
import com.um.catalogsync.sync.application.service.CatalogVersionService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * El interruptor de la sincronizacion de arranque.
 *
 * <p>Es un criterio de aceptacion por si mismo —"el comportamiento se puede
 * desactivar por configuracion para las pruebas"— y lo que hay que
 * verificar son las dos direcciones: que la propiedad en {@code false}
 * saque el componente, y que el valor por defecto sea tenerlo. Lo segundo
 * importa tanto como lo primero: si el default fuera apagado, un entorno
 * real sin la variable arrancaria para siempre sin catalogo y sin que nada
 * lo dijera.
 *
 * <p>Se usa {@code ApplicationContextRunner} y no {@code @SpringBootTest}
 * porque lo unico que se evalua es la condicion. Un contexto completo con
 * el runner encendido ademas <i>correria</i> la sincronizacion, en paralelo
 * con el PostgreSQL compartido de la suite, y eso haria que estas dos
 * aserciones dependieran de una carrera que no tienen nada que ver con lo
 * que prueban.
 */
class InitialCatalogSyncRunnerConditionTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(CatalogVersionService.class, () -> new CatalogVersionService(Optional::empty))
            .withBean(
                    CatalogSyncService.class,
                    () -> new CatalogSyncService(trigger -> null, snapshot -> null))
            .withUserConfiguration(InitialCatalogSyncRunner.class);

    @Test
    @DisplayName("Sin configurar nada, la sincronizacion de arranque esta encendida")
    void porDefectoEstaEncendida() {
        contexto.run(ctx -> assertThat(ctx).hasSingleBean(InitialCatalogSyncRunner.class));
    }

    @Test
    @DisplayName("Con la propiedad en false, el componente no se registra")
    void seApagaPorConfiguracion() {
        contexto.withPropertyValues("sync.startup.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(InitialCatalogSyncRunner.class));
    }
}

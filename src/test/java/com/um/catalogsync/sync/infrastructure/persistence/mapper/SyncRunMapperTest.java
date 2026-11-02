package com.um.catalogsync.sync.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.um.catalogsync.sync.domain.model.SyncMode;
import com.um.catalogsync.sync.domain.model.SyncRun;
import com.um.catalogsync.sync.domain.model.SyncStatus;
import com.um.catalogsync.sync.domain.model.SyncTrigger;
import com.um.catalogsync.sync.infrastructure.persistence.entity.SyncRunEntity;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncRunMapperTest {

    private static final Instant EMPEZO = Instant.parse("2026-09-10T13:44:00Z");
    private static final Instant TERMINO = Instant.parse("2026-09-10T13:45:00Z");

    private final SyncRunMapper mapper = new SyncRunMapper();

    @Test
    @DisplayName("Null entra y null sale, en los dos sentidos")
    void guardaDeNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    @DisplayName("Convierte una corrida terminada en los dos sentidos")
    void vaYVuelve() {
        SyncRun original = SyncRun.builder()
                .id(3L)
                .mode(SyncMode.FULL)
                .triggeredBy(SyncTrigger.DISCONTINUITY)
                .status(SyncStatus.SUCCEEDED)
                .fromVersion(5L)
                .toVersion(7L)
                .startedAt(EMPEZO)
                .finishedAt(TERMINO)
                .build();

        SyncRun ida = mapper.toDomain(mapper.toEntity(original));

        assertThat(ida.getId()).isEqualTo(3L);
        assertThat(ida.getMode()).isEqualTo(SyncMode.FULL);
        assertThat(ida.getTriggeredBy()).isEqualTo(SyncTrigger.DISCONTINUITY);
        assertThat(ida.getStatus()).isEqualTo(SyncStatus.SUCCEEDED);
        assertThat(ida.getFromVersion()).isEqualTo(5L);
        assertThat(ida.getToVersion()).isEqualTo(7L);
        assertThat(ida.getStartedAt()).isEqualTo(EMPEZO);
        assertThat(ida.getFinishedAt()).isEqualTo(TERMINO);
    }

    @Test
    @DisplayName("Una corrida en curso conserva los tres nulos")
    void corridaEnCurso() {
        SyncRunEntity entidad = SyncRunEntity.builder()
                .id(1L)
                .mode(SyncMode.FULL.name())
                .triggeredBy(SyncTrigger.STARTUP.name())
                .status(SyncStatus.RUNNING.name())
                .startedAt(EMPEZO)
                .build();

        SyncRun corrida = mapper.toDomain(entidad);

        // Los tres nulos significan algo: no habia version previa, no se
        // llego a ninguna version, todavia no termino.
        assertThat(corrida.getFromVersion()).isNull();
        assertThat(corrida.getToVersion()).isNull();
        assertThat(corrida.getFinishedAt()).isNull();
        assertThat(corrida.isRunning()).isTrue();
        assertThat(corrida.isFinished()).isFalse();
    }

    @Test
    @DisplayName("Un valor que esta aplicacion nunca escribe es estado corrupto")
    void valorQueNoEscribimos() {
        SyncRunEntity entidad = SyncRunEntity.builder()
                .id(1L)
                .mode("PARCIAL")
                .triggeredBy(SyncTrigger.STARTUP.name())
                .status(SyncStatus.RUNNING.name())
                .startedAt(EMPEZO)
                .build();

        // No es una inconsistencia de datos externos como un dia de la
        // semana desconocido: estos valores los escribimos nosotros y la
        // columna tiene un CHECK, asi que solo puede venir de que alguien
        // escribio en la tabla por fuera de la aplicacion.
        assertThatThrownBy(() -> mapper.toDomain(entidad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mode")
                .hasMessageContaining("PARCIAL");
    }

    @Test
    @DisplayName("Nombra la columna que esta corrupta y no solo el valor")
    void nombraLaColumna() {
        SyncRunEntity entidad = SyncRunEntity.builder()
                .id(1L)
                .mode(SyncMode.FULL.name())
                .triggeredBy("PORQUE_SI")
                .status(SyncStatus.RUNNING.name())
                .startedAt(EMPEZO)
                .build();

        assertThatThrownBy(() -> mapper.toDomain(entidad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("triggered_by");
    }
}

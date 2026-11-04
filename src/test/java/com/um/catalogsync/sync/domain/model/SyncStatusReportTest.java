package com.um.catalogsync.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las reglas que derivan el estado y el atraso.
 *
 * <p>Es el criterio central de #28: los cuatro valores de {@link SyncHealth}
 * tienen que ser <b>distinguibles</b>, porque significan cosas distintas para
 * quien consume. "Atrasado" se recupera solo y "degradado" necesita que vuelva
 * la catedra, asi que una regla que los confundiera haria que nadie supiera si
 * tiene que esperar o ir a mirar.
 *
 * <p>Sin dobles y sin contexto: son reglas de un record sobre sus propios
 * campos.
 */
class SyncStatusReportTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T18:30:00Z");

    @Test
    @DisplayName("Al dia: la version local esta en la que publica la catedra")
    void alDia() {
        SyncStatusReport reporte = reporte(local(128), corridaExitosa(128), origen(128, 120));

        assertThat(reporte.health()).isEqualTo(SyncHealth.UP_TO_DATE);
        assertThat(reporte.versionsBehind()).isZero();
    }

    @Test
    @DisplayName("Atrasado: la catedra publico versiones que la copia local no aplico")
    void atrasado() {
        SyncStatusReport reporte = reporte(local(120), corridaExitosa(120), origen(128, 110));

        // Se recupera solo: lo trae la proxima notificacion o el chequeo
        // periodico. Quien consulta no tiene que hacer nada.
        assertThat(reporte.health()).isEqualTo(SyncHealth.BEHIND);
        assertThat(reporte.versionsBehind()).isEqualTo(8);
    }

    @Test
    @DisplayName("Reconstruyendo: hay una corrida abierta, aunque este atrasado")
    void reconstruyendo() {
        SyncStatusReport reporte = reporte(local(120), corridaEnCurso(), origen(128, 110));

        // Decir "atrasado" de algo que se esta poniendo al dia en este
        // momento seria cierto e inutil: por eso la corrida abierta gana.
        assertThat(reporte.health()).isEqualTo(SyncHealth.REBUILDING);
        // El atraso se sigue informando: es el que todavia falta cerrar.
        assertThat(reporte.versionsBehind()).isEqualTo(8);
    }

    @Test
    @DisplayName("Degradado: no se pudo leer el estado del origen")
    void degradadoPorOrigenInalcanzable() {
        SyncStatusReport reporte = new SyncStatusReport(
                local(128), corridaExitosa(128), SyncOriginState.UNREACHABLE, null, List.of());

        // Es "el estado local no se puede verificar" de §6.2. La copia local
        // sigue sirviendo, pero no hay forma de saber cuan vieja es, asi que
        // tampoco hay atraso que informar.
        assertThat(reporte.health()).isEqualTo(SyncHealth.DEGRADED);
        assertThat(reporte.versionsBehind()).isNull();
    }

    @Test
    @DisplayName("Degradado tambien cuando la catedra no publico ninguna version")
    void degradadoPorqueElOrigenNoPublicoNada() {
        SyncStatusReport reporte = new SyncStatusReport(
                local(128), corridaExitosa(128), SyncOriginState.NOT_PUBLISHED, null, List.of());

        // Redis contesto, asi que la integracion no esta caida, pero sin
        // version publicada no hay con que comparar: el estado local sigue
        // sin poder verificarse.
        assertThat(reporte.health()).isEqualTo(SyncHealth.DEGRADED);
    }

    @Test
    @DisplayName("Degradado: el ultimo intento fallo, aunque el origen se lea bien")
    void degradadoPorElUltimoIntentoFallido() {
        SyncStatusReport reporte = reporte(local(120), corridaFallida(), origen(128, 110));

        // Que Redis conteste no significa que la sincronizacion funcione: el
        // snapshot va por REST y la version local puede estar trabada por un
        // 401 de la cuenta tecnica. Sin esta regla, ese caso se informaria
        // como "atrasado, se recupera solo" y nadie iria a mirar.
        assertThat(reporte.health()).isEqualTo(SyncHealth.DEGRADED);
        assertThat(reporte.versionsBehind()).isEqualTo(8);
    }

    @Test
    @DisplayName("Una corrida fallida vieja no degrada si la ultima salio bien")
    void laCorridaFallidaViejaNoDegrada() {
        SyncStatusReport reporte = reporte(
                local(128),
                corridaExitosa(128),
                origen(128, 120),
                List.of(unError(SyncErrorCode.SNAPSHOT_UNAVAILABLE)));

        // El historial de errores no es el estado: lo que decide es como
        // salio el ultimo intento. Si no, una falla de hace un mes dejaria al
        // servicio degradado para siempre.
        assertThat(reporte.health()).isEqualTo(SyncHealth.UP_TO_DATE);
        assertThat(reporte.recentErrors()).hasSize(1);
    }

    @Test
    @DisplayName("Sin version local y sin fallas: atrasado, no degradado")
    void sinVersionLocalEsAtraso() {
        SyncStatusReport reporte = new SyncStatusReport(
                null, null, SyncOriginState.KNOWN, origen(128, 120), List.of());

        // Falta la primera sincronizacion y la trae el chequeo periodico sin
        // que nadie haga nada: es un atraso, no una falla.
        assertThat(reporte.health()).isEqualTo(SyncHealth.BEHIND);
        // Nulo y no 128: no hay desde donde contar. Un numero acá afirmaria
        // que la copia local esta en la version 0, que es otra cosa.
        assertThat(reporte.versionsBehind()).isNull();
    }

    @Test
    @DisplayName("Una version local por encima de la publicada no da atraso negativo")
    void nuncaAtrasoNegativo() {
        // Pasa de verdad: §14.3 publica el mismo numero en el hash y en la
        // clave suelta, y nada promete que esten sincronizados en un instante
        // dado. Un numero negativo seria un reporte que no se entiende.
        SyncStatusReport reporte = reporte(local(129), corridaExitosa(129), origen(128, 120));

        assertThat(reporte.health()).isEqualTo(SyncHealth.UP_TO_DATE);
        assertThat(reporte.versionsBehind()).isZero();
    }

    @Test
    @DisplayName("La lista de errores nula llega vacia y no rompe el reporte")
    void erroresNulosLleganVacios() {
        SyncStatusReport reporte = new SyncStatusReport(
                local(128), corridaExitosa(128), SyncOriginState.KNOWN, origen(128, 120), null);

        assertThat(reporte.recentErrors()).isEmpty();
    }

    private static SyncStatusReport reporte(
            CatalogVersion local, SyncRun ultima, PublishedCatalogMetadata origen) {
        return reporte(local, ultima, origen, List.of());
    }

    private static SyncStatusReport reporte(
            CatalogVersion local,
            SyncRun ultima,
            PublishedCatalogMetadata origen,
            List<SyncError> errores) {
        return new SyncStatusReport(local, ultima, SyncOriginState.KNOWN, origen, errores);
    }

    private static CatalogVersion local(long version) {
        return CatalogVersion.builder()
                .version(version)
                .appliedAt(AHORA)
                .source(CatalogVersionSource.INCREMENTAL)
                .build();
    }

    private static PublishedCatalogMetadata origen(long actual, long masAntigua) {
        return PublishedCatalogMetadata.builder()
                .currentVersion(actual)
                .oldestAvailableVersion(masAntigua)
                .publishedAt(AHORA)
                .build();
    }

    private static SyncRun corridaExitosa(long hasta) {
        return corrida(SyncStatus.SUCCEEDED).toVersion(hasta).finishedAt(AHORA).build();
    }

    private static SyncRun corridaFallida() {
        return corrida(SyncStatus.FAILED).finishedAt(AHORA).build();
    }

    private static SyncRun corridaEnCurso() {
        return corrida(SyncStatus.RUNNING).build();
    }

    private static SyncRun.SyncRunBuilder corrida(SyncStatus estado) {
        return SyncRun.builder()
                .id(412L)
                .mode(SyncMode.INCREMENTAL)
                .triggeredBy(SyncTrigger.EVENT)
                .decisionReason(SyncDecisionReason.BEHIND_CURRENT_VERSION)
                .status(estado)
                .fromVersion(120L)
                .startedAt(AHORA.minusSeconds(2));
    }

    private static SyncError unError(SyncErrorCode codigo) {
        return SyncError.builder()
                .syncRunId(411L)
                .occurredAt(AHORA.minusSeconds(600))
                .code(codigo)
                .detail("la catedra no respondio")
                .build();
    }
}

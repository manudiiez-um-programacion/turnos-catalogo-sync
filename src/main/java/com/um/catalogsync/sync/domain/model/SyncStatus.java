package com.um.catalogsync.sync.domain.model;

/**
 * En que termino una corrida de sincronizacion, o que sigue corriendo.
 *
 * <p>Son los tres valores del CHECK {@code ck_sync_run_status}, y la tabla
 * ata el estado a {@code finished_at} con otro CHECK: {@link #RUNNING} sin
 * fin, los otros dos con fin. Esa invariante no se sostiene con disciplina
 * del codigo sino por construccion, porque el puerto
 * {@code SyncRunRepository} no ofrece ninguna forma de escribir un estado
 * terminal sin su instante de fin.
 */
public enum SyncStatus {

    /**
     * Arranco y todavia no termino.
     *
     * <p>Una fila que queda en este estado para siempre es informacion, no
     * un bug del registro: significa que el proceso se cayo en medio de una
     * sincronizacion. La copia local sigue consistente —la unidad de trabajo
     * se habra ido en el rollback— pero el historial deja ver que paso.
     */
    RUNNING,

    /** Termino y la version local avanzo. */
    SUCCEEDED,

    /**
     * Termino mal. La version local no avanzo y la copia anterior quedo
     * intacta; el motivo esta en {@code sync_error}.
     */
    FAILED
}

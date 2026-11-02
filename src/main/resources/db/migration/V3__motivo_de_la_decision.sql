-- Por que se eligio sincronizar asi.
--
-- sync_run ya decia QUE se hizo (mode) y QUIEN lo pidio (triggered_by). Lo
-- que faltaba es POR QUE se eligio ese modo, que es lo que el enunciado pide
-- registrar cuando se detecta una discontinuidad (§6.2, §18.2): con mode =
-- FULL y triggered_by = DISCONTINUITY se sabe que hubo una, pero no si fue
-- porque la version local quedo fuera de la ventana retenida, porque faltaba
-- una version del rango o porque no se pudo verificar el estado del origen.
-- Las tres llevan al mismo lugar y significan cosas distintas para quien
-- revisa.

ALTER TABLE sync_run
    ADD COLUMN decision_reason VARCHAR(32);

-- Nullable y sin valor por defecto: las corridas que ya existen se hicieron
-- antes de que la decision se registrara, y ponerles un motivo inventado
-- seria peor que dejarlas sin uno. Las nuevas lo traen siempre.
COMMENT ON COLUMN sync_run.decision_reason IS
    'Por que se eligio este modo de sincronizacion. Nulo en las corridas anteriores a V3.';

-- Vocabulario propio, asi que se restringe: es la misma razon por la que
-- mode, status y triggered_by tienen CHECK y processed_event.source no.
ALTER TABLE sync_run
    ADD CONSTRAINT ck_sync_run_decision_reason CHECK (decision_reason IS NULL OR decision_reason IN (
        'NO_LOCAL_VERSION',
        'VERSION_OUT_OF_WINDOW',
        'MISSING_VERSION',
        'STATE_UNVERIFIABLE',
        'BEHIND_CURRENT_VERSION',
        'REQUESTED'
    ));

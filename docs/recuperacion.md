# Recuperación: duplicados, pérdidas, fuera de orden, reinicios e indisponibilidad

Cierra la viñeta 6 de §12 del enunciado ("una explicación del procesamiento
idempotente y la recuperación ante mensajes duplicados, perdidos o fuera de
orden"). Issue #35.

**Lo que acá no está.** El *por qué* de cada decisión —dónde va la
transacción, por qué manda Redis y no el número del evento, qué alternativas
se descartaron— está en [ADR 0004](adr/0004-sincronizacion-e-idempotencia.md)
y no se repite. La explicación narrativa, con diagramas de secuencia, está en
[`docs/sincronizacion.md`](sincronizacion.md), particularmente su
[§7](sincronizacion.md#7-idempotencia-duplicados-perdidos-y-fuera-de-orden) y
[§8](sincronizacion.md#8-la-discontinuidad-con-números). Este documento es el
índice que falta: un comportamiento por caso, con la prueba que lo demuestra.
Formato `Clase#método`.

## 1. Mensaje duplicado (§18.1)

El mismo `eventId` entregado dos veces no repite ningún efecto, y el segundo
intento confirma el offset igual que el primero. La deduplicación es un
`INSERT` contra la clave primaria de `processed_event`, no un
"consultar y después escribir": no hay ventana de carrera.

| Prueba |
| --- |
| `HandleCatalogUpdatedUseCaseImplTest#eventoDuplicado`, `#elDuplicadoNoSincroniza` |
| `JpaProcessedEventRepositoryAdapterTest#elSegundoEsDuplicado` |
| `CatalogUpdatedListenerTest#elDuplicadoConfirma` |
| `CatalogUpdatedConsumerIntegrationTest#elDuplicadoEnElTopicSeAbsorbe` |
| `SyncRecoveryEndToEndTest#elDuplicadoEnElTopicNoDuplicaEfectos` (con Kafka, Redis y PostgreSQL reales) |
| `SyncMetricsTest#cuentaLosDuplicados` — la evidencia de idempotencia que pide §11 |

## 2. Mensaje perdido

Una notificación que nunca llega no deja la copia local atrasada para
siempre: el chequeo periódico (`CatalogReconciliationJob`) compara la versión
local contra `current-version` sin depender de que haya llegado ningún
evento. El peor caso es un intervalo de atraso, no una pérdida definitiva.

| Prueba |
| --- |
| `ReconciliationIntegrationTest#recuperaLaNotificacionPerdida` |
| `ReconciliationIntegrationTest#sinAtrasoNoRegistraNada` (no hace trabajo de más cuando no hace falta) |
| `ReconciliationIntegrationTest#dejaLaCorridaEnElHistorial` |
| `SyncStatusReportTest#atrasado` — cómo se informa mientras el atraso no se cerró |

## 3. Mensaje fuera de orden

El destino de una sincronización nunca sale del número que trae el evento:
lo decide la comparación contra el estado publicado en Redis
(`current-version`, `oldest-available-version`). Esto cubre los tres casos de
desorden a la vez, con el mismo mecanismo:

- **Evento adelantado** (evento 8, Redis todavía en 7): no hay nada que
  aplicar; la sincronización no hace nada y el `eventId` queda consumido sin
  efecto. Lo recupera el chequeo periódico.
- **Evento atrasado** (evento 8, Redis ya en 9): se aplica hasta la 9.
- **Evento viejo** (evento 5 después de haber aplicado la 8): no retrocede
  nada.

| Prueba |
| --- |
| `SynchronizeCatalogUseCaseImplTest#yaEstaAlDia`, `#laVersionLocalMasNueva` — nada que hacer cuando la copia ya alcanzó o superó lo publicado (evento adelantado) |
| `HandleCatalogUpdatedUseCaseImplTest#elEventoNuevoSincroniza`, `#elDuplicadoNoSincroniza` — el handler llama a `synchronize()` sin pasarle la versión del evento |
| `JpaCatalogVersionRepositoryAdapterTest#retrocederPorIncrementalSeRechaza` — un intento de retroceder por vía incremental (evento viejo) se rechaza en el modelo |
| `ApplyIncrementalChangesUseCaseImplTest#noRetrocede` |

## 4. Reinicio

Dos escenarios de arranque distintos, los dos cubiertos:

- **Base vacía** (primer arranque, o después de perder el volumen): pide el
  snapshot completo automáticamente.
- **Reinicio con una sincronización a medias**: el proceso se cae con una
  corrida en `sync_run.status = RUNNING` y sin confirmar el offset del
  mensaje que la disparó. Al volver a arrancar, Kafka reentrega ese mensaje
  (el offset nunca se confirmó) y la deduplicación lo trata como lo que es:
  un reintento del mismo trabajo, no un duplicado a descartar.

| Prueba |
| --- |
| `FullSyncEndToEndTest#baseVaciaQuedaSincronizada` — arranque con base vacía |
| `SyncRecoveryEndToEndTest#elReinicioRecuperaElAtraso` — reinicio con corrida a medias; la copia queda consistente y el historial no se toca |
| `FullSyncIntegrationTest#elFalloConBaseVaciaNoDejaVersion` — un fallo durante ese primer arranque no deja una versión a medias |

## 5. Indisponibilidad de la cátedra, Redis o Kafka

El servicio sigue sirviendo búsquedas sobre la copia local aunque la cátedra,
Redis o Kafka estén caídos: las búsquedas nunca dependen de esas
integraciones (§6.2). Lo que sí se degrada es la frescura de la copia, y el
endpoint de estado lo informa en lugar de esconderlo.

**Cátedra REST**: timeout configurado, reintento acotado de la llamada del
snapshot (issue #31) y abandono explícito al agotarse.

| Prueba |
| --- |
| `RestCatalogSnapshotAdapterTest#timeoutDeLectura` |
| `RestCatalogSnapshotAdapterTest#unaFallaTransitoriaSeReintenta`, `#alAgotarLosIntentosSeAbandona` |
| `FullSyncEndToEndTest#unaFallaTransitoriaSeReintentaYTerminaBien` |
| `FullSyncEndToEndTest#seAgotanLosReintentosYUnaCorridaPosteriorRecupera` — una corrida posterior (del chequeo periódico) recupera lo que la primera no pudo |
| `FullSyncEndToEndTest#despuesDelFalloLaBusquedaSigueRespondiendo`, `#ningunaBusquedaHablaConLaCatedra` |

**Token técnico vencido**: se reintenta reemitiéndolo una sola vez; un 401
persistente no se reintenta en bucle.

| Prueba |
| --- |
| `TechnicalSessionCacheTest#reautenticaUnaVezAnteTokenRechazado` |
| `TechnicalSessionCacheTest#lasCredencialesRechazadasNoSeReintentan` — un 401 por credenciales, en cambio, no se reintenta |
| `RestIntegrationConfigAdapterTest#tokenRechazado` — qué distingue este 401 de uno por credenciales malas |

**Redis**: el chequeo periódico no revienta si no responde.

| Prueba |
| --- |
| `ReconciliationIntegrationTest#siRedisNoRespondeNoRevienta` |

**Cómo se informa hacia afuera** (`GET /api/internal/sync/status`, `health`):

| Prueba |
| --- |
| `SyncStatusReportTest#degradadoPorOrigenInalcanzable`, `#degradadoPorqueElOrigenNoPublicoNada` |
| `SyncStatusReportTest#degradadoPorElUltimoIntentoFallido`, `#laCorridaFallidaViejaNoDegrada` |
| `SyncStatusApiIntegrationTest#sinCatalogoAplicado`, `#conLaCatedraCaida` — responde `200` siempre, nunca un error de infraestructura |

**Cuenta técnica sin aprovisionar** (caso particular de indisponibilidad: la
integración no está lista, no que esté caída):

| Prueba |
| --- |
| `IntegrationConfigStartupVerifierTest#conLaCuentaSinHabilitarFallaElArranque` |

## Las tres integraciones a la vez

`SyncRecoveryEndToEndTest` (issue #30) es la única prueba donde PostgreSQL,
Redis, Kafka y la cátedra REST (simulada con WireMock) participan juntos, y
por eso aparece en varias filas de arriba: es la que puede demostrar la caída
real entre persistir y confirmar el offset, con reentrega real de Kafka al
mismo consumer group. Para correrla sola:

```bash
./mvnw test -Dtest=SyncRecoveryEndToEndTest
```

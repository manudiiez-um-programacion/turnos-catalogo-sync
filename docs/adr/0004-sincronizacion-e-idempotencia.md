# ADR 0004 — Estrategia de sincronización e idempotencia

- **Estado:** aceptada
- **Fecha:** 2026-11-04
- **Issue:** #29
- **Decisiones del proyecto que cierra:** ninguna de las listadas (D1–D8).
  Registra las decisiones que se tomaron y se probaron a lo largo de M2 y M3
  (issues #12–#28), que hasta ahora vivían solo en el javadoc de las clases.

> **Sobre el número.** El issue #29 pide el archivo
> `0002-sincronizacion-e-idempotencia.md`, pero `0002` ya está tomado por la
> decisión de identidad y autenticación, y `0003` por la instancia de
> PostgreSQL compartida. La numeración es correlativa
> ([README](README.md#convenciones)), así que este ADR es el `0004`.

## Contexto

§12 del enunciado exige dos explicaciones escritas: la estrategia de
sincronización completa e incremental, y el procesamiento idempotente con la
recuperación ante mensajes duplicados, perdidos o fuera de orden. La narrativa
está en [`docs/sincronizacion.md`](../sincronizacion.md); este ADR registra las
**decisiones** que la sostienen y por qué se descartaron las alternativas.

Las restricciones que las condicionan, todas externas:

- **§6.1 y §6.2.** La versión local avanza solo cuando la unidad de trabajo
  terminó completa. Si hay discontinuidad, una versión fuera de la ventana
  disponible o un estado local que no se puede verificar, corresponde
  sincronización completa. Las búsquedas nunca consultan al servicio central.
- **§15 y §16.** Kafka solo **notifica**; los datos se leen de Redis o del
  snapshot. La entrega es **al menos una vez**, así que hay que deduplicar por
  `eventId`, y el offset se confirma **después** de persistir.
- **§18.** Los escenarios concretos: notificación duplicada (§18.1), versión
  fuera de la ventana retenida (§18.2) y reintentos acotados (§18.4).
- **D6 (ZeroTier).** El servidor de la cátedra se alcanza por una red privada
  con ~228 ms de ida y vuelta medidos, y no se alcanza desde dentro de Docker.
  Toda llamada de red cuesta, y la red caída es el fallo **más frecuente**.

## Decisión

### 1. La unidad de trabajo es el snapshot completo, o **una versión** incremental

Hay exactamente dos transacciones posibles, las dos declaradas en la capa de
aplicación y no en un adaptador:

| Camino | Clase con `@Transactional` | Qué abarca |
| --- | --- | --- |
| Completa | `ApplyCatalogSnapshotUseCaseImpl` | vaciar y reescribir las tres colecciones + avanzar la versión |
| Incremental | `ApplyCatalogVersionUseCaseImpl` | las bajas y altas de **una** versión + avanzar la versión |

Los cuatro adaptadores de persistencia declaran la propagación por defecto
(`REQUIRED`), así que se suman a esa transacción en lugar de abrir una propia.

**Un atraso de diez versiones son diez transacciones, no una.** Cada versión es
su propia unidad: si la séptima falla, las seis anteriores quedan aplicadas y la
versión local queda en la sexta, que es un estado consistente desde el cual
reintentar.

**Lo que queda deliberadamente afuera de la transacción:** traer el snapshot por
REST, leer los cambios y las entidades de Redis, y registrar la corrida en
`sync_run`. Las dos primeras porque son cientos de milisegundos de red por
llamada (D6) y adentro mantendrían tomados los locks de las tres tablas todo ese
tiempo. La tercera porque el registro **no tiene que irse con el rollback**: un
historial que desaparece cuando hay un error es peor que no tener historial, así
que `JpaSyncRunRepositoryAdapter` declara `REQUIRES_NEW`.

### 2. La versión local es la última escritura de la transacción, y solo el snapshot la retrocede

`catalog_version` tiene una sola fila, garantizada por un `CHECK` sobre un id
fijo. `advanceTo` participa de la transacción de quien llama (`REQUIRED`), así
que la regla de §6.1 no se programa: se consigue por construcción. Si cualquier
paso anterior falla, el avance no se ejecuta; si falla el commit, no queda.

La regla de retroceso vive en el modelo (`CatalogVersion.allowsAdvanceTo`):

- `INCREMENTAL` **nunca** retrocede. Un incremento hacia atrás no tiene
  significado válido: solo puede venir de un evento reordenado o de un bug, y
  aceptarlo dejaría la versión describiendo datos que la copia no tiene.
- `SNAPSHOT` **sí** puede retroceder. Es el camino de la reconstrucción: cuando
  la copia local se reemplaza entera, la versión pasa a ser la del snapshot, y
  esa puede ser menor que la anotada. Negarse sería peor —dejaría la versión
  mintiendo sobre datos ya reemplazados—.
- Avanzar a la **misma** versión está permitido: reprocesar no debe alterar el
  resultado final (§18.1), y la versión no cambia.

### 3. Deduplicación por `eventId`: **insertar es la pregunta**

`processed_event` tiene el `eventId` como clave primaria. `recordIfNew` no
consulta y después escribe: inserta y mira cuántas filas cambiaron —una es
nuevo, cero es duplicado—. Un `existsById` seguido de un `save` dejaría una
ventana entre las dos llamadas por la que dos entregas del mismo mensaje
pasarían las dos, que es exactamente el caso que la deduplicación cubre. La
unicidad no la sostiene el código sino la tabla.

El registro va en la **misma transacción** que el caso de uso (`REQUIRED`): si
lo que vino después falla, el evento tampoco queda registrado y el reintento lo
toma como nuevo. Con `REQUIRES_NEW` quedarían eventos marcados como procesados
cuyos efectos nunca ocurrieron, que es la peor combinación posible.

**Retención:** `PROCESSED_EVENTS_RETENTION`, 30 días por defecto, purgada cada
6 horas por `ProcessedEventRetentionJob`. La única condición que la ventana
tiene que cumplir es ser **más larga que el atraso máximo con el que un
duplicado puede llegar**, que lo acota la retención del topic. Borrar antes de
tiempo sería peor que no borrar: un `eventId` purgado que después se reentrega
se procesaría como nuevo.

### 4. Orden de aplicación: una versión por vez, sin saltear ninguna

El recorrido va de `versionLocal + 1` hasta el destino, y **se detiene en la
primera versión cuya clave de cambios no está** (`ApplyIncrementalChangesUseCaseImpl`).
No la saltea: cada versión describe una diferencia contra la anterior, así que
un salto deja un hueco que nada posterior corrige. Lo informa en el resultado
(`IncrementalSyncResult.needsFullSync`) y quien decide pide el snapshot.

Dentro de una versión el orden lo imponen las FK, que son `RESTRICT` en los dos
sentidos (categoría ← profesional ← horario): **se borra de hijo a padre y se
escribe de padre a hijo**, y los borrados van antes de las escrituras. Eso
resuelve el caso del profesional cuya categoría llega en la misma versión.

Una versión con los tres arrays vacíos es válida (§14.5) y **también hace
avanzar la versión local**. Saltearla dejaría la copia atrás del origen para
siempre y el próximo recorrido volvería a encontrarse la misma clave vacía.

Un id que está en los cambios pero ya no está en su hash se aplica como **baja**:
el hash guarda el estado actual, no el histórico. Es distinto de la baja
**lógica**, que llega como entidad con `enabled` en `false` y se conserva
(§14.4).

### 5. Criterio de discontinuidad: tres datos y un árbol

La decisión se toma en `SynchronizeCatalogUseCaseImpl` con la versión local,
`current-version` y `oldest-available-version`, en ese orden de importancia:

| Situación | Qué se hace | Motivo registrado |
| --- | --- | --- |
| No se pudo leer el estado del origen | completa | `STATE_UNVERIFIABLE` |
| No hay versión local | completa | `NO_LOCAL_VERSION` |
| La cátedra no publicó ninguna versión | **nada** | — |
| Versión local por debajo de la ventana retenida | completa | `VERSION_OUT_OF_WINDOW` |
| La versión local ya cubre la publicada | **nada** | — |
| Si no | incremental; si se detiene, completa | `BEHIND_CURRENT_VERSION` / `MISSING_VERSION` |

El orden de las ramas no es decorativo: verificar el origen va primero porque
sin ese dato ninguna comparación posterior tiene sentido, y "no hay versión
local" va antes que la ventana porque la comparación necesita una versión local
con la que comparar.

La regla de la ventana vive en el modelo (`PublishedCatalogMetadata.canCatchUpFrom`)
porque es del contrato y no del proceso: una versión local **igual** a
`oldest-available-version` todavía sirve, porque lo que hacen falta son los
cambios *posteriores* a ella y esos están.

**Que la cátedra no publique metadata no dispara un snapshot.** No hay destino
al que llegar, y pedir uno significaría reconstruir la copia local cada pocos
minutos contra un origen que no dice nada.

### 6. El offset se confirma después de persistir

`enable.auto.commit` en `false` y modo `MANUAL_IMMEDIATE`: el único que confirma
es `CatalogUpdatedListener`, después de que el caso de uso —transaccional—
volvió. Con confirmación automática el cliente commitearía en segundo plano cada
cinco segundos y un mensaje entregado pero no procesado podría quedar
confirmado: una notificación perdida sin dejar rastro.

`auto.offset.reset` en `earliest`: un consumer group nuevo lee el topic desde el
principio. Con `latest` se perderían las notificaciones publicadas antes del
primer arranque. Procesar de más es inofensivo porque la deduplicación absorbe
lo ya visto.

Las tres clases de final, que son tres decisiones distintas:

| Qué pasó | ¿Confirma el offset? | Por qué |
| --- | --- | --- |
| Procesado o duplicado | **sí** | §18.1 lo dice: reconocer el duplicado y confirmar. No confirmarlo lo dejaría reentregándose para siempre |
| Mensaje que no cumple §15.3 | **sí**, salteándolo con un `ERROR` | No se va a arreglar en el próximo intento, y reintentarlo trabaría la partición —y todas las notificaciones posteriores— indefinidamente |
| Falla transitoria (base, Redis) | **no** | Es lo que hace que Kafka lo reentregue |

Al agotarse los reintentos de un mensaje (§18.4) el consumidor **se detiene** en
lugar de saltearlo: el offset nunca se confirmó, así que el reintento periódico
de arranque lo vuelve a levantar y Kafka reentrega desde el último offset
confirmado.

### 7. Timeouts y reintentos

Todos externalizados, sin valores por defecto escondidos en el código:

| Qué | Propiedad | Valor | Por qué |
| --- | --- | --- | --- |
| Conexión REST a la cátedra | `catedra.api.connect-timeout` | 5 s | |
| Lectura REST (incluye el snapshot) | `catedra.api.read-timeout` | 20 s | holgado: ~228 ms de RTT y un snapshot completo (D6) |
| Conexión a Redis | `catedra.redis.connect-timeout` | 5 s | |
| Comando de Redis | `catedra.redis.command-timeout` | 10 s | en cero o negativo Lettuce esperaría sin límite |
| Espera del `poll` de Kafka | `catedra.kafka.poll-timeout` | 3 s | |
| Reintento de engancharse al topic | `catedra.kafka.startup-retry-interval` | 30 s | el arranque no puede depender de la red privada |
| Espera entre reintentos de un mensaje | `catedra.kafka.retry-interval` | 5 s | |
| Reintentos de un mismo mensaje | `catedra.kafka.max-retries` | 3 | acotados: §18.4 prohíbe ciclos ilimitados |
| Chequeo periódico de versiones | `sync.reconciliation.interval` | 5 min | peor caso de cuánto tarda en notarse un aviso perdido |

**Qué se reintenta y qué no**, que es la parte que importa:

- **Un mensaje de Kafka**: hasta `max-retries` veces, y después el consumidor se
  detiene sin confirmar.
- **Una sincronización fallida**: no se reintenta en el momento. La recupera la
  comparación de versiones del chequeo periódico, que no depende de que llegue
  ningún evento. Un reintento inmediato contra una cátedra caída solo agregaría
  carga sobre un enlace de 228 ms.
- **El token técnico vencido**: se reintenta **una vez** la llamada, reemitiendo
  el token (`RestIntegrationConfigAdapter`). Si el token recién emitido también
  falla, el 401 es correcto y no hay nada que reintentar.
- **La llamada REST del snapshot**: hoy **no** tiene reintentos ni backoff
  propios. Es trabajo del issue #31, y está declarado como tal en el javadoc de
  `RestCatalogSnapshotAdapter`.

### 8. Una sola sincronización a la vez, y una red de seguridad

Tres cosas pueden pedir sincronizar: una notificación de Kafka, el chequeo
periódico y el arranque. El lock vive en `SynchronizeCatalogUseCaseImpl`, que es
el único lugar por donde pasan las tres, y **el que no lo consigue se va en
lugar de esperar**: lo que iba a hacer lo está haciendo el otro, y encolarse
solo lograría repetir la misma comparación un rato después. Es un
`ReentrantLock` en proceso, que alcanza con una sola instancia; con más de una
habría que moverlo a la base (`SELECT ... FOR UPDATE` sobre `catalog_version`) y
esta clase sería el único lugar a tocar.

El chequeo periódico (`CatalogReconciliationJob`) es la red de seguridad del
sistema entero: cubre la notificación que nunca llegó, la sincronización que
falló después de que el evento quedó registrado, y el arranque después de una
caída larga (§8). Cuando no hay nada que hacer no registra corrida ni escribe
nada por encima de `DEBUG`.

## Qué pasa si Redis y la notificación se contradicen

Es un caso real: el evento puede traer `newVersion: 8` mientras
`catedra:sync:current-version` todavía informa `7`, porque la publicación en
Redis y la notificación en Kafka no son atómicas entre sí.

**Manda Redis.** La `newVersion` del evento **no se usa como destino**:
`HandleCatalogUpdatedUseCaseImpl` llama a `synchronize()` sin pasarla, y el
destino lo decide la comparación contra el estado del origen. Es lo que dice
§15.3 —el evento notifica, los datos se leen de Redis o del snapshot— y vale en
los dos sentidos:

- **El evento adelantado** (evento 8, Redis 7): no hay nada que aplicar todavía.
  La sincronización no encuentra atraso y no hace nada. El `eventId` ya quedó
  registrado, así que una reentrega se reconoce como duplicado y **esa
  notificación no se vuelve a intentar**. Lo recupera el chequeo periódico en
  cuanto Redis publique la 8, que es exactamente para lo que existe.
- **El evento atrasado** (evento 8, Redis 9): se aplica hasta la 9. El número
  del mensaje no limita el destino.

La consecuencia hay que decirla en voz alta: **una notificación puede no
producir ningún efecto**, y eso es correcto. §16 dice que si una notificación se
pierde, la comparación de versiones tiene que permitir detectar y recuperar el
atraso. La alternativa —no registrar el evento hasta que la sincronización
termine— dejaría el mensaje reentregándose contra una cátedra caída, y el atraso
igual lo tendría que recuperar la comparación de versiones.

El mismo razonamiento cubre los **mensajes fuera de orden**: un evento de la
versión 5 que llega después de haber procesado la 8 no retrocede nada, porque el
destino no sale del mensaje; y si de todas formas se intentara, `advanceTo`
rechazaría el retroceso por incremental.

## Cada afirmación con su prueba

Esto es lo que mantiene el documento honesto: si un test se renombra o se borra,
la fila queda obviamente desactualizada. Formato `Clase#metodo`.

### La unidad de trabajo y la versión local

| Afirmación | Prueba |
| --- | --- |
| Si un paso del snapshot falla, la copia anterior y la versión quedan intactas | `FullSyncIntegrationTest#elFalloEnHorariosNoDejaRastro`, `#elFalloEnProfesionalesNoDejaRastro` |
| Con la base vacía, un fallo la deja vacía y sin versión | `FullSyncIntegrationTest#elFalloConBaseVaciaNoDejaVersion` |
| La versión no avanza si la transacción que aplica datos falla | `JpaCatalogVersionRepositoryAdapterTest#laVersionNoAvanzaSiLaTransaccionFalla` |
| La versión avanza junto con los datos cuando la transacción termina | `JpaCatalogVersionRepositoryAdapterTest#laVersionAvanzaSiLaTransaccionTermina` |
| Un avance rechazado deshace también los datos de su transacción | `JpaCatalogVersionRepositoryAdapterTest#unAvanceRechazadoDeshaceLosDatos` |
| Hay una sola fila de versión, y la base lo hace imposible de violar | `JpaCatalogVersionRepositoryAdapterTest#unaSegundaFilaEsImposible`, `#avancesConcurrentesNoDejanDosFilas`, `#primerosAvancesConcurrentesNoDejanDosFilas` |
| `INCREMENTAL` no retrocede; `SNAPSHOT` sí | `JpaCatalogVersionRepositoryAdapterTest#retrocederPorIncrementalSeRechaza`, `#retrocederPorSnapshotSePermite`, `FullSyncIntegrationTest#laVersionRetrocedePorSnapshot` |
| Asentar la misma versión está permitido | `JpaCatalogVersionRepositoryAdapterTest#laMismaVersionSePermite` |
| Una base recién inicializada no tiene versión y no es la versión 0 | `JpaCatalogVersionRepositoryAdapterTest#baseReciemInicializadaDevuelveVacio` |
| El registro de una corrida fallida sobrevive al rollback de los datos | `FullSyncIntegrationTest#elRegistroDelFalloSobreviveAlRollback`, `JpaSyncRunRepositoryAdapterTest#sobreviveAlRollback` |

### La sincronización completa

| Afirmación | Prueba |
| --- | --- |
| Aplica las tres colecciones y deja la versión en `snapshotVersion` | `FullSyncIntegrationTest#aplicaElSnapshotCompleto`, `FullSyncEndToEndTest#baseVaciaQuedaSincronizada` |
| Reemplaza la copia anterior en lugar de acumularla | `FullSyncIntegrationTest#reemplazaLaCopiaAnterior` |
| Correrla dos veces deja el mismo estado final | `FullSyncIntegrationTest#esIdempotente` |
| Un snapshot vacío deja la copia vacía y la versión asentada | `FullSyncIntegrationTest#aplicaUnSnapshotVacio` |
| Las entidades deshabilitadas se conservan como baja lógica | `FullSyncIntegrationTest#conservaLasDeshabilitadas` |
| Un snapshot que se contradice no avanza la versión y deja la copia entera | `FullSyncEndToEndTest#elFalloAMitadNoAvanzaLaVersion` |
| Después de un fallo, la búsqueda sigue respondiendo la copia anterior | `FullSyncEndToEndTest#despuesDelFalloLaBusquedaSigueRespondiendo` |
| Ninguna búsqueda vuelve a hablar con la cátedra | `FullSyncEndToEndTest#ningunaBusquedaHablaConLaCatedra` |

### La sincronización incremental y el orden

| Afirmación | Prueba |
| --- | --- |
| Recorre de `local + 1` al destino sin saltear ninguna versión | `ApplyIncrementalChangesUseCaseImplTest#recorreEnOrden` |
| Cada versión se aplica con su propio delta, no todas juntas | `ApplyIncrementalChangesUseCaseImplTest#unDeltaPorVersion` |
| Dos versiones consecutivas dejan el estado final y la versión correctos | `IncrementalSyncIntegrationTest#dosVersionesConsecutivas` |
| Si falta una versión del rango, se detiene en la anterior | `ApplyIncrementalChangesUseCaseImplTest#seDetieneEnLaVersionFaltante`, `IncrementalSyncIntegrationTest#seDetieneEnLaVersionFaltante` |
| Si una versión falla, las anteriores quedan aplicadas | `ApplyIncrementalChangesUseCaseImplTest#siUnaVersionFallaSeDetieneAhi` |
| Nunca retrocede ni reaplica lo ya cubierto | `ApplyIncrementalChangesUseCaseImplTest#noRetrocede`, `#yaEstaAlDia` |
| Sin versión local el camino incremental no aplica | `ApplyIncrementalChangesUseCaseImplTest#sinVersionLocal` |
| Una versión vacía hace avanzar la versión sin tocar los datos | `ApplyIncrementalChangesUseCaseImplTest#laVersionVaciaSeAplica`, `IncrementalSyncIntegrationTest#laVersionVaciaAvanza` |
| Un id ausente del hash se aplica como baja; la baja lógica no | `ApplyIncrementalChangesUseCaseImplTest#elIdAusenteEsUnaBaja`, `#laBajaLogicaNoEsUnaBaja`, `IncrementalSyncIntegrationTest#elIdAusenteSeBorra` |
| El orden por FK permite crear una categoría y su profesional en la misma versión | `IncrementalSyncIntegrationTest#laCategoriaYSuProfesionalEnLaMismaVersion` |
| Reprocesar una versión ya aplicada no cambia el resultado | `IncrementalSyncIntegrationTest#reprocesarNoCambiaNada` |

### La idempotencia y el offset

| Afirmación | Prueba |
| --- | --- |
| El mismo `eventId` dos veces no repite ningún efecto (§18.1) | `HandleCatalogUpdatedUseCaseImplTest#eventoDuplicado`, `#elDuplicadoNoSincroniza` |
| Insertar es la pregunta: el segundo insert es el duplicado | `JpaProcessedEventRepositoryAdapterTest#elSegundoEsDuplicado` |
| Dos eventos distintos de la misma versión se procesan los dos | `JpaProcessedEventRepositoryAdapterTest#dosEventosDeLaMismaVersion`, `HandleCatalogUpdatedUseCaseImplTest#dosEventosDeLaMismaVersion` |
| Si la transacción de quien llama falla, el evento no queda registrado | `JpaProcessedEventRepositoryAdapterTest#seVaConElRollback` |
| La retención borra lo viejo y respeta el borde de la ventana | `JpaProcessedEventRepositoryAdapterTest#purgaPorRetencion`, `#elBordeDeLaVentanaNoSeBorra`, `ProcessedEventRetentionJobTest#elLimiteEsAhoraMenosLaRetencion` |
| Se procesa y después se confirma el offset, en ese orden | `CatalogUpdatedListenerTest#procesaYConfirma` |
| Un duplicado también confirma el offset | `CatalogUpdatedListenerTest#elDuplicadoConfirma` |
| Una falla transitoria no confirma el offset y se propaga | `CatalogUpdatedListenerTest#laFallaTransitoriaNoConfirma`, `HandleCatalogUpdatedUseCaseImplTest#siElRegistroFallaNoSeTragaLaFalla` |
| Un mensaje que no cumple el contrato no traba la partición | `CatalogUpdatedListenerTest#elMensajeEnvenenadoSeSaltea`, `CatalogUpdatedConsumerIntegrationTest#elMensajeEnvenenadoNoTraba`, `#elEsquemaDesconocidoNoTraba` |
| El mismo mensaje publicado dos veces en el topic se procesa una sola | `CatalogUpdatedConsumerIntegrationTest#elDuplicadoEnElTopicSeAbsorbe` |
| El offset confirmado sobrevive a un consumidor nuevo del mismo grupo | `CatalogUpdatedConsumerIntegrationTest#elOffsetConfirmadoSobrevive` |
| Un grupo nuevo relee el topic entero y el estado final no cambia | `CatalogUpdatedConsumerIntegrationTest#relecturaCompletaSinEfectos` |
| Si la sincronización falla, el evento queda procesado igual | `HandleCatalogUpdatedUseCaseImplTest#laFallaDeSincronizacionNoSePropaga` |

### La discontinuidad y la recuperación

| Afirmación | Prueba |
| --- | --- |
| Local 3, current 7, oldest 4: snapshot y no se intenta `changes:4` (§18.2) | `SynchronizeCatalogUseCaseImplTest#elCasoCanonicoDeLaReferencia` |
| Local igual a `oldest` todavía sirve para avanzar con incrementos | `SynchronizeCatalogUseCaseImplTest#elBordeDeLaVentanaSirve` |
| Sin versión local, completa | `SynchronizeCatalogUseCaseImplTest#sinVersionLocal` |
| Si no se puede verificar el estado del origen, completa (§6.2) | `SynchronizeCatalogUseCaseImplTest#siNoSePuedeVerificarElEstado`, `#siLaMetadataEstaMalformada` |
| Si el recorrido se detiene por una versión faltante, completa después | `SynchronizeCatalogUseCaseImplTest#siFaltaUnaVersionDelRango` |
| Si ya está al día, no se hace nada ni se registra corrida | `SynchronizeCatalogUseCaseImplTest#yaEstaAlDia`, `#laVersionLocalMasNueva`, `ReconciliationIntegrationTest#sinAtrasoNoRegistraNada` |
| Si la cátedra no publicó metadata, no hay destino y no se hace nada | `SynchronizeCatalogUseCaseImplTest#sinMetadataPublicada` |
| Una notificación perdida deja la copia atrasada y el chequeo la recupera | `ReconciliationIntegrationTest#recuperaLaNotificacionPerdida` |
| Fuera de la ventana, el chequeo reconstruye con snapshot | `ReconciliationIntegrationTest#fueraDeLaVentanaReconstruye` |
| Si Redis no responde, el chequeo no revienta | `ReconciliationIntegrationTest#siRedisNoRespondeNoRevienta` |
| Solo una sincronización a la vez, y el lock se libera ante una falla | `SynchronizeCatalogUseCaseImplTest#unaSolaALaVez`, `#elLockSeLiberaAnteUnaFalla` |

### El estado informado

| Afirmación | Prueba |
| --- | --- |
| Los cuatro estados se distinguen y `BEHIND` no se confunde con `DEGRADED` | `SyncStatusReportTest` (los diez casos) |
| El reporte responde incluso sin catálogo o con la cátedra caída | `SyncStatusApiIntegrationTest#sinCatalogoAplicado`, `#conLaCatedraCaida` |
| Los duplicados quedan contados en `/actuator/metrics` | `SyncMetricsTest#cuentaLosDuplicados`, `CatalogUpdatedListenerTest#elDuplicadoConfirma` |

> **Lo que todavía no tiene prueba propia** y es trabajo del issue #30: la
> caída entre persistir y confirmar el offset ejercida de punta a punta con los
> tres contenedores a la vez, y el reinicio con una sincronización a medias. Las
> piezas están probadas por separado —el rollback, la deduplicación, la
> reentrega— pero no el escenario completo.

## Consecuencias

### A favor

- **La regla de §6.1 no depende de disciplina.** No hay forma de avanzar la
  versión sin haber aplicado los datos: la transacción lo hace imposible, y lo
  demuestran tres pruebas contra PostgreSQL real.
- **Un atraso se recupera de a pedazos.** Una falla en la séptima de diez
  versiones no tira las seis que ya estaban.
- **Las llamadas de red no sostienen locks.** Importa de verdad con 228 ms de
  RTT (D6).
- **Un duplicado es gratis.** No es un error ni dispara trabajo: se reconoce,
  se confirma y se cuenta.
- **Ninguna notificación perdida es definitiva.** La comparación de versiones no
  depende de que llegue ningún mensaje, así que el peor caso de un aviso perdido
  es un intervalo de atraso.
- **Cada fallo deja rastro consultable**, con un código estable, en `sync_run`,
  `sync_error` y el endpoint de estado.

### En contra

- **Una notificación puede no producir ningún efecto** (evento adelantado
  respecto de Redis) y el `eventId` igual queda consumido. Hay que confiar en el
  chequeo periódico, y eso convierte su intervalo en el peor caso de atraso del
  sistema.
- **Un atraso puede quedar a mitad de camino.** Si la quinta de diez versiones
  falla, la copia queda consistente pero atrasada hasta el próximo disparo.
- **El lock es en proceso**, así que la garantía de "una sola sincronización a la
  vez" se pierde con más de una instancia. Está acotado y documentado.
- **`sync_run` y `processed_event` necesitan políticas de retención**, que son
  dos trabajos periódicos más y dos ventanas más que configurar.
- **El snapshot no tiene reintentos propios** todavía (#31): hoy una cátedra
  intermitente se resuelve esperando el próximo chequeo, que puede ser hasta
  cinco minutos después.
- **Reemitir el token se reintenta una sola vez.** Un 401 persistente deja la
  sincronización trabada hasta que alguien revise la cuenta técnica; se ve en el
  estado como `DEGRADED` con `SNAPSHOT_UNAUTHORIZED`.

## Alternativas consideradas

### Una transacción para todo el atraso

Diez versiones en una sola unidad de trabajo. Se descarta: una falla en la
última tiraría todas las anteriores y la versión local volvería al punto de
partida, convirtiendo un atraso recuperable en un atraso que no avanza nunca si
alguna versión del rango falla de forma reproducible. Además mantendría los
locks de las tres tablas durante todas las lecturas de Redis intermedias.

### `existsById` seguido de `save` para deduplicar

Es la forma intuitiva y tiene una ventana de carrera entre las dos llamadas por
la que dos entregas del mismo mensaje pasan las dos. Se descarta porque ese es
*exactamente* el caso que la deduplicación tiene que cubrir. Insertar contra la
clave primaria no tiene ventana.

### Registrar el evento en su propia transacción (`REQUIRES_NEW`)

Dejaría eventos marcados como procesados cuyos efectos nunca ocurrieron, y el
reintento los descartaría como duplicados: la peor combinación posible, porque
el efecto no ocurre nunca y nada queda pendiente.

### Confirmar el offset antes de persistir, o dejar el auto-commit

Las dos pierden notificaciones en silencio: el mensaje queda confirmado sin que
sus efectos existan, y no hay rastro de que algo faltó. §16 fija el orden
contrario.

### Tomar `newVersion` del evento como destino

Es tentador —el mensaje ya trae el número— y hace al sistema dependiente de un
dato que §15.3 declara solo informativo. Si el evento viene adelantado respecto
de Redis, el recorrido pediría `changes:8` de una versión que todavía no está
publicada y se detendría con una falsa discontinuidad, que dispararía un
snapshot completo innecesario sobre el enlace de 228 ms.

### Saltear la versión faltante y seguir con la siguiente

Dejaría un hueco silencioso: las versiones describen diferencias contra la
anterior, así que lo que se saltea no lo corrige nada posterior. La copia local
quedaría afirmando una versión que no tiene. §18.2 manda reconstruir con
snapshot, que es lo que se hace.

### Saltear el mensaje al agotar los reintentos

Es el comportamiento por defecto de Spring Kafka. Se reemplaza por detener el
consumidor: como el offset nunca se confirmó, detenerse hace que el reintento de
arranque lo levante y Kafka reentregue. Saltearlo perdería la notificación. El
mensaje **envenenado** sí se saltea, y la diferencia es la clave: ese no se va a
arreglar nunca, y reintentarlo trabaría la partición entera.

### Un lock en la base en lugar de en proceso

Un `SELECT ... FOR UPDATE` sobre `catalog_version` funcionaría con varias
instancias. Se descarta por ahora porque hay una sola instancia y el lock en
proceso no tiene costo ni configuración; queda anotado como el único lugar a
tocar si eso cambia.

### Recordar la versión remota en lugar de leerla al informar el estado

Evitaría una lectura de Redis por consulta del endpoint de estado. Se descarta
porque convertiría el estado en un recuerdo: con la cátedra publicando versiones
nuevas, un valor guardado diría "al día" sobre una comparación vieja, y sin leer
el origen no habría forma de distinguir `BEHIND` de `DEGRADED`.

## Referencias

- Enunciado: §4.1 (responsabilidades), §6.1 y §6.2 (sincronización y
  discontinuidad), §8 (recuperación), §10.1 y §11 (pruebas y evidencias),
  §12 (documentación exigida).
- Referencia de integración: §7 (snapshot), §14 (Redis), §15.1–§15.3 (Kafka y
  `CatalogUpdated`), §16 (diagrama de sincronización), §18.1–§18.4
  (recuperación y reintentos).
- Narrativa: [`docs/sincronizacion.md`](../sincronizacion.md).
- ADRs relacionados: [0001](0001-arquitectura-hexagonal.md) (dónde viven las
  transacciones y los puertos), [0002](0002-identidad-y-autenticacion-entre-servicios.md)
  (por qué el JWT técnico de la cátedra no atraviesa la capa de aplicación).
- Issues: #12–#14 (completa), #22–#26 (incremental y discontinuidad),
  #27 (reconciliación), #28 (estado y errores), #30 (pruebas), #31 (timeouts y
  reintentos).

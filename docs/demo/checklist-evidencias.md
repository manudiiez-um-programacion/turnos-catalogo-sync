# Checklist cruzado: §11 y §12 contra evidencia

Cierra el issue #36. Cada viñeta de §11 (evidencias mínimas) y §12
(documentación obligatoria) del enunciado, cruzada contra el archivo, la
prueba o el paso de [`guion-demo.md`](guion-demo.md) que la cubre **en este
repositorio**. Las viñetas que son de otro repo están marcadas, para que no
parezca que falta algo acá.

## §11 — Evidencias mínimas esperadas

| # | Viñeta de §11 | ¿Este repo? | Dónde está la evidencia |
| --- | --- | --- | --- |
| 1 | Una inicialización desde snapshot | Sí | `FullSyncEndToEndTest#baseVaciaQuedaSincronizada` · guion [paso 1](guion-demo.md#paso-1--inicialización-desde-snapshot-111-040) |
| 2 | Una actualización incremental del catálogo | Sí | `SyncRecoveryEndToEndTest#dosVersionesPendientesDejanLaLocalEnLaUltima` · guion [paso 2](guion-demo.md#paso-2--incremental-discontinuidad-e-idempotencia-112-113-119-045) |
| 3 | Recuperación mediante snapshot luego de una discontinuidad | Sí | `SyncRecoveryEndToEndTest#fueraDeLaVentanaPideElSnapshot` · [docs/sincronizacion.md §8](../sincronizacion.md#8-la-discontinuidad-con-números) · guion paso 2 |
| 4 | Búsquedas resueltas sobre datos locales | Sí | `ProfessionalSearchApiIntegrationTest` (incluye el doble que falla si una búsqueda llama a la cátedra) · Bruno `04` · guion [paso 4](guion-demo.md#paso-4--búsquedas-y-filtros-sobre-datos-locales-114-115-130) |
| 5 | Filtros obligatorios: categoría, nombre, habilitado, disponibilidad | Sí | `ProfessionalSearchApiIntegrationTest` · Bruno `05`, `06`, `07` · guion paso 4 |
| 6 | Reserva confirmada por REST y Kafka | **No** — servicio de turnos | — |
| 7 | Reserva que expira sin completar la información | **No** — servicio de turnos | — |
| 8 | Consulta y cancelación de una reserva propia, sin acceso cruzado | **No** — servicio de turnos | Lo análogo *de este repo* (autorización por identidad en sus propios endpoints) está en la fila 11 |
| 9 | Procesamiento idempotente de un mensaje duplicado | Sí | `SyncRecoveryEndToEndTest#elDuplicadoEnElTopicNoDuplicaEfectos`, `SyncMetricsTest#cuentaLosDuplicados` (contador agregado) · [docs/recuperacion.md §1](../recuperacion.md#1-mensaje-duplicado-181) · guion paso 2, Bruno `10` (opcional, requiere ZeroTier) |
| 10 | Separación efectiva de datos entre los dos servicios | Sí | [docs/modelo-datos.md](../modelo-datos.md#delimitación-de-propiedad) · `ApiDeSoloLecturaTest`, `SecretosNoSeFiltranTest` · demo de rol Postgres sin `CONNECT` · guion [paso 5](guion-demo.md#paso-5--separación-de-datos-entre-servicios-1110-130) |
| 11 | Rechazo de accesos no autenticados o no autorizados | Sí | `ApiSecurityIntegrationTest` · Bruno `08`, `09` · guion [paso 3](guion-demo.md#paso-3--rechazo-de-accesos-no-autenticados-o-no-autorizados-1111-130) |
| 12 | Pruebas automatizadas de los comportamientos principales | Sí | `./mvnw verify` completo, badge de CI del README · guion [paso 6](guion-demo.md#paso-6--pruebas-automatizadas-panorama-1112-030) |

Fila 8: el único cruce de identidad que existe *en este repo* es el de sus
propios recursos (la agenda interna y el estado de sincronización, que solo
el JWT técnico de turnos puede leer) y la separación entre el JWT de usuario
y el técnico interno. No hay reservas ni "propiedad de un recurso por
usuario" acá: ese modelo entero —holds, reservas, cancelación propia— vive
en `turnos-reservas`.

## §12 — Documentación obligatoria

Ya cerrado por el issue #35 y anteriores; se repite acá solo para que el
cruce quede completo en un solo lugar, como pide el criterio de aceptación
de #36. El detalle viñeta por viñeta ya está en la
[tabla del README](../../README.md#documentación-obligatoria-§12).

| # | Viñeta de §12 | Dónde está |
| --- | --- | --- |
| 1 | README con requisitos, configuración y pasos reproducibles | [`README.md`](../../README.md) |
| 2 | Diagrama de arquitectura (app, los dos servicios, bases, integraciones) | [`docs/arquitectura.md`](../arquitectura.md) |
| 3 | Contratos entre los dos servicios (operaciones, DTO, autenticación, errores) | [`docs/contratos/catalogo-turnos.md`](../contratos/catalogo-turnos.md) |
| 4 | Modelo de datos de este servicio y delimitación de su propiedad | [`docs/modelo-datos.md`](../modelo-datos.md) |
| 5 | Estrategia de sincronización completa e incremental | [`docs/sincronizacion.md`](../sincronizacion.md) |
| 6 | Procesamiento idempotente y recuperación ante duplicados, pérdidas y desorden | [`docs/recuperacion.md`](../recuperacion.md) |
| 7 | Decisiones de seguridad, autenticación y autorización | [`docs/seguridad.md`](../seguridad.md) |
| 8 | Instrucciones para ejecutar las pruebas automatizadas | [README, Verificación automática](../../README.md#verificación-automática) |

## Cómo se armó esta tabla

Cruzando el texto de §11 y §12 del enunciado
([`docs/catedra/PROJECT_STATEMENT-v1.md`](../catedra/PROJECT_STATEMENT-v1.md))
contra lo que ya existe: el README (que ya tenía la tabla de §12), los
índices de prueba de `docs/sincronizacion.md` y `docs/recuperacion.md` (que
ya asocian cada afirmación con su prueba, ADR 0004), y la colección de
Bruno nueva de este issue. Nada de lo que aparece acá se escribió de cero
salvo la colección, el script de JWT de demo y el guion: el resto es
indexar lo que #11 a #35 ya habían dejado probado y documentado.

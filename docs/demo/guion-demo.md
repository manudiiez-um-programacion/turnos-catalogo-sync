# Guion de demo para la defensa

Cierra el issue #36. Paso a paso, cronometrado, para producir en vivo las
evidencias de §11 que corresponden a **este repositorio** (catálogo y
sincronización). Las que no corresponden están marcadas explícitamente en
cada paso y en [`checklist-evidencias.md`](checklist-evidencias.md).

**Por qué varios pasos corren una prueba automatizada en vez de pegarle a la
cátedra real.** El enlace a la cátedra es por ZeroTier, con ~228ms de RTT
(README, [Latencia](../../README.md#latencia)), y una sincronización
completa real tarda varios segundos más el tiempo de unirse a la red. Mostrar
en vivo `SyncRecoveryEndToEndTest` o `FullSyncEndToEndTest` —que ya ejercitan
PostgreSQL, Redis y Kafka reales por Testcontainers, con la cátedra REST
simulada por WireMock— es más rápido, 100% reproducible sin depender de que
el servidor de la cátedra esté arriba justo en ese momento, y es exactamente
la prueba que el README ya señala como "la que §11 pide poder mostrar". Los
pasos que sí pegan en vivo (búsquedas, rechazos) corren contra una instancia
local con datos ya sincronizados de antes, así que tampoco dependen de la
red de la cátedra durante la defensa.

## Antes de la defensa (no cronometrado)

1. `docker compose up --build` con el `.env` completo, **al menos una vez
   con ZeroTier activo**, para que la base tenga datos reales (snapshot
   aplicado). Después de esa primera vez, `docker compose down` (sin `-v`)
   conserva el volumen: se puede repetir `docker compose up` offline el día
   de la defensa.
2. Generar los dos JWT de demo y pegarlos en el entorno `local` de la
   colección Bruno ([`coleccion-bruno/`](coleccion-bruno/)):
   ```bash
   ./docs/demo/generar-jwt.sh usuario   # -> variable jwtUsuario
   ./docs/demo/generar-jwt.sh interno   # -> variable jwtInterno
   ```
3. Correr la request `04 - Busqueda sin filtros` una vez y copiar un
   `id` de profesional y de categoría reales a `professionalId` y
   `categoryId` del entorno.
4. Confirmar `GET /actuator/health` → `{"status":"UP"}`.
5. Tener dos terminales: una para `./mvnw test -Dtest=...`, otra para
   Bruno/`docker compose`/`psql`.

## Durante la defensa

| Paso | Qué muestra | §11 / §12 | Duración objetivo |
| --- | --- | --- | --- |
| 0 | Intro | — | 0:30 |
| 1 | Inicialización desde snapshot | §11.1 | 0:40 |
| 2 | Incremental, discontinuidad e idempotencia | §11.2, §11.3, §11.9 | 0:45 |
| 3 | Rechazo de accesos no autenticados/no autorizados | §11.11 | 1:30 |
| 4 | Búsquedas y filtros sobre datos locales | §11.4, §11.5 | 1:30 |
| 5 | Separación de datos entre servicios | §11.10 | 1:30 |
| 6 | Pruebas automatizadas, panorama | §11.12 | 0:30 |
| — | Qué es de turnos/KMP y no de este repo | §11.6-8 | 0:30 |

**Total: ≈ 7 minutos de guion propio.** Medido en esta máquina, cada
`./mvnw test -Dtest=...` de abajo corre en 10-25 segundos reales (sin
contar el tiempo de narrarlo); el margen de la tabla es para hablar encima
del comando, no para esperarlo.

### Paso 0 — Intro (0:30)

"Este servicio guarda la copia local del catálogo de la cátedra —
categorías, profesionales, horarios— y se la expone a turnos y a la app por
contrato. Las evidencias de disponibilidad, reservas y la app en sí viven en
los otros dos repos; acá muestro snapshot, incremental, recuperación,
búsquedas, idempotencia, separación de datos y autorización."

### Paso 1 — Inicialización desde snapshot (§11.1, 0:40)

```bash
./mvnw test -Dtest='FullSyncEndToEndTest'
```

(El método que es la evidencia puntual es `baseVaciaQuedaSincronizada`; los
selectores `Clase#método` de Surefire no bajan a los métodos de clases
`@Nested`, así que acá y en el paso siguiente se corre la clase entera —
sigue siendo rápido, medido en esta máquina.)

Narrar mientras corre: base vacía → pide el snapshot completo a la cátedra
(simulada) → aplica las tres colecciones → la versión local queda en la del
snapshot. `BUILD SUCCESS` es la evidencia.

*Si hay ZeroTier activo y tiempo de sobra*, reforzarlo con el dato real:
`docker compose down -v && docker compose up --build`, esperar el log
`Snapshot ... aplicado`, y `curl -s http://localhost:8080/actuator/health | jq '.components.catalogSync'`.
No es necesario para que el paso cuente: la prueba automatizada ya es la
evidencia aceptada.

### Paso 2 — Incremental, discontinuidad e idempotencia (§11.2, §11.3, §11.9, 0:45)

```bash
./mvnw test -Dtest='SyncRecoveryEndToEndTest'
```

La clase entera corre en segundos (medido: ~24s en esta máquina) y deja
varias afirmaciones de una sola corrida, apoyada en los tres transportes
reales (PostgreSQL, Redis, Kafka por Testcontainers). Las tres que importan
para este paso, para señalar en el log mientras corre:

- `dosVersionesPendientesDejanLaLocalEnLaUltima` — dos versiones pendientes
  se aplican **en orden** (§11.2).
- `fueraDeLaVentanaPideElSnapshot` — la versión local cae antes de
  `oldest-available-version`: reconstruye con el snapshot completo, no con
  el incremental (§11.3, §18.2).
- `elDuplicadoEnElTopicNoDuplicaEfectos` — el mismo `eventId` dos veces en
  el topic deja un solo efecto, y el offset se confirma igual (§11.9,
  §18.1).

El detalle de cada caso, con diagrama de secuencia, está en
[`docs/sincronizacion.md`](../sincronizacion.md#7-idempotencia-duplicados-perdidos-y-fuera-de-orden)
y en [`docs/recuperacion.md`](../recuperacion.md).

### Paso 3 — Rechazo de accesos no autenticados o no autorizados (§11.11, 1:30)

Automatizado, cuatro casos a la vez:

```bash
./mvnw test -Dtest='ApiSecurityIntegrationTest'
```

En vivo con Bruno, dos de esos casos con una request real:

1. **`08 - Rechazo sin token`** → `GET /api/professionals` sin
   `Authorization` → `401` con `code: AUTH_TOKEN_MISSING`.
2. **`09 - Rechazo con la identidad equivocada`** → `GET
   /api/internal/sync/status` con el JWT de **usuario final** en la API
   **interna** → `401` (la cadena interna ni llega a mirar la autoridad: no
   conoce ese secreto). Mostrar que con `jwtInterno` la misma request del
   paso 2 de Bruno sí responde `200`: la diferencia es un solo campo del
   entorno, la separación es criptográfica, no un `if`.

### Paso 4 — Búsquedas y filtros sobre datos locales (§11.4, §11.5, 1:30)

En vivo con Bruno, contra la instancia con datos ya sincronizados:

1. **`04 - Busqueda sin filtros`** — el catálogo paginado.
2. **`05 - Filtro por categoria y nombre parcial`** — `categoryId` exacto +
   `name` parcial insensible a acentos.
3. **`06 - Filtro por habilitado=false`** — solo los deshabilitados (baja
   lógica, §14.4).
4. **`07 - Filtro por disponibilidad`** — día + franja horaria.

Mencionar, sin tener que demostrarlo aparte: **nunca se consulta a la
cátedra para resolver una búsqueda** (§6.2) — lo prueba
`ProfessionalSearchApiIntegrationTest`, que reemplaza el puerto del snapshot
por un doble y falla si una búsqueda lo llega a invocar.

### Paso 5 — Separación de datos entre servicios (§11.10, 1:30)

Dos niveles de evidencia, de código hacia abajo:

```bash
./mvnw test -Dtest='ApiDeSoloLecturaTest,SecretosNoSeFiltranTest'
```

`ApiDeSoloLecturaTest` prueba que ninguna ruta acepta un método que escriba
(la única escritura es la sincronización, nunca HTTP);
`SecretosNoSeFiltranTest`, que ningún secreto aparece en una respuesta ni en
un log.

Y en vivo, a nivel base de datos — el rol `catalogo` no tiene permiso de
conexión cruzada (README,
[La base de datos](../../README.md#la-base-de-datos-una-base-y-un-usuario-propios)):

```bash
# simula el rol del servicio de turnos: nace sin ningun permiso otorgado
docker compose exec postgres psql -U postgres -c "CREATE ROLE demo_turnos LOGIN;"
docker compose exec postgres psql -U demo_turnos -d catalogo -c '\dt'
# FATAL: permission denied for database "catalogo"

# limpieza
docker compose exec postgres psql -U postgres -c "DROP ROLE demo_turnos;"
```

Cerrar con una frase: "no es una convención, es que no existe ninguna
credencial con la que el otro servicio pueda conectarse a esta base; la
única vía es el contrato REST con JWT técnico interno
([`docs/contratos/catalogo-turnos.md`](../contratos/catalogo-turnos.md))".

### Paso 6 — Pruebas automatizadas, panorama (§11.12, 0:30)

No correr `./mvnw verify` completo en vivo (varios minutos con todos los
Testcontainers: PostgreSQL, Redis y Kafka reales por cada clase de
integración). Señalar el badge de CI del README y decir: "cubre
sincronización, idempotencia, autorización y aislamiento, filtros, y las dos
APIs internas; corre completo en GitHub Actions en cada push y en cada PR,
con `./mvnw verify` — es lo mismo que acabo de correr suelto en los pasos
1, 2, 3 y 5, más el resto de las clases de dominio y casos de uso".

### Cierre — qué es de turnos/KMP y no de este repo (0:30)

Leer textual, para que quede explícito ante los dos profesores:

> Las evidencias de §11 que **no** corresponden a este repositorio son la
> reserva confirmada por REST y Kafka, la reserva que expira, y la consulta
> y cancelación de una reserva propia — las tres viven en el servicio de
> turnos — y el funcionamiento de la app, que es de la app KMP.

## Qué hacer si algo no funciona en el momento

- **Docker no responde** → usar directamente el log de la última corrida de
  CI (badge del README) como evidencia de respaldo para los pasos 1, 2, 3 y
  5.
- **No hay red ZeroTier** → no afecta ningún paso de este guion: todos
  corren contra Testcontainers (pasos 1, 2, 3, 5) o contra datos ya
  sincronizados de antes (paso 4). Es justo el comportamiento que pide §11:
  las búsquedas nunca dependen de la cátedra.
- **El JWT pegado en Bruno venció** → volver a correr
  `./docs/demo/generar-jwt.sh usuario|interno` (dura 4 horas por defecto,
  alcanza de sobra para una sesión).

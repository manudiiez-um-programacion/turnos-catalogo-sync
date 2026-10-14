# ADR 0001 — Arquitectura hexagonal con slices verticales

- **Estado:** aceptada
- **Fecha:** 2026-10-14
- **Issue:** #2
- **Decisiones del proyecto que cierra:** D2 (JHipster), D3 (motor de base de datos y migraciones)

## Contexto

Este servicio mantiene una copia local del catálogo (categorías profesionales,
profesionales y horarios semanales) cuya fuente autoritativa es el servicio
central de la cátedra. Esa copia se alimenta por tres caminos distintos
—snapshot REST, cambios incrementales en Redis y notificaciones Kafka— y se
expone por dos caminos propios: la búsqueda para la app KMP y el contrato de
agenda vigente para el servicio de turnos.

Esto deja al servicio con **cinco integraciones externas** y una regla de
negocio que no depende de ninguna de ellas: la versión local solo avanza cuando
la unidad de trabajo terminó completa. Esa regla es el corazón del servicio y es
lo que hay que poder probar sin levantar Redis, Kafka ni la API de la cátedra.

El enunciado deja la arquitectura interna a cargo del alumno (§10) y pide
documentar y justificar las decisiones técnicas (§12). Además, los datos de
acceso al entorno de la cátedra todavía no llegaron (decisión abierta D6), así
que el desarrollo arranca contra dobles de prueba: si la lógica de
sincronización estuviera pegada a los clientes de Redis o Kafka, no habría forma
de avanzar hasta que lleguen esos datos.

## Decisión

Se adopta **arquitectura hexagonal (puertos y adaptadores) organizada por slices
verticales**, siguiendo la skill `hexagonal-slicing` del repo con los desvíos
que se registran más abajo.

### Paquete raíz

```
com.um.catalogsync
```

Queda fijo. El issue #3 crea la estructura a partir de este nombre y no se
renombra después: mover paquetes más tarde ensucia el historial que el profesor
tiene que poder leer (§13.1).

### Build

**Maven**, con el wrapper `mvnw` versionado en el repo. El enunciado no impone
build tool; se elige Maven porque es el que usan el repo de ejemplo de la
cátedra, la plantilla de la skill y la documentación de Spring Boot. El wrapper
permite que el profesor clone y corra `./mvnw verify` sin instalar nada.

### Slices

Cada slice es un paquete autocontenido con las tres capas adentro
(`domain/`, `application/`, `infrastructure/`).

| Slice | De qué es dueño |
| --- | --- |
| `category` | Categorías profesionales de la copia local: modelo, persistencia y consulta. |
| `professional` | Profesionales de la copia local, y la búsqueda con filtros por categoría, nombre, habilitado y disponibilidad (§4.1). |
| `schedule` | Horarios semanales, y el armado de la agenda vigente que consume el servicio de turnos. |
| `sync` | El proceso de sincronización: versión local aplicada, snapshot completo, incremental, deduplicación por `eventId`, detección de discontinuidad y estado/errores de sincronización. |
| `integration` | La cuenta técnica ante la cátedra: configuración de acceso, obtención y renovación del JWT técnico. Es el único slice que conoce esas credenciales. |
| `shared` | Transversales sin dueño de negocio: seguridad, manejo de errores en `application/problem+json`, configuración de OpenAPI, reloj inyectable y métricas. |

`sync` es el único slice que escribe en los otros tres. `category`,
`professional` y `schedule` son de **solo lectura hacia afuera**: no exponen
creación, modificación ni borrado por HTTP, porque la única escritura legítima
viene de la sincronización.

### Estructura interna de un slice

```
com.um.catalogsync.<slice>/
├── domain/
│   ├── model/            modelo de negocio en Java puro
│   └── ports/
│       ├── in/           un caso de uso por interfaz
│       └── out/          lo que el dominio necesita del exterior
├── application/
│   ├── usecases/         una impl por puerto de entrada, @Component
│   ├── service/          fachada que compone casos de uso, @Service
│   └── exception/        excepciones de dominio/aplicación
└── infrastructure/
    ├── persistence/      entity JPA, repositorio Spring Data, mapper, adapter
    └── web/              @RestController, DTOs request/response, mapper
```

Los slices que hablan con la cátedra agregan adaptadores propios bajo
`infrastructure/` (`client/`, `messaging/`, `cache/`), espejando el patrón de
`persistence/`.

### Regla de dependencias

**Las dependencias apuntan hacia adentro.**

```
infrastructure ──→ application ──→ domain ──→ (nada)
```

- `domain` no depende de nada: ni de Spring, ni de JPA, ni de Jackson.
- `application` depende solo de `domain`.
- `infrastructure` depende de `application` y `domain`, nunca al revés.

**Prohibición explícita:** una `@Entity` JPA, un DTO web, un DTO de un cliente
REST externo o cualquier tipo de `infrastructure` **no puede aparecer en la
firma de una clase de `domain` o `application`** —ni como parámetro, ni como
retorno, ni como campo. Si aparece un `import` de `infrastructure.*` dentro de
`domain` o `application`, la regla está rota y se corrige mapeando en el borde,
no relajando la regla. Lo que cruza las capas es siempre el modelo de dominio.

El mismo criterio aplica entre slices: un slice no importa el `infrastructure`
de otro. Si `sync` necesita escribir categorías, lo hace contra un puerto de
salida que `category` expone, no contra su repositorio JPA.

### Adaptadores externos

Cinco adaptadores, cada uno implementando un puerto de salida que vive en el
`domain` de su slice. Los nombres definitivos pueden ajustarse al implementar
cada issue; lo que no cambia es que **cada integración externa entra por un
puerto**.

| Adaptador | Slice | Puerto que implementa | Responsabilidad |
| --- | --- | --- | --- |
| Persistencia JPA | `category`, `professional`, `schedule`, `sync` | `CategoryRepositoryPort`, `ProfessionalRepositoryPort`, `SchedulePort`, `SyncStatePort` | La copia local en PostgreSQL. Expone `upsert` y `replaceAll`, no CRUD. |
| Cliente REST de cátedra | `sync` | `CatalogSnapshotPort` | `GET /api/synchronization/snapshot` (§7) con el JWT técnico. Trae la sincronización completa. |
| Redis | `sync` | `CatalogVersionPort`, `CatalogChangesPort` | Lee `catedra:sync:current-version`, `oldest-available-version`, `metadata`, los hashes de entidades y `catedra:sync:changes:{version}` (§14). Solo lectura sobre `catedra:sync:*`. |
| Kafka | `sync` | `CatalogUpdatedListener` (entrada) | Consume `CatalogUpdated` del topic `catedra.catalog.{groupId}` (§15.3). Solo notifica: los datos se leen de Redis o del snapshot. Commit de offset después de persistir. |
| API REST propia | `professional`, `schedule`, `sync` | puertos de entrada de cada slice | Búsqueda y filtros para KMP, agenda vigente para el servicio de turnos, y estado de sincronización. Protegida con JWT. |

El adaptador de Kafka es de **entrada**: no implementa un puerto de salida, sino
que invoca un caso de uso de `sync`. Se lo lista acá porque es una de las cinco
superficies externas del servicio.

El JWT técnico lo provee el slice `integration` a través de un puerto que el
cliente REST consume; ningún otro slice lo conoce, y nunca se expone a la app
KMP (§3.2).

## Consecuencias

### A favor

- La regla de la versión local (avanza solo al terminar la unidad de trabajo) se
  prueba con dobles de los puertos, sin Redis ni Kafka ni la API de la cátedra.
  Esto desbloquea el desarrollo mientras D6 siga abierta.
- Las cinco integraciones son reemplazables: cambiar de cliente Redis o de
  librería Kafka toca un adaptador, no la lógica.
- Cada funcionalidad vive en un solo paquete. Entender "profesionales" es abrir
  `professional/`, no recorrer cuatro carpetas técnicas.
- La prohibición de `@Entity` en `domain` evita el acoplamiento que aparece
  cuando el modelo de negocio arrastra decisiones de la base de datos.

### En contra

- **Más archivos.** Un slice CRUD completo son ~20 clases donde un controlador
  con un repositorio serían 3. Se acepta: el costo es la primera vez, y la skill
  trae un scaffolder (`scripts/scaffold_feature.py`) que genera el boilerplate.
- **Mappers en cada borde.** Hay que mantener `entity ⇄ domain` y `dto ⇄ domain`
  en paralelo. Es el precio de que el dominio no sepa de JPA ni de HTTP.
- **Indirección.** Seguir una request cruza controller → service → use case →
  puerto → adapter. Cuesta más de leer la primera vez que una llamada directa.
- Hay que sostener la disciplina: nada en el compilador impide importar
  `infrastructure` desde `domain`. Se controla en revisión de código y,
  eventualmente, con un test de arquitectura.

## Alternativas consideradas

### Capas horizontales (`controller/`, `service/`, `repository/`)

Lo estándar en Spring y lo que menos archivos genera. Se descarta porque con
cinco integraciones externas la capa `service` termina dependiendo directamente
de `RestTemplate`, `RedisTemplate` y el consumer de Kafka, y la lógica de
sincronización deja de ser testeable sin esa infraestructura. Además dispersa
cada funcionalidad en cuatro paquetes.

### JHipster (decisión D2)

**Decisión: no se usa.** El §3.1 lo deja explícitamente opcional —"utilizarlo o
no utilizarlo no modifica los requisitos funcionales, de integración, seguridad,
separación de datos, pruebas y documentación"—. Confirmado además con la
cátedra: es opcional y hay alumnos que parten de cero.

Razones:

1. **Estructura incompatible.** JHipster genera capas horizontales
   (`domain/`, `repository/`, `service/`, `web/rest/`), que es justamente la
   alternativa descartada arriba.
2. **Entidades mezcladas.** Lo que JHipster llama `domain/` no es un modelo de
   dominio: son clases con anotaciones de JPA, de validación y a veces de
   serialización en la misma declaración. Eso viola la prohibición explícita de
   este ADR.
3. **CRUD que sobra.** Genera `POST`, `PUT` y `DELETE` por entidad. Acá
   categorías, profesionales y horarios son de solo lectura hacia afuera: habría
   que borrar la mayor parte de lo generado.

Lo que el enunciado sí exige se cumple igual, sin el generador:

- **Modelo de usuario compatible con el de JHipster (§3.2):** `login`,
  `password`, `firstName`, `lastName`, `email`, `imageUrl` y `langKey`, con ids
  internos, estado de activación, autoridades y auditoría administrados por el
  backend. El propio enunciado prevé este caso: "quien no lo utilice implemente
  los mismos datos y comportamientos observables". Esto se implementa en el
  **servicio de turnos**, dueño de la identidad del usuario final (decisión
  abierta D1); este servicio solo valida el JWT emitido allá.
- **Errores en `application/problem+json` (§13 de la referencia de
  integración):** `type`, `title`, `status`, `detail`, `message`, `path` y un
  `code` estable, más `fieldErrors` en errores de validación. Se produce con un
  `@RestControllerAdvice` en el slice `shared`. Los clientes deciden por `status`
  y `code`, nunca por el texto de `detail`.

### Un solo módulo Maven por capa

Separar `domain`, `application` e `infrastructure` en módulos Maven distintos
haría que el compilador imponga la regla de dependencias en vez de confiar en la
disciplina. Se descarta por ahora: para el tamaño de este servicio agrega
complejidad de build desproporcionada. Si la regla se rompe repetidamente, la
alternativa más barata es un test de arquitectura (ArchUnit) antes que modularizar.

## Desvíos respecto de la skill `hexagonal-slicing`

La skill es la referencia de layout y convenciones, pero su stack asume un
proyecto de ejemplo. Los desvíos son obligatorios por el enunciado:

| Desvío | Motivo |
| --- | --- |
| **PostgreSQL en lugar de H2** | §3 prohíbe H2, SQLite y cualquier base embebida o en memoria como almacenamiento principal. Exige un gestor de base de datos servidor. H2 no figura como dependencia en ningún perfil, tampoco en `test`. |
| **Flyway agregado** | La skill no contempla migraciones; §3 exige que cada servicio administre las propias. |
| **Testcontainers en pruebas** | Consecuencia de lo anterior: las pruebas corren contra un PostgreSQL real y descartable, y contra Redis y Kafka reales donde haga falta. Es además lo que necesitan las pruebas de integración obligatorias mientras D6 siga abierta. |
| **Spring Security agregado** | La API de búsqueda y la de agenda van protegidas con JWT (§8, §11). La skill no incluye seguridad. |
| **Spring Kafka y Lettuce agregados** | Integración con `CatalogUpdated` y con las claves `catedra:sync:*`. La skill solo contempla web + JPA. |
| **Slices de solo lectura hacia afuera** | La skill asume slices CRUD completos. Acá los puertos de salida de `category`, `professional` y `schedule` exponen `upsert` y `replaceAll` —las operaciones que necesita la sincronización— y no se expone `create`/`update`/`delete` por HTTP. |
| **Slices sin entidad propia** | `sync` modela un proceso, no una entidad de catálogo, e `integration` modela una credencial. Ninguno de los dos se genera con el scaffolder; se escriben a mano. |

Se mantiene sin cambios todo lo demás: Spring Boot 4.1.0 como parent, Java 25,
Lombok, springdoc-openapi, un caso de uso por interfaz, fachada `@Service` por
slice, adaptador de persistencia que nunca devuelve una entidad JPA, e inyección
por constructor con `@RequiredArgsConstructor` (ninguna `@Autowired` en campos).

## Base de datos y migraciones (decisión D3)

**PostgreSQL + Flyway**, con base lógica y usuario propios de este servicio y sin
permisos cruzados con el servicio de turnos (§3). Todo levanta con Docker
Compose. En pruebas, PostgreSQL por Testcontainers.

Confirmado con la cátedra: no hay motor impuesto y Flyway es de uso habitual
entre los alumnos. Se elige Flyway sobre Liquibase por ser SQL plano versionado,
más simple de leer y de defender en la presentación final.

## Referencias

- Enunciado `docs/catedra/PROJECT_STATEMENT-v1.md`: §3, §3.1, §3.2, §4.1, §10, §12, §13.1
- Referencia de integración `docs/catedra/INTEGRATION_REFERENCE-v2.md`: §7, §13, §14, §15
- Skill `.claude/skills/hexagonal-slicing/SKILL.md`
- Issue #2; consume el #3 (estructura inicial)

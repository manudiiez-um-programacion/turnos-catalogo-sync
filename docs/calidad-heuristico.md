# Lectura del informe de calidad heurístico

`docs/EVALUACION.md` lo genera automáticamente la cátedra en cada evaluación
y no se edita a mano. Es un análisis **heurístico** (regex + métricas), no
una nota, y el propio documento lo dice. Esta página no lo contradice: señala
con evidencia de código cuál de sus hallazgos bajos es un defecto real y
cuál es una limitación conocida de cómo mide. Se actualiza cuando aparece una
señal nueva del mismo tipo, en vez de abrir un issue por cada una.

## D1 — Diseño OO, SOLID y patrones (1/100)

**Acoplamiento (CBO) contado sobre inyección de dependencias.** El
heurístico cuenta cada tipo referenciado por una clase —incluidas las
dependencias que Spring inyecta por constructor— como acoplamiento real. En
una arquitectura hexagonal con muchos adapters finos eso encadena: cada
adapter de infraestructura (persistencia, Redis, REST, mensajería) termina
con un CBO alto aunque tenga una sola responsabilidad y un único motivo de
cambio. Ejemplos de `docs/EVALUACION.md` (sección "Señales detectadas"):
`JpaSyncRunRepositoryAdapter` (CBO=49), `RestCatalogSnapshotAdapter`
(CBO=40), `SecurityConfig` (CBO=86, por configurar explícitamente cada
filtro y cadena de seguridad). Ninguna de las tres concentra lógica de
negocio: son puntos de ensamblado del framework, que es justamente lo que
pide aislar el ADR
[0001](adr/0001-arquitectura-hexagonal.md).

**`record` no se reconoce como clase.** El análisis parece basarse en
regex buscando la palabra `class`, y no cuenta un `record` como contenedor
de clase. El repo tiene 37 `record` públicos en `src/main` contra 127
`class` (al 2026-11-16): esa porción del código "no cuenta" para las
métricas de POO y resta puntos tanto al %POO como a D1.

## "Sueltas" = 1 — falso positivo

La señal apunta a `toCriteria`, en
`src/main/java/com/um/catalogsync/professional/infrastructure/web/dto/ProfessionalSearchRequest.java`.
No es una función fuera de una clase: es un método de instancia del
`record ProfessionalSearchRequest`. Mismo origen que el punto anterior
(`record` no reconocido).

## D5 — Seguridad y robustez (0/100)

Las dos señales de "posible secreto hardcodeado" son nombres de campo, no
valores:

- `redisPassword` en
  `src/main/java/com/um/catalogsync/integration/infrastructure/client/dto/IntegrationDto.java`
- `idToken` (mapeado desde `id_token`) en
  `src/main/java/com/um/catalogsync/integration/infrastructure/client/dto/TechnicalLoginResponseDto.java`

Son los DTOs que deserializan la respuesta del login técnico de la cátedra
(§5.1, §5.3 de `docs/catedra/INTEGRATION_REFERENCE-v2.md`): los nombres los
fija el contrato externo, no este código, y ningún valor de secreto está
escrito en el repositorio. Las dos clases además ya sobreescriben
`toString()` para no filtrar esos campos en logs — la precaución ya estaba
tomada antes de este informe.

## Duplicación (puerta 4.4%, boilerplate 9.2%)

Los bloques que el análisis marca como repetidos son boilerplate de
anotaciones, no lógica de negocio copiada:

- El bloque de 8 líneas repetido en
  `ProfessionalSearchController.java` son los `@ApiResponse` de Swagger
  (400/401/503), con la misma forma en `CurrentAgendaController` y
  `SyncStatusController`: documentan los mismos códigos de error del
  `ApiExceptionHandler` compartido, así que es esperable que se parezcan.
- Los bloques repetidos en `SearchableNameTest`, `IntegrationConfigTest` y
  tests similares son imports estáticos y anotaciones de JUnit/AssertJ
  (`@Test`, `@DisplayName`, `import static
  org.assertj.core.api.Assertions.assertThat`), no setup ni aserciones de
  negocio copiadas. Clases de test chicas y enfocadas en un solo
  comportamiento repiten ese encabezado por diseño.

No se refactorizó nada de esto para bajar la métrica: no hay un defecto que
lo justifique, y extraer la anotación Swagger o una base de test solo por el
heurístico empeoraría la legibilidad de cada punto de uso.

## Qué sí se tomó como señal real

Este documento no vacía la dimensión D1 ni D5: si aparece un acoplamiento
evitable de verdad (una clase con responsabilidades mezcladas, no un adapter
delgado) o un secreto con valor real en el repositorio, se corrige como lo
que es — un issue de `fix` o `refactor`, no una entrada acá.

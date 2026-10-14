# turnos-catalogo-sync

Servicio de **catálogo y sincronización** del proyecto integrador 2026
(sistema distribuido de turnos). Este repo contiene SOLO este servicio.

## Contexto del sistema
- El sistema completo tiene tres repos: app KMP Android, este servicio,
  y el servicio de turnos y reservas.
- El servicio central de la cátedra es la fuente autoritativa del catálogo
  (categorías, profesionales, horarios semanales). Se integra por REST,
  Redis y Kafka.
- **Fuera de este repo (no implementar acá):** disponibilidad de turnos,
  holds, reservas, eventos Kafka de turnos (`alumnos.turnos.acciones.*`,
  `catedra.turnos.telefono.*`) y la app KMP.

## Responsabilidades de este servicio
- Guardar la copia local de categorías, profesionales y horarios semanales.
- Conservar la versión de catálogo aplicada.
- Sincronización completa (snapshot REST) e incremental (Kafka + Redis).
- Detectar discontinuidades y reconstruir la copia local.
- Búsqueda y filtros sobre datos locales: categoría, nombre, habilitado
  y disponibilidad (mínimo).
- Informar estado y errores de sincronización.
- Exponer al servicio de turnos la agenda vigente por un contrato REST
  protegido con JWT.

## Reglas no negociables
- Java + Spring Boot. Base de datos servidor (no H2, SQLite ni en memoria).
  Migraciones propias. Todo levanta con Docker Compose.
- Datos exclusivos: el servicio de turnos nunca accede a estas tablas.
  Integración solo por contrato REST con JWT.
- Las búsquedas nunca consultan al servicio central: solo la copia local.
- La versión local avanza solo cuando la unidad de trabajo terminó
  correctamente (snapshot completo o todos los cambios de una versión).
- Kafka (`CatalogUpdated`) solo notifica; los datos se leen de Redis o
  del snapshot. Deduplicar por `eventId`. Commit de offset después de
  persistir.
- Si la versión local < `oldest-available-version`, hay discontinuidad
  o el estado no se puede verificar: sincronización completa.
- La referencia de integración es la fuente de verdad de los contratos
  externos: no inventar campos, endpoints ni claves.
- Secretos (JWT técnico, `redisPassword`, URL de la API, Redis, Kafka)
  siempre externalizados. El JWT técnico nunca va a la app KMP.
- Arquitectura hexagonal por slices verticales: la decisión y sus desvíos
  están en `docs/adr/0001-arquitectura-hexagonal.md`; el layout y las
  convenciones, en la skill `hexagonal-slicing`
  (`.claude/skills/hexagonal-slicing/SKILL.md`).

## Documentación de la cátedra (leer cuando haga falta)
- `docs/catedra/PROJECT_STATEMENT-v1.md`: enunciado.
  Relevante para este repo: §3, §4.1, §6, §8, §9, §10, §11, §12.
- `docs/catedra/INTEGRATION_REFERENCE-v2.md`: contratos externos.
  Relevante para este repo: §2–§5 (identidades y cuenta técnica),
  §7 (snapshot), §13 (errores REST), §14 (Redis), §15.1–§15.3
  (Kafka y `CatalogUpdated`), §16 (diagrama de sincronización),
  §18.1–§18.2 (recuperación).

## Stack y comandos
Pendiente: se completa en el issue #3 (estructura inicial). Hasta entonces
no hay código de aplicación en el repo.

## Planificación
El trabajo está planificado en GitHub y **eso es la fuente de verdad**:
4 milestones quincenales y 36 issues (#1–#36), cada uno con contexto,
criterios de aceptación, notas técnicas, fuera de alcance y dependencias.

- `M1 - Fundaciones y dominio local` (vence 26/10/2026) — issues #1–#10
- `M2 - Sincronizacion completa y busqueda` (09/11/2026) — issues #11–#21
- `M3 - Sincronizacion incremental y recuperacion` (23/11/2026) — #22–#30
- `M4 - Robustez, seguridad y entrega` (30/11/2026) — issues #31–#36

Para retomar el trabajo en una sesión nueva:
```bash
gh issue list --milestone "M1 - Fundaciones y dominio local" --state open
gh issue view <NN>          # criterios de aceptación y dependencias
gh pr list                  # qué quedó en revisión
```

Los issues se trabajan en orden dentro de cada milestone, porque las
dependencias son casi lineales. Si un issue resulta más grande de lo
previsto, partirlo en issues nuevos del mismo milestone antes de seguir.

## Decisiones cerradas
No volver a plantearlas. Están documentadas en `docs/adr/`.

- **D2 — JHipster: NO se usa.** Confirmado con la cátedra el 2026-10-14:
  §3.1 lo deja opcional. Choca con la hexagonal por slices (estructura
  horizontal, entidades que mezclan JPA/validación/serialización, CRUD que
  acá sobra). Se cumple igual lo exigido: modelo de usuario compatible
  (§3.2, en el repo de turnos) y errores `application/problem+json` con
  `code` estable (§13). Ver `docs/adr/0001-arquitectura-hexagonal.md`.

- **D3 — PostgreSQL + Flyway.** Confirmado el 2026-10-14. Base lógica y
  usuario propios, sin permisos cruzados (§3). Testcontainers en pruebas;
  H2 en ningún perfil. Flyway sobre Liquibase por ser SQL plano versionado.

- **Build: Maven** con wrapper `mvnw` versionado. El enunciado no lo impone;
  se elige por el repo de ejemplo de la cátedra, la skill y Spring Boot.

- **Paquete raíz: `com.um.catalogsync`.** Fijo, no se renombra.

## Decisiones abiertas
Los issues referencian estas decisiones por su ID (`D1`, `D4`, …). Mientras
no estén cerradas, los issues que dependen de ellas no se pueden terminar.
La recomendación es una propuesta, no una decisión tomada.

- **D1 — ¿Dónde viven el registro y login de usuarios finales?**
  Recomendado: en el **servicio de turnos**, que ya es dueño de la propiedad
  por usuario (§4.2); este servicio solo valida el JWT que turnos emite.
  Falta acordar cómo se publica la clave de firma (secreto compartido o
  JWKS). Bloquea #17, #18, #32, #33. Compartida con el repo de turnos.

- **D4 — ¿JWT del usuario propagado o JWT técnico interno entre servicios?**
  Recomendado: la API de agenda (#18) usa un **JWT técnico interno propio
  del proyecto** (distinto del JWT técnico de la cátedra), porque es una
  consulta de catálogo sin propiedad por usuario; la API de búsqueda usa el
  **JWT del usuario final**. Bloquea #17, #18, #32. Compartida con turnos.

- **D5 — ¿KMP consulta la búsqueda directo al catálogo o pasa por turnos?**
  §4.1 pide que los filtros se puedan usar desde KMP sin aclarar la vía.
  Recomendado: **KMP consulta este servicio directamente**, con JWT de
  usuario final y CORS restringido. Bloquea #15, #16, #17. Compartida con
  el repo KMP.

- **D6 — Datos de acceso al entorno de la cátedra.**
  URL de la API, host y puerto de Redis y bootstrap de Kafka se envían por
  otro canal y todavía no llegaron (§3). El alta de la cuenta técnica es
  manual, con Postman, una sola vez (§5.1). Mientras no estén: desarrollar
  contra **WireMock** (REST) y **Testcontainers** (Redis, Kafka), que es
  además lo que necesitan las pruebas obligatorias. Bloquea la verificación
  real de #11–#14 y #22–#28, no su desarrollo.

- **D7 — ¿Para qué se usa `testing` y quién abre las promociones?**
  Pendiente de confirmar con el profesor. Importa porque los `Closes #NN`
  solo cierran issues al mergear en `main`: hasta que haya una promoción,
  los issues quedan abiertos aunque el trabajo esté hecho. No abrir
  promociones sin pedido explícito del usuario.

- **D8 — ¿Se implementa el rol administrador (opcional, §9)?**
  Recomendado: **no**, y menos en este repo: las reservas son del servicio
  de turnos. Si se decide hacerlo, es un issue nuevo allá.

## Git y GitHub (obligatorio)
Seguí la skill `git-flujo-integrador`
(`.claude/skills/git-flujo-integrador/SKILL.md`). Reglas críticas:
- `main`, `develop` y `testing` están protegidas: nunca pushear directo
  ni hacer force push.
- Nada se trabaja sin issue. Rama por issue `tipo/mN-NN-slug`, que sale
  de la rama del milestone `milestone/mN-slug`.
- Conventional Commits con cuerpo (qué y por qué) y `Refs #NN`.
  Nunca `--no-verify`. Hook en `.githooks/`; si no está activo:
  `git config core.hooksPath .githooks`.
- PRs de issue van a la rama del milestone; el PR a `develop` es por
  milestone y lo aprueba el profesor. `Closes #NN` solo en el PR a `main`.
- Pedir confirmación antes de pushear, crear PRs o crear issues.
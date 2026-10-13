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
- Arquitectura hexagonal: seguí la skill `hexagonal-slicing`
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
Pendiente: se completa en el issue de estructura inicial.

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
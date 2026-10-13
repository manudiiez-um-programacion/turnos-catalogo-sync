# turnos-catalogo-sync

Servicio de **catálogo y sincronización** del proyecto integrador 2026
(sistema distribuido de turnos). Este repositorio contiene solamente este
servicio.

## Qué hace este servicio

- Guarda la copia local de categorías, profesionales y horarios semanales.
- Conserva la versión de catálogo aplicada.
- Ejecuta sincronización completa (snapshot REST) e incremental (Kafka + Redis).
- Detecta discontinuidades y reconstruye la copia local.
- Resuelve búsquedas y filtros contra los datos locales: categoría, nombre,
  habilitado y disponibilidad.
- Informa el estado y los errores de sincronización.
- Expone al servicio de turnos la agenda vigente por un contrato REST
  protegido con JWT.

El servicio central de la cátedra es la fuente autoritativa del catálogo. Las
búsquedas nunca lo consultan: se resuelven siempre sobre la copia local.

### Fuera de alcance de este repositorio

Disponibilidad de turnos, holds, reservas, los eventos Kafka de turnos
(`alumnos.turnos.acciones.*`, `catedra.turnos.telefono.*`) y la aplicación KMP.
Eso vive en los otros dos repositorios del proyecto.

## Primeros pasos después de clonar

Este repositorio versiona sus propios git hooks en `.githooks/`. Git no los
activa solo, así que hay **un paso obligatorio por clon**:

```bash
git config core.hooksPath .githooks
```

Sin eso, el hook `commit-msg` no corre y se pueden crear commits que no
cumplen Conventional Commits. Para verificar que quedó activo:

```bash
git config core.hooksPath   # tiene que imprimir: .githooks
```

El hook rechaza cualquier mensaje que no tenga el formato
`tipo(scope): resumen` con cuerpo obligatorio. **Nunca** se saltea con
`--no-verify`: si rechaza un commit, se corrige el mensaje y se reintenta.

### Variables de entorno

```bash
cp .env.example .env
```

Después hay que completar `.env` con los valores reales. `.env` está en
`.gitignore` y no se versiona nunca. Los secretos de integración con la
cátedra (JWT técnico, contraseña de Redis, URLs privadas) no van al código,
ni a la documentación, ni a los logs.

## Stack y comandos

Pendiente: se completa en el issue
[#3](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/3)
(estructura inicial) y el README reproducible completo, en el
[#34](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/34).

## Documentación

- [`docs/catedra/PROJECT_STATEMENT-v1.md`](docs/catedra/PROJECT_STATEMENT-v1.md) — enunciado de la cátedra.
- [`docs/catedra/INTEGRATION_REFERENCE-v2.md`](docs/catedra/INTEGRATION_REFERENCE-v2.md) — contratos externos (REST, Redis, Kafka). Es la fuente de verdad: no se inventan campos, endpoints ni claves.
- `CLAUDE.md` — contexto del repositorio y reglas no negociables.
- `.claude/skills/` — flujo de git del proyecto y convenciones de arquitectura hexagonal.

## Planificación

El trabajo está organizado en cuatro milestones quincenales con 36 issues.
Nada se trabaja sin issue: rama por issue `tipo/mN-NN-slug`, que sale de la
rama del milestone `milestone/mN-slug`.

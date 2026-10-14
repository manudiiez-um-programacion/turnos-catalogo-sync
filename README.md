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

## Stack

| | |
| --- | --- |
| Lenguaje | Java 25 |
| Framework | Spring Boot 4.1.0 |
| Build | Maven, con wrapper versionado (`./mvnw`) |
| Base de datos | PostgreSQL (servidor; §3 prohíbe H2 y embebidas) |
| Documentación de la API | springdoc-openapi / Swagger UI |

Requiere **JDK 25** instalado. No hace falta instalar Maven: el wrapper lo baja.

```bash
java -version   # tiene que decir 25
```

## Comandos

```bash
./mvnw verify            # compila y corre las pruebas
./mvnw spring-boot:run   # levanta el servicio (necesita PostgreSQL)
```

El servicio no arranca sin base de datos ni sin las variables de `.env`: la
configuración no tiene valores por defecto para nada sensible, a propósito.
Levantar todo con Docker Compose llega en el issue
[#4](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/4);
el README reproducible completo, en el
[#34](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/34).

### Perfiles

| Perfil | Para qué |
| --- | --- |
| *(base)* | Común a todos. Swagger apagado. |
| `local` | Desarrollo. Swagger en `/swagger-ui.html`, logs en DEBUG. |
| `test` | Pruebas. La base la provee Testcontainers. |

Se elige con `SPRING_PROFILES_ACTIVE`. Swagger se apaga en cualquier perfil
con `SPRINGDOC_ENABLED=false`.

## Estructura

Arquitectura hexagonal con slices verticales: cada slice es un paquete
autocontenido con sus tres capas adentro.

```
com.um.catalogsync
├── category/        categorías profesionales
├── professional/    profesionales, búsqueda y filtros
├── schedule/        horarios semanales y agenda vigente
├── sync/            versión local, snapshot, incremental, estado
├── integration/     cuenta técnica ante la cátedra
└── shared/          seguridad, errores, OpenAPI, reloj, métricas
```

Cada slice tiene `domain/` (Java puro, sin framework), `application/` (casos de
uso) e `infrastructure/` (adaptadores). Las dependencias apuntan hacia adentro:
ninguna `@Entity` JPA ni DTO web puede aparecer en una firma de `domain` o
`application`. El detalle está en
[`docs/adr/0001-arquitectura-hexagonal.md`](docs/adr/0001-arquitectura-hexagonal.md).

## Documentación

- [`docs/catedra/PROJECT_STATEMENT-v1.md`](docs/catedra/PROJECT_STATEMENT-v1.md) — enunciado de la cátedra.
- [`docs/catedra/INTEGRATION_REFERENCE-v2.md`](docs/catedra/INTEGRATION_REFERENCE-v2.md) — contratos externos (REST, Redis, Kafka). Es la fuente de verdad: no se inventan campos, endpoints ni claves.
- `CLAUDE.md` — contexto del repositorio y reglas no negociables.
- `.claude/skills/` — flujo de git del proyecto y convenciones de arquitectura hexagonal.

## Planificación

El trabajo está organizado en cuatro milestones quincenales con 36 issues.
Nada se trabaja sin issue: rama por issue `tipo/mN-NN-slug`, que sale de la
rama del milestone `milestone/mN-slug`.

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

Para levantar el servicio por primera vez alcanza con poner una contraseña
cualquiera en `DB_PASSWORD` y `POSTGRES_SUPERUSER_PASSWORD`: el resto de las
variables son de integraciones que todavía no están implementadas y pueden
quedar como vienen. Después, [Levantar todo con Docker Compose](#levantar-todo-con-docker-compose).

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
Estos dos comandos son para trabajar desde el IDE; el camino recomendado
para levantar el conjunto es
[Docker Compose](#levantar-todo-con-docker-compose). El README reproducible
completo llega en el issue
[#34](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/34).

### Perfiles

| Perfil | Para qué |
| --- | --- |
| *(base)* | Común a todos. Swagger apagado. |
| `local` | Desarrollo. Swagger en `/swagger-ui.html`, logs en DEBUG. |
| `test` | Pruebas. La base la provee Testcontainers. |

Se elige con `SPRING_PROFILES_ACTIVE`. Swagger se apaga en cualquier perfil
con `SPRINGDOC_ENABLED=false`.

## Levantar todo con Docker Compose

Camino recomendado y el único que no necesita tener JDK 25 ni PostgreSQL
instalados en la máquina: solo Docker.

```bash
cp .env.example .env            # una vez; completar las dos contraseñas
docker compose up --build       # compila la imagen y levanta todo
```

Cuando termina, estas tres direcciones responden:

| | |
| --- | --- |
| `http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| `http://localhost:8080/swagger-ui.html` | Swagger UI (perfil `local`) |
| `localhost:5432` | PostgreSQL, usuario y base `catalogo` |

Comprobación rápida:

```bash
curl http://localhost:8080/actuator/health
```

### Qué levanta y qué no

| Servicio | Dónde corre |
| --- | --- |
| Aplicación (`catalog-sync`) | Este compose |
| PostgreSQL propio (`postgres`) | Este compose |
| Redis de la cátedra | **Externo**, no se levanta acá |
| Kafka de la cátedra | **Externo**, no se levanta acá |

Redis y Kafka son infraestructura **compartida de la cátedra**: se consumen
por red con el host, el usuario y la contraseña que llegan en el objeto
`integration` de la respuesta del login técnico (§5.3 de la referencia de
integración). No tiene sentido levantar copias locales: no tendrían los
datos ni los eventos reales. Lo que las pruebas automatizadas necesiten lo
levanta **Testcontainers** dentro de la propia prueba, no este compose.

### Comandos habituales

```bash
docker compose up --build        # levantar (recompilando la imagen)
docker compose up -d             # levantar en segundo plano
docker compose logs -f           # ver los logs de los dos servicios
docker compose logs -f catalog-sync
docker compose ps                # estado y salud de cada contenedor
docker compose restart catalog-sync
```

Apagar:

```bash
docker compose down              # apaga y borra los contenedores,
                                 # PERO CONSERVA los datos de la base
docker compose down -v           # apaga y BORRA TAMBIÉN el volumen
```

La diferencia importa. Los datos viven en un volumen llamado
`turnos-catalogo-sync-postgres-data`, que sobrevive a `down` y a recrear el
contenedor. `down -v` lo elimina: se pierde la copia local del catálogo y,
en el siguiente `up`, PostgreSQL se inicializa de cero y vuelve a correr el
script de creación de la base.

Eso último es justamente el motivo por el que a veces hace falta `-v`: el
script `docker/postgres/init/01-crear-base-catalogo.sh` corre **una sola
vez**, cuando el volumen está vacío. Si se cambia `DB_NAME`, `DB_USERNAME` o
`DB_PASSWORD` en `.env`, el cambio no tiene efecto hasta borrar el volumen:

```bash
docker compose down -v && docker compose up --build
```

### La base de datos: una base y un usuario propios

El enunciado (§3) permite que varios servicios compartan una misma instancia
de PostgreSQL, siempre que tengan bases lógicas separadas, usuarios **sin
permisos cruzados** y migraciones independientes. Por eso hay dos cuentas:

| Rol | Para qué | Privilegios |
| --- | --- | --- |
| `postgres` (`POSTGRES_SUPERUSER`) | Administración de la instancia. La aplicación nunca lo usa. | Superusuario |
| `catalogo` (`DB_USERNAME`) | La cuenta con la que se conecta el servicio. | Rol común: `LOGIN`, dueño de su base. Sin `SUPERUSER`, `CREATEDB` ni `CREATEROLE` |

El script de inicialización, además de crear el rol y la base, le quita a
`PUBLIC` el permiso de conexión y lo devuelve solo al dueño. Resultado: un
usuario de otro servicio —el de turnos, por ejemplo— no puede conectarse a
esta base ni siquiera si en el futuro comparten la instancia:

```bash
# FATAL: permission denied for database "catalogo"
#        User does not have CONNECT privilege.
```

Las tablas las crean las migraciones, nunca Hibernate
(`spring.jpa.hibernate.ddl-auto: validate`). Llegan en el issue
[#5](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/5).

### La imagen

`Dockerfile` multi-stage, en dos etapas:

1. **`build`** — JDK 25 y el wrapper de Maven. Copia primero `pom.xml` y
   después `src/`, para que un cambio en el código no invalide la capa de
   dependencias descargadas.
2. **`runtime`** — solo un JRE 25 y el jar. El proceso corre con el usuario
   `catalogo` (uid 1001), **sin privilegios**: nunca como `root`.

Las pruebas no corren durante el build de la imagen, porque las de
integración usan Testcontainers y necesitan un demonio Docker que dentro del
build no existe. De eso se encarga `./mvnw verify` en CI (issue
[#10](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/10)).

### Si algo falla

| Síntoma | Causa y solución |
| --- | --- |
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | Ya hay un PostgreSQL ocupando el puerto. Poner otro en `DB_PORT` (por ejemplo `5433`) y ajustar `DB_URL` si se corre la app desde el IDE. |
| `Bind for 0.0.0.0:8080 failed` | Lo mismo con `SERVER_PORT`. |
| `required variable DB_PASSWORD is missing a value` | Falta el `.env` o la variable está vacía. `cp .env.example .env` y completarla. |
| `dependency failed to start: container catalogo-postgres is unhealthy` | PostgreSQL no arrancó. Ver `docker compose logs postgres`. |
| Cambié una credencial en `.env` y sigue la vieja | El script de init ya corrió. `docker compose down -v && docker compose up --build`. |
| `password authentication failed for user "catalogo"` | Mismo caso: el volumen tiene el rol con la contraseña anterior. |

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

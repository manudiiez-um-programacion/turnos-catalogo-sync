<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-72%2F100-green) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-OK-green) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-4.7%25-orange) ![%POO](https://img.shields.io/badge/%25POO-62%25-yellow) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-4-red) ![D2](https://img.shields.io/badge/D2-100-green) ![D4](https://img.shields.io/badge/D4-40-orange) ![D5](https://img.shields.io/badge/D5-100-green) ![D6](https://img.shields.io/badge/D6-100-green) ![D7](https://img.shields.io/badge/D7-100-green)

**Calidad (heurístico, 7 dimensiones):** índice **72/100** · build **OK** · aprobar **no (6/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# turnos-catalogo-sync

[![CI](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/actions/workflows/ci.yml/badge.svg)](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/actions/workflows/ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=manudiiez-um-programacion_turnos-catalogo-sync&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=manudiiez-um-programacion_turnos-catalogo-sync)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=manudiiez-um-programacion_turnos-catalogo-sync&metric=coverage)](https://sonarcloud.io/summary/new_code?id=manudiiez-um-programacion_turnos-catalogo-sync)

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

### Verificación automática

Cada push a cualquier rama, y cada PR hacia `milestone/*`, `develop`,
`testing` y `main`, corre `./mvnw verify` en GitHub Actions
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)). El badge de arriba
muestra el resultado de la rama por defecto.

Los dos disparadores verifican cosas distintas: el push prueba la rama tal
como está, y el PR prueba el **merge** de la rama con su destino, que es lo
que detecta el caso de "mi rama pasa sola, pero mezclada con el destino
rompe".

Es verificación propia, no una barrera de merge: el ruleset del repositorio
no exige status checks y el workflow no lo cambia. Configurar el check como
obligatorio es del profesor.

El job no usa ningún secreto. Las pruebas que necesitan el entorno real de
la cátedra —al que solo se llega por
[ZeroTier](#el-entorno-de-la-cátedra-acceso-por-zerotier)— se marcan con el
tag `catedra` de JUnit y quedan excluidas por defecto:

```java
@Tag("catedra")
class SnapshotRealDeLaCatedraTest { ... }
```

```bash
./mvnw verify              # sin las de la cátedra: es lo que corre CI
./mvnw verify -Pcatedra    # las incluye (requiere estar en la red y .env)
```

Un job que depende de una red privada falla siempre y deja de informar sobre
el código, así que esas pruebas quedan fuera a propósito. El mecanismo es la
propiedad `excluded.test.groups` del `pom.xml`.

### Análisis de calidad: SonarCloud

El mismo job, después de que las pruebas pasan, manda el código a
[SonarCloud](https://sonarcloud.io/summary/new_code?id=manudiiez-um-programacion_turnos-catalogo-sync).
El análisis **lo dispara GitHub Actions**, no el análisis automático de
SonarCloud: los dos a la vez se pisan, así que el automático queda
desactivado en la web.

Qué mide:

| | |
| --- | --- |
| **Bugs y code smells** | Problemas de corrección y mantenibilidad sobre el código Java. |
| **Vulnerabilidades y security hotspots** | Patrones de riesgo: secretos en el código, inyección, criptografía débil. |
| **Cobertura** | La que mide JaCoCo en `./mvnw verify`, leída de `target/site/jacoco/jacoco.xml`. |
| **Duplicación** | Bloques repetidos. |
| **New code** | Lo anterior restringido a lo que cambió. Es la vista que importa: el proyecto puede arrastrar deuda y aun así exigir que lo nuevo entre limpio. |

La cobertura se mide con JaCoCo, que ya corre en `./mvnw verify`:

```bash
./mvnw verify                        # genera target/site/jacoco/jacoco.xml
open target/site/jacoco/index.html   # el reporte navegable, en local
```

`lombok.config` marca el código que genera Lombok con `@lombok.Generated`,
y JaCoCo lo ignora. Sin eso, los getters, setters y builders de cada modelo
contarían como líneas sin probar: el número mediría la cantidad de
anotaciones de Lombok en lugar de la calidad de las pruebas, y bajaría
justo cuando se agrega un modelo bien hecho.

**El token lo provee el dueño del repositorio.** El análisis necesita un
`SONAR_TOKEN` que vive en los *secrets* del repositorio y no aparece en el
código, ni en el `pom.xml`, ni en el workflow, ni en este README. Si el
secret no está configurado, el paso de análisis se omite con un aviso y el
job sigue compilando y probando igual: la verificación del código no
depende de una cuenta externa.

Lo que sí está en el `pom.xml` son `sonar.organization` y
`sonar.projectKey`, que identifican el proyecto y no autorizan nada. Tienen
que coincidir exactamente con los que muestra SonarCloud.

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
integración). Se llega a ellos por ZeroTier, y desde un contenedor hay una
trampa de ruteo: ver
[El entorno de la cátedra](#el-entorno-de-la-cátedra-acceso-por-zerotier). No tiene sentido levantar copias locales: no tendrían los
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

### Las migraciones

Las tablas las crean las migraciones de **Flyway**, nunca Hibernate
(`spring.jpa.hibernate.ddl-auto: validate`). Viven en
`src/main/resources/db/migration` y la aplicación las aplica al arrancar, así
que `docker compose up` sobre una base vacía deja el esquema listo sin ningún
paso extra.

| Migración | Qué crea |
| --- | --- |
| `V1__catalogo_local.sql` | La copia local del catálogo: `professional_category`, `professional`, `weekly_schedule`, con los índices de los filtros |
| `V2__sincronizacion.sql` | El estado propio del servicio: `catalog_version`, `processed_event`, `sync_run`, `sync_error` |

Reglas que conviene saber antes de agregar una:

- **Las migraciones aplicadas no se editan.** Flyway valida el checksum de
  cada una contra el historial: si cambia un archivo ya aplicado, la
  aplicación no arranca. Los cambios van en una migración nueva, `V3`, `V4`.
- **`clean` está deshabilitado** en todos los perfiles: borraría el esquema
  completo. Para empezar de cero, `docker compose down -v`.
- **No hay `baseline-on-migrate`.** Si aparecen tablas sin historial de
  Flyway, es un error y queremos que falle en lugar de asumir una línea base
  que no existe.

El historial se puede mirar en la tabla que Flyway mantiene:

```bash
docker compose exec postgres psql -U catalogo -d catalogo \
  -c 'select version, description, success from flyway_schema_history order by installed_rank'
```

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
| La app no alcanza la API, Redis o Kafka de la cátedra, pero desde el host sí | Solapamiento de subredes con `docker0`. Ver [El entorno de la cátedra](#el-entorno-de-la-cátedra-acceso-por-zerotier). |
| `Migration checksum mismatch` o `Validate failed` al arrancar | Se editó una migración ya aplicada. Se revierte el archivo y el cambio va en una migración nueva; o `docker compose down -v` si la base es descartable. |

## El entorno de la cátedra: acceso por ZeroTier

El servidor de la cátedra **no está en internet**: se alcanza por una red
privada ZeroTier. La API REST, Redis y Kafka corren los tres en la misma IP
—la que la red asigna a ese servidor— en puertos distintos.

Nada de esto hace falta para el desarrollo de M1: el servicio levanta y
responde `UP` sin tocar la cátedra. Se vuelve necesario a partir de
[#11](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/11),
cuando empieza la sincronización.

### Pasos previos

1. Instalar el cliente de ZeroTier.
2. Unirse a la red con el `network_id` que da el profesor.
3. Enviarle el **node ID** del cliente al profesor.
4. Esperar a que autorice el nodo y asigne una IP.

El `network_id`, la IP del servidor y los puertos **no se versionan**: van en
`.env`, igual que el resto de los datos de integración. `.env.example` lleva
solo la forma de cada variable.

Para confirmar que la red está activa:

```bash
ifconfig | grep -B4 'IP-ASIGNADA'    # tiene que aparecer una interfaz con la IP
nc -z -w4 IP-DEL-SERVIDOR PUERTO-API # "succeeded" si el servidor responde
```

### Variables que dependen de esto

`CATEDRA_API_BASE_URL`, `REDIS_HOST`, `REDIS_PORT` y
`KAFKA_BOOTSTRAP_SERVERS`. Dos detalles que ahorran tiempo:

- **`CATEDRA_API_BASE_URL` va sin `/api`.** Todas las rutas de la referencia
  de integración ya lo incluyen (`POST /api/authenticate`,
  `GET /api/synchronization/snapshot`), así que ponerlo también en la URL
  base produce `/api/api/...`.
- **El puerto de Kafka no es el 9092 habitual.** Usar el que indique el
  profesor.

Al verificar rutas a mano, cuidado con una trampa: una ruta inexistente
devuelve **200**, porque cae en el catch-all del frontend del servidor. Lo
que confirma que un endpoint existe es el **401** (existe y pide
autenticación), no el 200.

### El contenedor no alcanza al servidor, pero el host sí

Este es el problema no obvio del entorno. Con la configuración de Docker por
defecto:

```bash
# desde el host: funciona
curl -s -o /dev/null -w '%{http_code}\n' http://IP-DEL-SERVIDOR:PUERTO-API/
# 200

# desde el contenedor: timeout
docker compose exec catalog-sync wget -q -O /dev/null -T 8 http://IP-DEL-SERVIDOR:PUERTO-API/
# wget: download timed out
```

**No es latencia ni firewall: es un solapamiento de subredes.** La IP del
servidor cae dentro de `172.17.0.0/16`, que es el rango que Docker usa por
defecto para su bridge `docker0`. Para verificarlo, dentro de la VM de
Docker Desktop:

```bash
docker run --rm --net=host --privileged alpine:3 ip route get IP-DEL-SERVIDOR
```

Si imprime `dev docker0`, el paquete se está entregando al bridge local en
lugar de salir hacia el host. El motivo está en el orden de las reglas de
ruteo de la VM: la tabla `main` se consulta antes que la que tiene la ruta
por defecto, y `main` contiene `172.17.0.0/16 dev docker0`. Una IP de
internet no matchea ahí y sale bien; la de la cátedra sí matchea y se queda.

Tres formas de resolverlo, de más a menos permanente:

**A. Mover el pool de direcciones de Docker.** Es el arreglo de fondo:
sobrevive a reinicios. En Docker Desktop → Settings → Docker Engine, agregar
al JSON y aplicar:

```json
{
  "default-address-pools": [
    { "base": "10.210.0.0/16", "size": 24 }
  ],
  "bip": "10.211.0.1/24"
}
```

`bip` mueve `docker0` y `default-address-pools` mueve las redes que Docker
crea para los proyectos. **Esto recrea todas las redes de Docker de la
máquina**: los contenedores de otros proyectos se reinician y cambian de IP.
Conviene hacerlo en un momento tranquilo.

**B. Agregar una ruta más específica en la VM.** Desbloquea en el momento,
sin tocar la configuración global ni molestar a otros proyectos, pero **se
pierde cuando Docker Desktop se reinicia**:

```bash
docker run --rm --net=host --privileged alpine:3 \
  ip route add RANGO-DEL-SERVIDOR/24 via 192.168.65.1 dev eth0
```

Al ser `/24` gana sobre el `/16` de `docker0` dentro de la misma tabla.
`192.168.65.1` es el gateway de la VM de Docker Desktop hacia el host. Para
revertirlo, el mismo comando con `ip route del`.

**C. No usar Docker para la app.** Si solo hace falta probar la integración,
alcanza con dejar PostgreSQL en Compose y correr la app en el host, que no
tiene el problema:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Verificado en este repo: el diagnóstico y la opción **B** se probaron de
punta a punta (con la ruta puesta, el contenedor recibe 200 de la API y
alcanza los puertos de Redis y Kafka; al quitarla, vuelve el timeout). La
opción **A** se apoya en el mismo mecanismo pero no se probó en esta máquina,
porque reinicia los contenedores de otros proyectos.

### Latencia

El enlace es relayado, no directo, y se nota:

| | |
| --- | --- |
| RTT | ~228 ms (`min/avg/max = 225/228/233`), 0% de pérdida |
| Abrir la conexión TCP | ~0.23 s |
| Request HTTP completa | ~0.46 s |

Consecuencias: los defaults de Spring no sirven tal cual, cualquier
integración que haga una request por entidad es inviable, y el backoff tiene
que partir de una base mayor a la de una LAN. El ajuste de timeouts y
reintentos es el issue
[#31](https://github.com/manudiiez-um-programacion/turnos-catalogo-sync/issues/31).

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

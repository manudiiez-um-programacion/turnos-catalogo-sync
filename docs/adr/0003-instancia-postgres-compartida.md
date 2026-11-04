# ADR 0003 — Instancia de PostgreSQL compartida con el servicio de turnos

- **Estado:** aceptada
- **Fecha:** 2026-11-01
- **Issue:** #72
- **Decisiones del proyecto que cierra:** ninguna de las listadas (D1–D8);
  decisión nueva, cruzada con `turnos-reservas` (su issue #5)

## Contexto

El sistema tiene dos servicios backend (este y `turnos-reservas`), cada uno
con su propia base de datos PostgreSQL (D3,
[0001](0001-arquitectura-hexagonal.md)). El enunciado (§3) exige un gestor
de base de datos servidor, pero no exige una instancia física por servicio:
permite explícitamente que varios servicios compartan una misma instancia,
siempre que tengan bases lógicas separadas, usuarios sin permisos cruzados y
migraciones independientes.

`turnos-reservas` planteó esta decisión como criterio de aceptación de su
issue #5 (Docker Compose), porque define cómo se levanta la infraestructura
de la solución completa. Las dos opciones eran una instancia física por
servicio o una instancia física compartida con dos bases lógicas. El usuario
decidió la segunda.

Esta ADR solo registra la decisión y el contrato que la hace posible desde
este repo. La instancia compartida en sí no se materializa acá: ver
[Fuera de alcance](#fuera-de-alcance).

## Decisión

La solución completa usa **una sola instancia física de PostgreSQL**, con
dos bases lógicas (`catalogo` y `turnos_reservas`) y dos roles sin permisos
cruzados entre sí, uno por servicio.

Cada repo de servicio conserva su `compose.yaml` autocontenido, con su
propio PostgreSQL de un solo servicio: así cada equipo puede desarrollar y
probar su servicio de forma aislada, sin depender del otro repo. El
`compose.yaml` de este repo sigue siendo, en consecuencia, infraestructura
de **desarrollo aislado del catálogo**, no la instancia de la solución
completa.

La instancia compartida se materializa en un **compose de nivel superior**,
fuera de los dos repos de servicio (en otro repo o carpeta), que monta los
dos directorios de scripts de inicialización sobre el mismo contenedor de
PostgreSQL. Para que eso funcione sin que los scripts de los dos servicios
se pisen, se fija este contrato:

| Punto | Catálogo (este repo) | Turnos |
| --- | --- | --- |
| Imagen | `postgres:18-alpine` | `postgres:18-alpine` |
| Punto de montaje de datos | `/var/lib/postgresql` | `/var/lib/postgresql` |
| Script de init | `docker/postgres/init/01-crear-base-catalogo.sh` | `docker/postgres/init/02-crear-base-turnos.sh` |
| Variables de init | `CATALOG_DB_NAME`, `CATALOG_DB_USERNAME`, `CATALOG_DB_PASSWORD` | `TURNOS_DB_NAME`, `TURNOS_DB_USERNAME`, `TURNOS_DB_PASSWORD` |
| Puerto PostgreSQL publicado | `5432` | `5433` |
| Puerto de la app publicado | `8080` | `8081` |

El prefijo numérico de los scripts (`01-`, `02-`) importa porque el
entrypoint de la imagen de PostgreSQL corre los archivos de
`docker-entrypoint-initdb.d/` en orden alfabético la primera vez que el
volumen está vacío: con prefijos distintos el orden es determinístico
aunque el compose de nivel superior monte los dos directorios juntos.

Lo que ya tiene este repo cubre el resto del contrato sin cambios:
- `docker/postgres/init/01-crear-base-catalogo.sh` ya usa variables
  prefijadas por servicio y no asume ser el único script del directorio.
- El mismo script ya hace `REVOKE ALL ON DATABASE ... FROM PUBLIC` seguido
  de `GRANT CONNECT` solo al dueño, y cierra el esquema `public` de la
  misma forma. Esa es la garantía concreta de que el rol de turnos no puede
  conectarse a la base del catálogo ni viceversa, exigida por el enunciado
  (§3) e independiente de que la instancia sea propia o compartida.
- Los puertos de este repo (`5432`, `8080`) no cambian: ya eran los que el
  contrato le asigna a catálogo.

## Consecuencias

### A favor
- Menos instancias de PostgreSQL para administrar en el despliegue de la
  solución completa (una en vez de dos).
- Cada repo sigue siendo desarrollable y testeable de forma aislada: nadie
  necesita el otro servicio levantado para trabajar en el propio.
- El contrato (imagen, punto de montaje, prefijos, variables, puertos) ya
  estaba satisfecho de este lado antes de que existiera esta decisión, así
  que no hizo falta tocar código: la separación de permisos ya estaba
  pensada para este escenario (ver el comentario original en
  `01-crear-base-catalogo.sh`).

### En contra
- La instancia física pasa a ser un recurso compartido entre dos equipos:
  un problema de capacidad, de rendimiento o una caída en esa instancia
  afecta a los dos servicios a la vez, cosa que no pasaba con instancias
  separadas.
- El compose de nivel superior que la materializa queda fuera de los dos
  repos de servicio, en un tercer lugar que ninguno de los dos controla
  directamente: la trazabilidad de esa pieza de infraestructura no vive en
  ninguno de los repos del equipo.
- Agrega una dependencia operativa entre repos (los prefijos numéricos y
  los nombres de variables) que no existía: un cambio futuro en el esquema
  de nombres de un lado puede romper el montaje conjunto del otro.

## Alternativas consideradas

### Instancia física por servicio (la que había hasta ahora)
Cada repo con su PostgreSQL propio, sin relación entre instancias. Más
simple de razonar y sin acoplamiento entre repos, pero duplica la
infraestructura de base de datos en el despliegue de la solución completa.
Se descartó porque el enunciado permite compartir instancia y el usuario
prefirió esa opción para esa solución completa; no afecta el desarrollo
aislado de cada servicio, que sigue intacto.

## Referencias

- `PROJECT_STATEMENT-v1.md` §3 (permite compartir instancia con bases
  lógicas separadas y usuarios sin permisos cruzados).
- Issue #72 (este repo) y
  `manudiiez-um-programacion/turnos-reservas#5`.
- `docs/adr/0001-arquitectura-hexagonal.md` (D3: PostgreSQL + Flyway).
- `docker/postgres/init/01-crear-base-catalogo.sh` (comentario original que
  anticipaba este escenario).

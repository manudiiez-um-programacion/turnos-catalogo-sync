#!/bin/sh
# Crea la base logica y el usuario propios de este servicio.
#
# Lo ejecuta una unica vez el entrypoint de la imagen de PostgreSQL, cuando
# el volumen de datos esta vacio. Si el volumen ya tiene datos, este script
# NO se vuelve a correr: para re-ejecutarlo hay que borrar el volumen
# (`docker compose down -v`).
#
# Por que un .sh y no un .sql: el entrypoint no interpola variables de
# entorno dentro de los archivos .sql, y el nombre de la base, el usuario y
# la contrasena vienen de .env. Nada de eso se escribe en el repo.
#
# Por que no se usa POSTGRES_USER para el servicio: el usuario que crea
# POSTGRES_USER es superusuario de toda la instancia. El enunciado (§3)
# permite compartir una instancia fisica entre servicios siempre que los
# usuarios no tengan permisos cruzados, y un superusuario los tendria sobre
# los esquemas del servicio de turnos. Asi que el superusuario queda como
# cuenta de administracion y este script crea aparte un rol comun, dueno
# solo de su propia base.

set -eu

echo "[init] creando el rol '${CATALOG_DB_USERNAME}' y la base '${CATALOG_DB_NAME}'"

psql -v ON_ERROR_STOP=1 \
     --username "${POSTGRES_USER}" \
     --dbname "${POSTGRES_DB}" \
     --set=rol="${CATALOG_DB_USERNAME}" \
     --set=clave="${CATALOG_DB_PASSWORD}" \
     --set=base="${CATALOG_DB_NAME}" <<'SQL'
-- Rol de la aplicacion: puede conectarse y nada mas. Sin SUPERUSER no ve ni
-- toca objetos de otras bases; sin CREATEDB ni CREATEROLE no puede
-- escalarse creando una base o un rol nuevos.
CREATE ROLE :"rol" WITH
    LOGIN
    PASSWORD :'clave'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOREPLICATION
    NOBYPASSRLS;

-- Base logica exclusiva de este servicio, de la que el rol es dueno: puede
-- crear sus tablas ahi (las migraciones de #5) y en ningun otro lado.
CREATE DATABASE :"base" WITH OWNER = :"rol" ENCODING = 'UTF8';

-- Por defecto PostgreSQL deja que cualquier rol se conecte a cualquier base.
-- Se quita ese permiso general y se devuelve solo al dueno: si en el futuro
-- la instancia aloja tambien la base del servicio de turnos, su usuario no
-- puede conectarse a esta.
REVOKE ALL ON DATABASE :"base" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"base" TO :"rol";
SQL

psql -v ON_ERROR_STOP=1 \
     --username "${POSTGRES_USER}" \
     --dbname "${CATALOG_DB_NAME}" \
     --set=rol="${CATALOG_DB_USERNAME}" <<'SQL'
-- El esquema public de una base recien creada es visible para todos los
-- roles. Se cierra y se pone a nombre del dueno de la base, que es quien
-- necesita crear tablas ahi.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"rol";
SQL

echo "[init] base '${CATALOG_DB_NAME}' lista"

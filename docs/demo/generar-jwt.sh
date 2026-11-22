#!/usr/bin/env bash
# Firma un JWT HS256 de demo con los secretos del .env local, para pegarlo en
# el entorno de Bruno antes de la defensa (issue #36).
#
# Por que existe: la coleccion de Bruno no puede traer un JWT real porque un
# JWT firmado ES el secreto en otra forma (§9, D1/D4). Este servicio tampoco
# emite tokens: es resource server, nunca authorization server (ver
# docs/seguridad.md). Este script hace, con el .env que ya tiene cada uno en
# su maquina, lo mismo que harian turnos o la app KMP al firmar un token real,
# solo que a mano y para una demo.
#
# Uso:
#   ./docs/demo/generar-jwt.sh usuario   # JWT de usuario final (claim "auth")
#   ./docs/demo/generar-jwt.sh interno   # JWT tecnico interno (claim "authorities")
#
# Lee JWT_USER_SECRET/JWT_USER_ISSUER o JWT_INTERNAL_SECRET/JWT_INTERNAL_ISSUER
# del .env en la raiz del repo. No imprime el secreto, solo el token firmado.

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${RAIZ}/.env"

if [ ! -f "${ENV_FILE}" ]; then
  echo "No existe ${ENV_FILE}. Primero: cp .env.example .env y completar los secretos." >&2
  exit 1
fi

leer_variable() {
  # Toma el valor de una clave del .env sin hacer `source` (evita ejecutar
  # cualquier otra linea del archivo).
  grep -E "^$1=" "${ENV_FILE}" | tail -n1 | cut -d'=' -f2-
}

IDENTIDAD="${1:-}"
case "${IDENTIDAD}" in
  usuario)
    SECRETO="$(leer_variable JWT_USER_SECRET)"
    EMISOR="$(leer_variable JWT_USER_ISSUER)"
    SUJETO="demo.usuario.final"
    CLAIM='"auth":["ROLE_USER"]'
    ;;
  interno)
    SECRETO="$(leer_variable JWT_INTERNAL_SECRET)"
    EMISOR="$(leer_variable JWT_INTERNAL_ISSUER)"
    SUJETO="turnos-reservas-service"
    CLAIM='"authorities":["SERVICE_TURNOS"]'
    ;;
  *)
    echo "Uso: $0 usuario|interno" >&2
    exit 1
    ;;
esac

if [ -z "${SECRETO}" ] || [ -z "${EMISOR}" ]; then
  echo "Falta el secreto o el issuer de '${IDENTIDAD}' en ${ENV_FILE}." >&2
  exit 1
fi

TTL_HORAS="${2:-4}"
AHORA=$(date +%s)
EXPIRA=$((AHORA + TTL_HORAS * 3600))

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

ENCABEZADO=$(printf '{"alg":"HS256","typ":"JWT"}' | base64url)
CUERPO=$(printf '{"sub":"%s","iss":"%s","iat":%d,"exp":%d,%s}' \
  "${SUJETO}" "${EMISOR}" "${AHORA}" "${EXPIRA}" "${CLAIM}" | base64url)

FIRMA=$(printf '%s.%s' "${ENCABEZADO}" "${CUERPO}" \
  | openssl dgst -sha256 -hmac "${SECRETO}" -binary \
  | base64url)

echo "${ENCABEZADO}.${CUERPO}.${FIRMA}"

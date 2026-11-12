#!/usr/bin/env bash
# =============================================================================
# HU-07 · Revisa un archivo de variables antes de subirlo a Jenkins o de desplegar.
#
#   bash deploy/check-env.sh /ruta/segura/circleguard-env-dev
#
# Comprueba que no falte nada, que los secretos tengan el largo y formato que
# exige cada servicio, y que Docker Compose acepte la configuracion completa.
# No imprime ningun secreto.
# =============================================================================
set -euo pipefail

if [[ $# != 1 || ! -s $1 ]]; then
  echo "Uso: $0 <archivo-env>" >&2
  exit 2
fi
env_file=$1
deploy_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
errors=0
fail() { echo "  ERROR $*"; errors=$((errors + 1)); }
pass() { echo "  OK    $*"; }

# Lee el archivo sin ejecutarlo (no se usa source)
declare -A env=()
while IFS= read -r line || [[ -n $line ]]; do
  [[ $line =~ ^[[:space:]]*(#|$) ]] && continue
  if [[ $line =~ ^([A-Z0-9_]+)=(.*)$ ]]; then
    value=${BASH_REMATCH[2]}
    value=${value%\"}; value=${value#\"}; value=${value%\'}; value=${value#\'}
    env[${BASH_REMATCH[1]}]=$value
  else
    fail "linea no valida: ${line%%=*}"
  fi
done < "$env_file"

required=(COMPOSE_PROJECT_NAME COMPOSE_FILE REGISTRY IMAGE_TAG SITE_ADDRESS CORS_ALLOWED_ORIGINS
  DB_USERNAME DB_PASSWORD JWT_SECRET QR_SECRET QR_EXPIRATION VAULT_SECRET VAULT_SALT VAULT_HASH_SALT
  NEO4J_PASSWORD REDIS_PASSWORD LDAP_PASSWORD)
for key in "${required[@]}"; do
  if [[ -z ${env[$key]:-} ]]; then fail "$key vacia o ausente"; fi
done
for key in "${!env[@]}"; do
  [[ ${env[$key]} =~ CAMBIAR|changeme|cambia-esto ]] && fail "$key conserva un valor de ejemplo"
done

[[ ${env[IMAGE_TAG]:-} =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] && pass "IMAGE_TAG con formato vX.Y.Z" || fail "IMAGE_TAG debe ser vX.Y.Z (nunca latest)"
for key in JWT_SECRET QR_SECRET; do
  (( ${#env[$key]} >= 32 )) && pass "$key de ${#env[$key]} caracteres" || fail "$key necesita al menos 32 caracteres (HMAC-SHA256)"
done
if [[ ${env[QR_EXPIRATION]:-x} =~ ^[0-9]+$ ]] && (( ${env[QR_EXPIRATION]} >= 1000 )); then
  pass "QR_EXPIRATION=${env[QR_EXPIRATION]} ms ($(( ${env[QR_EXPIRATION]} / 1000 )) s)"
else
  fail "QR_EXPIRATION va en milisegundos y debe ser >= 1000 (60000 = 60 s)"
fi
[[ ${env[VAULT_SALT]:-} =~ ^([0-9a-fA-F]{2})+$ ]] && pass "VAULT_SALT en hexadecimal" || fail "VAULT_SALT debe ser hexadecimal de largo par (Encryptors.text)"
(( ${#env[NEO4J_PASSWORD]} >= 8 )) && pass "NEO4J_PASSWORD de al menos 8 caracteres" || fail "NEO4J_PASSWORD necesita al menos 8 caracteres (Neo4j 5)"
first_site=${env[SITE_ADDRESS]%%,*}
[[ ${env[CORS_ALLOWED_ORIGINS]:-} == *"https://${first_site// /}"* ]] && pass "CORS_ALLOWED_ORIGINS incluye https://${first_site// /}" \
  || fail "CORS_ALLOWED_ORIGINS deberia incluir https://${first_site// /}"
case ${env[COMPOSE_FILE]:-} in
  compose.yml:compose.dev.yml|compose.yml:compose.prod.yml) pass "COMPOSE_FILE=${env[COMPOSE_FILE]}" ;;
  *) fail "COMPOSE_FILE debe ser compose.yml:compose.dev.yml (dev) o compose.yml:compose.prod.yml (stage y prod)" ;;
esac
if [[ ${env[COMPOSE_PROJECT_NAME]:-} == *dev && ${env[COMPOSE_FILE]:-} != *dev* ]] || [[ ${env[COMPOSE_PROJECT_NAME]:-} != *dev && ${env[COMPOSE_FILE]:-} == *dev* ]]; then
  fail "COMPOSE_PROJECT_NAME y COMPOSE_FILE no corresponden al mismo ambiente"
fi

if command -v docker >/dev/null && docker compose version >/dev/null 2>&1; then
  files=()
  IFS=: read -r -a parts <<<"${env[COMPOSE_FILE]}"
  for part in "${parts[@]}"; do files+=(-f "$deploy_dir/$part"); done
  if out=$(docker compose --project-directory "$deploy_dir" --env-file "$env_file" "${files[@]}" config --quiet 2>&1); then
    pass "docker compose acepta la configuracion completa"
  else
    fail "docker compose rechaza la configuracion: $(sed 's/=.*/=<oculto>/' <<<"$out" | head -3)"
  fi
else
  echo "  AVISO docker compose no esta instalado aqui: no se valido el Compose"
fi

if (( errors > 0 )); then
  echo "RESULTADO: $errors problema(s). Corrigelos antes de desplegar."
  exit 1
fi
echo "RESULTADO: archivo listo para el ambiente ${env[COMPOSE_PROJECT_NAME]}"

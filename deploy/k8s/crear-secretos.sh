#!/usr/bin/env bash
# HU-18 / HU-20 · Crea (o actualiza) el Secret `circleguard-secrets` de un ambiente.
#
#   DB_USERNAME=... DB_PASSWORD=... JWT_SECRET=... QR_SECRET=... LDAP_PASSWORD=... \
#   VAULT_SECRET=... VAULT_SALT=... VAULT_HASH_SALT=... NEO4J_PASSWORD=... REDIS_PASSWORD=... \
#   INTERNAL_API_KEY=... \
#   bash deploy/k8s/crear-secretos.sh dev
#
# Los valores llegan por variables de entorno (Jenkins Credentials en el pipeline) y
# nunca se escriben en el repositorio ni se imprimen. Es idempotente.
set -euo pipefail

ambiente="${1:?Uso: crear-secretos.sh <dev|stage|prod>}"
namespace="circleguard-${ambiente}"
claves=(DB_USERNAME DB_PASSWORD JWT_SECRET QR_SECRET LDAP_PASSWORD VAULT_SECRET VAULT_SALT VAULT_HASH_SALT NEO4J_PASSWORD REDIS_PASSWORD INTERNAL_API_KEY)

args=()
for clave in "${claves[@]}"; do
  [[ -n "${!clave:-}" ]] || { echo "Falta la variable $clave" >&2; exit 1; }
  args+=("--from-literal=${clave}=${!clave}")
done

kubectl create namespace "$namespace" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl -n "$namespace" create secret generic circleguard-secrets "${args[@]}" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
echo "Secret circleguard-secrets listo en $namespace (${#claves[@]} claves)"

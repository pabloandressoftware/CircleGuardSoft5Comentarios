#!/usr/bin/env bash
set -euo pipefail
environment="${1:?Uso: verify-no-changes.sh <dev|stage|prod>}"
[[ "$environment" =~ ^(dev|stage|prod)$ ]] || exit 1
repo=$(cd "$(dirname "$0")/../../.." && pwd)
evidence="$repo/.local-evidence/hu-17/$environment/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$evidence"
cd "$repo/infra/terraform/envs/$environment"
set +e
terraform plan -input=false -lock-timeout=5m -detailed-exitcode -no-color > "$evidence/no-changes.txt" 2>&1
code=$?
set -e
cat "$evidence/no-changes.txt"
printf 'environment=%s\nexit_code=%s\nutc=%s\n' "$environment" "$code" "$(date -u +%FT%TZ)" > "$evidence/result.txt"
if [[ "$code" != 0 ]]; then
  echo "No se demostró idempotencia: 1=error; 2=cambios pendientes. Evidencia: $evidence" >&2
  exit 1
fi
echo "Plan sin cambios (exit 0). Evidencia: $evidence"

#!/usr/bin/env bash
# Demostración REAL: terraform console mantiene el lock; otro plan debe fallar.
# No inserta locks artificiales ni aplica cambios; no usar durante un despliegue.
set -euo pipefail
environment="${1:?Uso: verify-lock.sh <dev|stage|prod>}"
[[ "$environment" =~ ^(dev|stage|prod)$ ]] || exit 1
repo=$(cd "$(dirname "$0")/../../.." && pwd)
evidence="$repo/.local-evidence/hu-17/$environment/lock-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$evidence"
cd "$repo/infra/terraform/envs/$environment"
field() { sed -nE "s/^$1[[:space:]]*=[[:space:]]*\"([^\"]+)\".*/\1/p" backend.hcl; }
bucket=$(field bucket); key=$(field key); table=$(field dynamodb_table); region=$(field region)
[[ -n "$bucket" && -n "$key" && -n "$table" && -n "$region" ]] || { echo 'backend.hcl incompleto' >&2; exit 1; }
lock_key="{\"LockID\":{\"S\":\"$bucket/$key\"}}"
read_lock() { aws dynamodb get-item --region "$region" --table-name "$table" --key "$lock_key" --consistent-read --query Item.Info.S --output text; }
[[ "$(read_lock)" == None ]] || { echo 'Ya existe un lock: no interferir con otra operación.' >&2; exit 1; }
pipe=$(mktemp -u); mkfifo "$pipe"
holder=''
cleanup() {
  if [[ -n "$holder" ]]; then
    printf 'exit\n' >&3 || true
    exec 3>&-
    wait "$holder" || true
  fi
  rm -f "$pipe"
}
trap cleanup EXIT
terraform console -no-color < "$pipe" > "$evidence/holder.txt" 2>&1 &
holder=$!
exec 3> "$pipe"
acquired=false
for attempt in $(seq 1 30); do
  kill -0 "$holder" 2>/dev/null || { cat "$evidence/holder.txt" >&2; exit 1; }
  info=$(read_lock)
  if [[ "$info" != None ]]; then
    printf '%s\n' "$info" > "$evidence/dynamodb-lock.json"
    acquired=true; break
  fi
  sleep 1
done
[[ "$acquired" == true ]] || { echo 'No se observó el lock de Terraform en DynamoDB.' >&2; exit 1; }
set +e
terraform plan -input=false -lock-timeout=3s -no-color > "$evidence/contender.txt" 2>&1
code=$?
set -e
[[ "$code" != 0 ]] && grep -q 'Error acquiring the state lock' "$evidence/contender.txt" || {
  cat "$evidence/contender.txt" >&2; echo 'El segundo plan no falló por bloqueo.' >&2; exit 1;
}
cleanup; holder=''; trap - EXIT
[[ "$(read_lock)" == None ]] || { echo 'El lock no se liberó; revisar al propietario, no usar force-unlock automáticamente.' >&2; exit 1; }
terraform plan -input=false -lock-timeout=5m -no-color > "$evidence/released-plan.txt" 2>&1
printf 'environment=%s\ncontender_exit=%s\nlock_released=true\n' "$environment" "$code" > "$evidence/result.txt"
echo "Lock observado en DynamoDB, segundo plan bloqueado y plan final correcto: $evidence"

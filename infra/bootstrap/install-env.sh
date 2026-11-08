#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 2 ]]; then
  echo "Uso: $0 <usuario@host> <archivo-env-de-Jenkins>" >&2
  exit 2
fi

target=$1
env_file=$2
if [[ ! $target =~ ^[a-zA-Z0-9._-]+@[a-zA-Z0-9][a-zA-Z0-9.-]*$ || ! -s $env_file ]]; then
  echo 'Se necesita un destino SSH usuario@host y un archivo de credenciales no vacío.' >&2
  exit 2
fi

ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -- "$target" '
  set -eu
  umask 077
  cd /opt/circleguard
  incoming=$(mktemp .env.XXXXXX)
  trap "rm -f -- \"$incoming\"" EXIT
  cat > "$incoming"
  test -s "$incoming"
  mv -fT -- "$incoming" .env
' < "$env_file"

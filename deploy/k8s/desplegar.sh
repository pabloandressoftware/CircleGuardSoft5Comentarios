#!/usr/bin/env bash
# HU-18 · Despliega los servicios y el cliente web con el chart generico.
#
#   bash deploy/k8s/desplegar.sh dev v1.0.0            # todos
#   bash deploy/k8s/desplegar.sh dev v1.0.0 gateway-service web   # solo algunos
#
# La misma etiqueta SemVer se usa en dev, stage y prod (imagen inmutable).
set -euo pipefail

ambiente="${1:?Uso: desplegar.sh <dev|stage|prod> <vX.Y.Z> [servicio...]}"
etiqueta="${2:?Falta la etiqueta de la imagen (vX.Y.Z)}"
shift 2
aqui="$(cd "$(dirname "$0")/../helm" && pwd)"
ns="circleguard-${ambiente}"

servicios=("$@")
if [[ ${#servicios[@]} -eq 0 ]]; then
  servicios=(identity-service auth-service promotion-service form-service gateway-service notification-service file-service dashboard-service web)
fi

for s in "${servicios[@]}"; do
  helm upgrade --install "$s" "$aqui/circleguard-service" -n "$ns" \
    -f "$aqui/values/$s.yaml" -f "$aqui/envs/$ambiente.yaml" \
    --set image.tag="$etiqueta" --wait --timeout 10m
done

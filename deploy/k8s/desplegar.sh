#!/usr/bin/env bash
# HU-18 / HU-19 · Despliega los servicios y el cliente web con el chart generico.
#
#   bash deploy/k8s/desplegar.sh dev v1.0.0            # todos
#   bash deploy/k8s/desplegar.sh dev v1.0.0 gateway-service web   # solo algunos
#
# La misma etiqueta SemVer se usa en dev, stage y prod (imagen inmutable).
# El registro se toma de REGISTRY (por defecto 127.0.0.1:5000) para que el
# mismo script sirva en kind, k3s o cualquier cluster que alcance Nexus.
# Si NX_USER y NX_PASS estan definidos, crea/actualiza el imagePullSecret
# `nexus-registry` del namespace. El pipeline ya lo hace desde Jenkins.
set -euo pipefail

ambiente="${1:?Uso: desplegar.sh <dev|stage|prod> <vX.Y.Z> [servicio...]}"
etiqueta="${2:?Falta la etiqueta de la imagen (vX.Y.Z)}"
shift 2
aqui="$(cd "$(dirname "$0")/../helm" && pwd)"
ns="circleguard-${ambiente}"
registry="${REGISTRY:-127.0.0.1:5000}"
helm_timeout="${HELM_TIMEOUT:-10m}"
rollout_timeout="${ROLLOUT_TIMEOUT:-600s}"

servicios=("$@")
if [[ ${#servicios[@]} -eq 0 ]]; then
  servicios=(identity-service auth-service promotion-service form-service gateway-service notification-service file-service dashboard-service web)
fi

kubectl apply -f "$aqui/../k8s/namespaces.yaml" >/dev/null

if [[ -n "${NX_USER:-}" && -n "${NX_PASS:-}" ]]; then
  kubectl -n "$ns" create secret docker-registry nexus-registry \
    --docker-server="$registry" \
    --docker-username="$NX_USER" \
    --docker-password="$NX_PASS" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
fi

for s in "${servicios[@]}"; do
  if [[ "$s" == "web" ]]; then
    repositorio="${registry}/circleguard/web"
  else
    repositorio="${registry}/circleguard/circleguard-${s}"
  fi
  echo "helm upgrade --install ${s} (${repositorio}:${etiqueta}) en ${ns}"
  public_values=()
  if [[ -n ${SITE_ADDRESS:-} ]]; then
    public_host=${SITE_ADDRESS%%,*}
    [[ $public_host =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ ]] || { echo 'SITE_ADDRESS invalido.' >&2; exit 2; }
    public_values=(--set-string "ingress.host=$public_host"
      --set-string "ingress.tls[0].hosts[0]=$public_host"
      --set-string "env.CORS_ALLOWED_ORIGINS=https://$public_host"
      --set-string "ingress.annotations.cert-manager\\.io/cluster-issuer=${TLS_ISSUER:-circleguard-selfsigned}")
  fi
  helm upgrade --install "$s" "$aqui/circleguard-service" -n "$ns" \
    -f "$aqui/values/$s.yaml" -f "$aqui/envs/$ambiente.yaml" \
    --set image.repository="$repositorio" \
    --set image.tag="$etiqueta" \
    --set "imagePullSecrets[0].name=nexus-registry" \
    "${public_values[@]}" \
    --wait --timeout "$helm_timeout"
  kubectl -n "$ns" rollout status "deployment/$s" --timeout="$rollout_timeout"
done

echo "Servicios desplegados en ${ns} con la etiqueta ${etiqueta}."

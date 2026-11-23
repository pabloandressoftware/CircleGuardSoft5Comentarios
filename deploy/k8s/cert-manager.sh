#!/usr/bin/env bash
# HU-21 · Instala cert-manager y crea el ClusterIssuer autofirmado de CircleGuard.
#
#   bash deploy/k8s/cert-manager.sh
#
# Es idempotente. Despues, los Ingress de dev/stage/prod piden su certificado
# con la anotacion cert-manager.io/cluster-issuer=circleguard-selfsigned
# (ver deploy/helm/envs/<ambiente>.yaml). El humo usa curl -k porque la CA es
# autofirmada; cuando HU-17/HU-21 tenga un dominio publico se puede cambiar por
# un Issuer de Let's Encrypt.
set -euo pipefail

version="${CERT_MANAGER_VERSION:-v1.16.2}"
aqui="$(cd "$(dirname "$0")" && pwd)"

helm repo add jetstack https://charts.jetstack.io >/dev/null
helm repo update >/dev/null

helm upgrade --install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace \
  --version "$version" \
  --set crds.enabled=true \
  --wait --timeout 10m

kubectl apply -f "$aqui/issuers.yaml"
kubectl wait --for=condition=Ready clusterissuer/circleguard-selfsigned --timeout=120s

echo "cert-manager ${version} y el ClusterIssuer circleguard-selfsigned estan listos."

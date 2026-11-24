#!/usr/bin/env bash
# HU-21 · Instala cert-manager y el emisor local o publico de CircleGuard.
#
#   bash deploy/k8s/cert-manager.sh
#
# Es idempotente. TLS_ISSUER_MODE=acme y ACME_EMAIL habilitan Let's Encrypt
# HTTP-01 sobre Traefik; sin ese modo conserva el emisor local autofirmado.
set -euo pipefail

version="${CERT_MANAGER_VERSION:-v1.21.3}"
mode="${TLS_ISSUER_MODE:-selfsigned}"
if [[ $mode != selfsigned && $mode != acme ]]; then
  echo 'TLS_ISSUER_MODE debe ser selfsigned o acme.' >&2
  exit 2
fi
if [[ $mode == acme ]]; then
  [[ ${ACME_EMAIL:-} =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$ ]] || {
    echo 'Define ACME_EMAIL para registrar el emisor publico.' >&2
    exit 2
  }
fi
aqui="$(cd "$(dirname "$0")" && pwd)"

helm repo add jetstack https://charts.jetstack.io >/dev/null
helm repo update >/dev/null

helm upgrade --install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace \
  --version "$version" \
  --set crds.enabled=true \
  --wait --timeout 10m

if [[ $mode == acme ]]; then
  issuer=circleguard-letsencrypt
  cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: $issuer
spec:
  acme:
    email: $ACME_EMAIL
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: circleguard-acme-account
    solvers:
      - http01:
          ingress:
            ingressClassName: traefik
EOF
else
  issuer=circleguard-selfsigned
  kubectl apply -f "$aqui/issuers.yaml"
fi
kubectl wait --for=condition=Ready "clusterissuer/$issuer" --timeout=120s
echo "cert-manager ${version} y el ClusterIssuer $issuer estan listos."

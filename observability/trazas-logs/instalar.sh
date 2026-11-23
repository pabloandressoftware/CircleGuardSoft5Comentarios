#!/usr/bin/env bash
# HU-25 · Instala Jaeger (trazas) y Loki + Promtail (logs) en el namespace monitoring y los
# conecta a Grafana. No modifica el stack de HU-24: se corre despues de observability/instalar.sh.
#
#   bash observability/trazas-logs/instalar.sh
#
# Despues hay que redesplegar form, promotion y notification para que arranquen con el agente
# (deploy/k8s/desplegar.sh <ambiente> <vX.Y.Z> form-service promotion-service notification-service).
set -euo pipefail

aqui="$(cd "$(dirname "$0")" && pwd)"
monitoring_ns=monitoring
loki_version="${LOKI_STACK_VERSION:-2.10.2}"

command -v helm >/dev/null || { echo 'Falta helm.' >&2; exit 1; }
command -v kubectl >/dev/null || { echo 'Falta kubectl.' >&2; exit 1; }

kubectl get namespace "$monitoring_ns" >/dev/null 2>&1 \
  || kubectl create namespace "$monitoring_ns"

kubectl apply -f "$aqui/jaeger.yaml"
kubectl -n "$monitoring_ns" rollout status deployment/jaeger --timeout=300s

helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
helm repo update >/dev/null

helm upgrade --install loki grafana/loki-stack \
  -n "$monitoring_ns" \
  --version "$loki_version" \
  -f "$aqui/loki-stack-values.yaml" \
  --wait --timeout 10m

kubectl apply -f "$aqui/datasources.yaml"

cat <<EOF
Jaeger y Loki instalados en $monitoring_ns.

Jaeger:   kubectl -n $monitoring_ns port-forward svc/jaeger 16686:16686
          http://127.0.0.1:16686
Grafana:  kubectl -n $monitoring_ns port-forward svc/kube-prometheus-stack-grafana 3000:80
          http://127.0.0.1:3000 -> Explore -> Loki o Jaeger
EOF

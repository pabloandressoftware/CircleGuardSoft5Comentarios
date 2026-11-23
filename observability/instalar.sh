#!/usr/bin/env bash
# HU-24 · Instala kube-prometheus-stack, el ServiceMonitor, las alertas y los
# dashboards de CircleGuard.
#
#   CI_DISCORD_WEBHOOK='https://discord.com/api/webhooks/...' \
#   GRAFANA_ADMIN_PASSWORD='...' \
#   bash observability/instalar.sh
#
# El webhook es el mismo de Spidey que usa Jenkins (credencial discord-webhook).
# Nunca se escribe en el repositorio: se guarda en el Secret alertmanager-discord.
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
monitoring_ns=monitoring
release=kube-prometheus-stack

webhook="${CI_DISCORD_WEBHOOK:-${DISCORD_WEBHOOK:-}}"
if [[ -z "$webhook" ]]; then
  echo 'Define CI_DISCORD_WEBHOOK o DISCORD_WEBHOOK con la URL de Spidey.' >&2
  exit 2
fi
: "${GRAFANA_ADMIN_PASSWORD:?Define GRAFANA_ADMIN_PASSWORD para el usuario admin de Grafana}"

command -v helm >/dev/null || { echo 'Falta helm.' >&2; exit 1; }
command -v kubectl >/dev/null || { echo 'Falta kubectl.' >&2; exit 1; }

kubectl get namespace "$monitoring_ns" >/dev/null 2>&1 \
  || kubectl create namespace "$monitoring_ns"

kubectl -n "$monitoring_ns" create secret generic grafana-admin \
  --from-literal=admin-user=admin \
  --from-literal=admin-password="$GRAFANA_ADMIN_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n "$monitoring_ns" create secret generic alertmanager-discord \
  --from-literal=webhook-url="$webhook" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update >/dev/null

version="${KUBE_PROMETHEUS_VERSION:-65.5.1}"
helm upgrade --install "$release" prometheus-community/kube-prometheus-stack \
  -n "$monitoring_ns" \
  --version "$version" \
  -f "$raiz/observability/kube-prometheus-stack/values.yaml" \
  --wait --timeout 15m

kubectl apply -f "$raiz/observability/servicemonitors.yaml"

# PrometheusRule: envuelve las reglas que tambien usa promtool.
{
  cat <<'EOF'
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: circleguard-alertas
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
EOF
  sed 's/^/  /' "$raiz/observability/alerts/rules.yaml"
} | kubectl apply -f -

for dashboard in tecnico negocio; do
  file="$raiz/observability/grafana/$dashboard.json"
  configmap="circleguard-grafana-$dashboard"
  kubectl -n "$monitoring_ns" create configmap "$configmap" \
    --from-file="$dashboard.json=$file" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  kubectl -n "$monitoring_ns" label configmap "$configmap" \
    grafana_dashboard=1 grafana_folder=CircleGuard --overwrite >/dev/null
done

cat <<EOF
kube-prometheus-stack $version instalado en $monitoring_ns.

Grafana:     kubectl -n $monitoring_ns port-forward svc/$release-grafana 3000:80
             http://127.0.0.1:3000  (usuario admin)
Prometheus:  kubectl -n $monitoring_ns port-forward svc/$release-kube-prometheus-prometheus 9090:9090
Alertmanager:kubectl -n $monitoring_ns port-forward svc/$release-kube-prometheus-alertmanager 9093:9093

El ServiceMonitor recolecta los 8 servicios Java de los 3 ambientes.
EOF

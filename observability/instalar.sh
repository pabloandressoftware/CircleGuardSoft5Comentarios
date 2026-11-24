#!/usr/bin/env bash
# HU-24 · Instala kube-prometheus-stack, el ServiceMonitor, las alertas y los
# dashboards de CircleGuard.
#
#   GRAFANA_ADMIN_PASSWORD='...' bash observability/instalar.sh
#   CI_DISCORD_WEBHOOK='https://discord.com/api/webhooks/...' \
#   GRAFANA_ADMIN_PASSWORD='...' bash observability/instalar.sh   # con Discord
#
# Se instala una vez por cluster (dev, stage y prod son clusters distintos):
# usar el KUBECONFIG del ambiente. El webhook es OPCIONAL. Sin el, las alertas
# solo quedan en Alertmanager (API/UI) y no se envian notificaciones externas.
# Si se define, es el mismo de Spidey que usa Jenkins (credencial discord-webhook)
# y nunca se escribe en el repositorio: va dentro del Secret de configuracion.
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
monitoring_ns=monitoring
release=kube-prometheus-stack

webhook="${CI_DISCORD_WEBHOOK:-${DISCORD_WEBHOOK:-}}"
: "${GRAFANA_ADMIN_PASSWORD:?Define GRAFANA_ADMIN_PASSWORD para el usuario admin de Grafana}"

command -v helm >/dev/null || { echo 'Falta helm.' >&2; exit 1; }
command -v kubectl >/dev/null || { echo 'Falta kubectl.' >&2; exit 1; }

kubectl get namespace "$monitoring_ns" >/dev/null 2>&1 \
  || kubectl create namespace "$monitoring_ns"

kubectl -n "$monitoring_ns" create secret generic grafana-admin \
  --from-literal=admin-user=admin \
  --from-literal=admin-password="$GRAFANA_ADMIN_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

extra_args=()
config_secret=alertmanager-circleguard-discord
if [[ -n "$webhook" ]]; then
  case "$webhook" in
    https://discord.com/api/webhooks/*|https://discordapp.com/api/webhooks/*) ;;
    *) echo 'CI_DISCORD_WEBHOOK no parece un webhook de Discord.' >&2; exit 2 ;;
  esac
  # Alertmanager 0.27 no admite webhook_url_file en discord_configs: la URL va
  # en la configuracion completa, guardada solo en este Secret del cluster.
  config_file="$(mktemp)"
  trap 'rm -f "$config_file"' EXIT
  cat > "$config_file" <<EOF
global:
  resolve_timeout: 5m
route:
  receiver: local
  group_by: [alertname, namespace]
  group_wait: 10s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - matchers:
        - severity="critical"
      receiver: discord
receivers:
  - name: local
  - name: discord
    discord_configs:
      - webhook_url: '${webhook}'
        send_resolved: true
        title: '{{ .CommonAnnotations.summary }}'
        message: |
          {{ range .Alerts }}{{ .Annotations.description }}
          {{ end }}
EOF
  kubectl -n "$monitoring_ns" create secret generic "$config_secret" \
    --from-file=alertmanager.yaml="$config_file" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  extra_args+=(--set "alertmanager.alertmanagerSpec.configSecret=$config_secret")
  echo 'Alertmanager: alertas criticas hacia Discord (Secret de configuracion).'
else
  echo 'Alertmanager: sin CI_DISCORD_WEBHOOK; las alertas quedan solo en Alertmanager.'
  echo 'Discord queda PENDIENTE: este modo no envia notificaciones externas.'
fi

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update >/dev/null

version="${KUBE_PROMETHEUS_VERSION:-65.5.1}"
helm upgrade --install "$release" prometheus-community/kube-prometheus-stack \
  -n "$monitoring_ns" \
  --version "$version" \
  -f "$raiz/observability/kube-prometheus-stack/values.yaml" \
  "${extra_args[@]}" \
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

El ServiceMonitor recolecta los 8 servicios Java del ambiente de este cluster.
EOF

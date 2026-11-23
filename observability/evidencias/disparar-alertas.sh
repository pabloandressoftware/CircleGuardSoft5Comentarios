#!/usr/bin/env bash
# HU-24 · Dispara deliberadamente tres alertas criticas y guarda evidencia.
#
#   bash observability/evidencias/disparar-alertas.sh [service-down|high-errors|gate-denials|all]
#
# Requiere kubectl, curl y jq contra un cluster con CircleGuard y kube-prometheus-stack.
# Siempre restaura los recursos al terminar (trap). No uses este script en prod.
set -euo pipefail

mode="${1:-all}"
env_name="${ENV_NAME:-dev}"
ns="circleguard-${env_name}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="observability/evidencias/${stamp}"
mkdir -p "$out"

command -v kubectl >/dev/null || { echo 'Falta kubectl.' >&2; exit 1; }
command -v curl >/dev/null || { echo 'Falta curl.' >&2; exit 1; }
command -v jq >/dev/null || { echo 'Falta jq.' >&2; exit 1; }

pids=()
cleanup() {
  for pid in "${pids[@]:-}"; do
    kill "$pid" >/dev/null 2>&1 || true
  done
  kubectl -n "$ns" get deployment/web >/dev/null 2>&1 \
    && kubectl -n "$ns" scale deployment/web --replicas=1 >/dev/null 2>&1 || true
  kubectl -n "$ns" get statefulset/postgres >/dev/null 2>&1 \
    && kubectl -n "$ns" scale statefulset/postgres --replicas=1 >/dev/null 2>&1 || true
}
trap cleanup EXIT

start_forward() {
  local svc=$1 local_port=$2 remote_port=$3
  kubectl -n "$ns" port-forward "svc/$svc" "${local_port}:${remote_port}" >/dev/null 2>&1 &
  pids+=("$!")
  for _ in $(seq 1 30); do
    if curl -fsS --max-time 2 "http://127.0.0.1:${local_port}/" >/dev/null 2>&1 \
      || curl -sS --max-time 2 "http://127.0.0.1:${local_port}/" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "No se pudo abrir port-forward para $svc." >&2
  return 1
}

alertmanager_url="${ALERTMANAGER_URL:-}"
if [[ -z "$alertmanager_url" ]]; then
  kubectl -n monitoring port-forward svc/kube-prometheus-stack-kube-prometheus-alertmanager 19093:9093 >/dev/null 2>&1 &
  pids+=("$!")
  alertmanager_url="http://127.0.0.1:19093"
  sleep 2
fi

capture() {
  local name=$1 dir="$out/$name"
  mkdir -p "$dir"
  kubectl -n "$ns" get pods -o wide > "$dir/pods.txt" 2>&1 || true
  curl -fsS "$alertmanager_url/api/v2/alerts" > "$dir/alerts.json" 2>&1 || true
  cp "$dir/alerts.json" "$dir/alertmanager.json"
}

wait_alert() {
  local name=$1
  for _ in $(seq 1 40); do
    if curl -fsS "$alertmanager_url/api/v2/alerts" 2>/dev/null \
      | jq -e --arg n "$name" 'any(.[]; .labels.alertname == $n)' >/dev/null; then
      echo "Alerta $name visible en Alertmanager."
      return 0
    fi
    sleep 15
  done
  echo "La alerta $name no aparecio en Alertmanager." >&2
  return 1
}

service_down() {
  echo '>>> CircleguardServiceDown: se escala auth-service a 0'
  kubectl -n "$ns" scale deployment/auth-service --replicas=0 >/dev/null
  wait_alert CircleguardServiceDown
  capture 01-service-down
  kubectl -n "$ns" scale deployment/auth-service --replicas=2 >/dev/null
}

high_errors() {
  echo '>>> CircleguardHigh5xxRate: se detiene PostgreSQL y se golpea form-service'
  kubectl -n "$ns" scale statefulset/postgres --replicas=0 >/dev/null
  start_forward form-service 18086 8086
  for _ in $(seq 1 300); do
    curl -sS --max-time 3 -o /dev/null "http://127.0.0.1:18086/api/v1/questionnaires/active" || true
  done
  wait_alert CircleguardHigh5xxRate
  capture 02-high-5xx
  kubectl -n "$ns" scale statefulset/postgres --replicas=1 >/dev/null
}

gate_denials() {
  echo '>>> CircleguardGateDenialSpike: se envian tokens invalidos al gateway'
  start_forward gateway-service 18087 8087
  for _ in $(seq 1 300); do
    curl -sS --max-time 3 -o /dev/null -X POST \
      -H 'Content-Type: application/json' \
      -d '{"token":"token-invalido-para-alerta"}' \
      "http://127.0.0.1:18087/api/v1/gate/validate" || true
  done
  wait_alert CircleguardGateDenialSpike
  capture 03-gate-denials
}

case "$mode" in
  service-down) service_down ;;
  high-errors) high_errors ;;
  gate-denials) gate_denials ;;
  all)
    service_down
    high_errors
    gate_denials
    ;;
  *)
    echo "Uso: $0 [service-down|high-errors|gate-denials|all]" >&2
    exit 2
    ;;
esac

echo "Evidencia guardada en $out"

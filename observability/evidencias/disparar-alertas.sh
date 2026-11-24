#!/usr/bin/env bash
# HU-24 · Dispara deliberadamente tres alertas criticas y guarda evidencia.
#
#   ENV_NAME=dev bash observability/evidencias/disparar-alertas.sh [service-down|high-errors|gate-denials|all]
#
# Requiere kubectl y curl contra el cluster del ambiente (KUBECONFIG propio) con
# CircleGuard y kube-prometheus-stack (observability/instalar.sh).
#
# Garantias:
# - Se niega a ejecutarse en prod: tumbar auth o PostgreSQL de prod no es una prueba.
# - Antes de tocar un recurso guarda sus replicas ORIGINALES y el trap de salida
#   las restaura siempre (tambien si una alerta no aparece o el script falla), y
#   espera a que el rollout vuelva a estar listo.
# - Mantiene la carga hasta ver la alerta FIRING (con limite de tiempo) y despues
#   registra su RESOLVED. La evidencia es local (Alertmanager); Discord es aparte.
set -euo pipefail

mode="${1:-all}"
env_name="${ENV_NAME:-dev}"
case "$env_name" in
  dev|stage) ;;
  prod) echo 'Este script no se ejecuta en prod.' >&2; exit 2 ;;
  *) echo "ENV_NAME invalido: $env_name" >&2; exit 2 ;;
esac
ns="circleguard-${env_name}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="${EVIDENCE_DIR:-observability/evidencias/${env_name}-${stamp}}"
fire_timeout="${FIRE_TIMEOUT_SECONDS:-600}"
resolve_timeout="${RESOLVE_TIMEOUT_SECONDS:-600}"
mkdir -p "$out"

command -v kubectl >/dev/null || { echo 'Falta kubectl.' >&2; exit 1; }
command -v curl >/dev/null || { echo 'Falta curl.' >&2; exit 1; }
kubectl get namespace "$ns" >/dev/null

log() { printf '%s %s\n' "$(date -u +%H:%M:%SZ)" "$*" | tee -a "$out/registro.txt"; }

pids=()
restore=()   # entradas "tipo/nombre=replicas" en orden de modificacion

stop_load() {
  for pid in "${pids[@]:-}"; do
    [[ -n "$pid" ]] && kill "$pid" >/dev/null 2>&1 || true
  done
  pids=()
}

cleanup() {
  local status=$?
  stop_load
  local entry resource replicas
  for entry in "${restore[@]:-}"; do
    [[ -n "$entry" ]] || continue
    resource=${entry%=*}
    replicas=${entry##*=}
    log "Restaurando $resource a $replicas replicas"
    kubectl -n "$ns" scale "$resource" --replicas="$replicas" >/dev/null 2>&1 || status=1
  done
  for entry in "${restore[@]:-}"; do
    [[ -n "$entry" ]] || continue
    resource=${entry%=*}
    kubectl -n "$ns" rollout status "$resource" --timeout=300s >/dev/null 2>&1 \
      || { log "AVISO: $resource no quedo listo tras la restauracion"; status=1; }
  done
  kubectl -n "$ns" get deployments,statefulsets -o wide > "$out/estado-final.txt" 2>&1 || true
  for pid in "${forward_pids[@]:-}"; do
    [[ -n "$pid" ]] && kill "$pid" >/dev/null 2>&1 || true
  done
  exit "$status"
}
forward_pids=()
trap cleanup EXIT

# Guarda las replicas originales una sola vez y luego escala.
scale_to() {
  local resource=$1 replicas=$2 current
  current=$(kubectl -n "$ns" get "$resource" -o jsonpath='{.spec.replicas}')
  if ! printf '%s\n' "${restore[@]:-}" | grep -q "^${resource}="; then
    restore+=("${resource}=${current}")
    log "Replicas originales de $resource: $current"
  fi
  kubectl -n "$ns" scale "$resource" --replicas="$replicas" >/dev/null
  log "$resource escalado a $replicas"
}

restore_now() {
  local resource=$1 entry
  for entry in "${restore[@]:-}"; do
    if [[ ${entry%=*} == "$resource" ]]; then
      kubectl -n "$ns" scale "$resource" --replicas="${entry##*=}" >/dev/null
      kubectl -n "$ns" rollout status "$resource" --timeout=300s >/dev/null
      log "$resource restaurado a ${entry##*=} replicas y listo"
    fi
  done
}

start_forward() {
  local target=$1 local_port=$2 remote_port=$3 namespace=${4:-$ns}
  kubectl -n "$namespace" port-forward "$target" "${local_port}:${remote_port}" >/dev/null 2>&1 &
  forward_pids+=("$!")
  for _ in $(seq 1 30); do
    if curl -sS --max-time 2 -o /dev/null "http://127.0.0.1:${local_port}/" 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "No se pudo abrir port-forward para $target." >&2
  return 1
}

alertmanager_url="${ALERTMANAGER_URL:-}"
if [[ -z "$alertmanager_url" ]]; then
  # alertmanager-operated lo crea el operador para cualquier release del chart.
  start_forward svc/alertmanager-operated 19093 9093 monitoring
  alertmanager_url="http://127.0.0.1:19093"
fi

alerts_for() {
  curl -fsS -G --max-time 10 --data-urlencode "filter=alertname=\"$1\"" \
    --data-urlencode 'active=true' --data-urlencode 'silenced=false' \
    "$alertmanager_url/api/v2/alerts"
}

alert_active() {
  local body
  body=$(alerts_for "$1" 2>/dev/null) || return 1
  [[ -n "$body" && "$body" != "[]" ]]
}

capture() {
  # Dos declaraciones: bash expande todos los argumentos de `local` antes de asignar,
  # asi que "$name" aun no existe en la misma linea y set -u aborta el script.
  local name=$1
  local dir="$out/$name"
  mkdir -p "$dir"
  date -u +%Y-%m-%dT%H:%M:%SZ > "$dir/hora-utc.txt"
  kubectl -n "$ns" get pods -o wide > "$dir/pods.txt" 2>&1 || true
  curl -fsS --max-time 10 "$alertmanager_url/api/v2/alerts" > "$dir/alertmanager.json" 2>&1 || true
}

wait_firing() {
  local name=$1 deadline=$((SECONDS + fire_timeout))
  while (( SECONDS < deadline )); do
    if alert_active "$name"; then
      log "FIRING: $name visible en Alertmanager"
      return 0
    fi
    sleep 15
  done
  log "ERROR: $name no aparecio en ${fire_timeout}s"
  return 1
}

wait_resolved() {
  local name=$1 deadline=$((SECONDS + resolve_timeout))
  while (( SECONDS < deadline )); do
    if ! alert_active "$name"; then
      log "RESOLVED: $name ya no esta activa"
      return 0
    fi
    sleep 15
  done
  log "ERROR: $name sigue activa tras ${resolve_timeout}s"
  return 1
}

# Carga sostenida en segundo plano hasta que se detenga explicitamente.
load_loop() {
  local workers=$1; shift
  for _ in $(seq 1 "$workers"); do
    ( while true; do curl -sS --max-time 3 -o /dev/null "$@" || true; done ) &
    pids+=("$!")
  done
}

service_down() {
  log '>>> CircleguardServiceDown: auth-service sin replicas disponibles'
  capture 01-service-down-antes
  scale_to deployment/auth-service 0
  wait_firing CircleguardServiceDown
  capture 01-service-down-firing
  restore_now deployment/auth-service
  wait_resolved CircleguardServiceDown
  capture 01-service-down-resolved
}

high_errors() {
  log '>>> CircleguardHigh5xxRate: PostgreSQL detenido y carga sobre form-service'
  capture 02-high-5xx-antes
  start_forward svc/form-service 18086 8086
  scale_to statefulset/postgres 0
  load_loop 4 "http://127.0.0.1:18086/api/v1/questionnaires/active"
  wait_firing CircleguardHigh5xxRate
  capture 02-high-5xx-firing
  stop_load
  restore_now statefulset/postgres
  wait_resolved CircleguardHigh5xxRate
  capture 02-high-5xx-resolved
}

gate_denials() {
  log '>>> CircleguardGateDenialSpike: tokens invalidos al gateway (>1 RED/s sostenido)'
  capture 03-gate-denials-antes
  start_forward svc/gateway-service 18087 8087
  load_loop 4 -X POST -H 'Content-Type: application/json' \
    -d '{"token":"token-invalido-para-alerta"}' \
    "http://127.0.0.1:18087/api/v1/gate/validate"
  wait_firing CircleguardGateDenialSpike
  capture 03-gate-denials-firing
  stop_load
  wait_resolved CircleguardGateDenialSpike
  capture 03-gate-denials-resolved
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

log "Evidencia guardada en $out"

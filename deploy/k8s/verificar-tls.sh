#!/usr/bin/env bash
# HU-21 · Verifica con curl que un Ingress responde por HTTPS y muestra el certificado.
#
#   bash deploy/k8s/verificar-tls.sh dev.circleguard.local
#
# Valida confianza y nombre del certificado y acepta 2xx/3xx/401/403.
# Solo para certificados locales, CIRCLE_TLS_INSECURE=1 omite la confianza.
set -euo pipefail

host="${1:?Uso: verificar-tls.sh <host-del-ingress>}"

for path in / /api/v1/health; do
  tls_args=()
  [[ ${CIRCLE_TLS_INSECURE:-0} != 1 ]] || tls_args=(-k)
  code=$(curl -sS "${tls_args[@]}" --max-time 15 -o /dev/null -w '%{http_code}' "https://${host}${path}" || true)
  echo "TLS ${path} -> ${code}"
  case "$code" in
    2??|3??|401|403) ;;
    *)
      echo "Fallo TLS/HTTPS en ${path} (${code})." >&2
      exit 1
      ;;
  esac
done

if command -v openssl >/dev/null 2>&1; then
  echo "--- certificado servido por ${host}:443 ---"
  echo | openssl s_client -servername "$host" -connect "${host}:443" 2>/dev/null \
    | openssl x509 -noout -subject -issuer -dates
else
  echo "AVISO: openssl no esta instalado; no se pudo imprimir el certificado." >&2
fi

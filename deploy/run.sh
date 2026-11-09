#!/usr/bin/env bash
# =============================================================================
# HU-10 · Despliegue de una etiqueta ya publicada en un ambiente CircleGuard.
#
# Se ejecuta EN la VM del ambiente, no en Jenkins. Jenkins lo invoca por SSH
# manteniendo un tunel inverso 127.0.0.1:5000 -> registro de Nexus:
#
#   printf '%s\n' "$NX_PASS" | ssh -R 5000:nexus:5000 ubuntu@host \
#     'bash /opt/circleguard/run.sh vX.Y.Z'
#
# Requisitos en la VM: Docker con el plugin compose, el archivo /opt/circleguard/.env
# y los archivos de deploy/ copiados en /opt/circleguard/. El usuario ubuntu no
# esta en el grupo docker; por eso se usa sudo -n. La contrasena de Nexus solo
# se lee por stdin y nunca se imprime.
# =============================================================================
set -euo pipefail

if [[ $# != 1 || ! $1 =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Uso: $0 vX.Y.Z  (la contrasena de Nexus se lee por stdin)" >&2
  exit 2
fi

image_tag=$1
deploy_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$deploy_dir"

if [[ ! -s .env ]]; then
  echo "Falta $deploy_dir/.env; sube la credencial circleguard-env-<ambiente> primero." >&2
  exit 1
fi

read -r nexus_password
if [[ -z $nexus_password ]]; then
  echo 'No se recibio la contrasena de Nexus por stdin.' >&2
  exit 1
fi
printf '%s' "$nexus_password" | sudo -n docker login 127.0.0.1:5000 \
  -u "${NEXUS_USER:-admin}" --password-stdin >/dev/null
unset nexus_password

# IMAGE_TAG del shell gana sobre el valor de respaldo del .env: se promueve la
# MISMA etiqueta inmutable en dev, stage y prod.
sudo -n env IMAGE_TAG="$image_tag" docker compose pull
sudo -n env IMAGE_TAG="$image_tag" docker compose up -d --wait --remove-orphans

echo "--- docker compose ps (${image_tag}) ---"
sudo -n env IMAGE_TAG="$image_tag" docker compose ps

# Humo local contra el proxy. Acepta respuestas vivas aunque la ruta no exista.
site=$(grep -E '^SITE_ADDRESS=' .env | head -n 1 | cut -d= -f2- | cut -d, -f1 | xargs)
if [[ -n $site ]]; then
  for path in / /api/v1/health; do
    code=$(curl -ksS --max-time 15 --resolve "${site}:443:127.0.0.1" \
      -o /dev/null -w '%{http_code}' "https://${site}${path}" || true)
    echo "humo ${path} -> ${code}"
    case "$code" in
      2??|3??|401|403|404|405) ;;
      *)
        echo "Prueba de humo fallida en ${path} (${code})." >&2
        exit 1
        ;;
    esac
  done
else
  echo 'AVISO: .env no define SITE_ADDRESS; se omite el humo del proxy.' >&2
fi

echo "Despliegue ${image_tag} completado."

#!/usr/bin/env bash
set -euo pipefail
tag=${1:?Uso: preflight-images.sh vX.Y.Z}
registry=${REGISTRY:?Define el registro publico de CircleGuard}
bash scripts/package-images.sh "$tag"
for service in auth identity promotion form gateway notification file dashboard; do
  name="circleguard-$service-service"
  bash scripts/scan-image.sh "$registry/circleguard/$name:$tag" "$name"
done
bash scripts/scan-image.sh "$registry/circleguard/web:$tag" web

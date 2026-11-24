#!/usr/bin/env bash
# Snapshots de handover: no ocupa ninguna etiqueta oficial vX.Y.Z de Nexus.
set -euo pipefail
set +x
registry=${REGISTRY:?Define el registro HTTPS de Nexus}
: "${NX_USER:?Falta la credencial Nexus}"
: "${NX_PASS:?Falta la credencial Nexus}"
git diff --quiet
git diff --cached --quiet
if [[ -n $(git ls-files --others --exclude-standard) ]]; then
  echo 'El snapshot requiere un checkout limpio.' >&2
  exit 1
fi
revision=$(git rev-parse HEAD)
tag="handover-${revision:0:12}"
images=()
for entry in auth:8180 identity:8083 promotion:8088 form:8086 gateway:8087 notification:8082 file:8085 dashboard:8084; do
  service="circleguard-${entry%:*}-service"
  image="$registry/circleguard/$service:$tag"
  docker build --build-arg "SERVICE=$service" --build-arg "SERVICE_PORT=${entry#*:}" \
    --label "org.opencontainers.image.revision=$revision" \
    --label "org.opencontainers.image.version=$tag" -t "$image" .
  images+=("$image")
done
web="$registry/circleguard/web:$tag"
docker build --label "org.opencontainers.image.revision=$revision" \
  --label "org.opencontainers.image.version=$tag" -t "$web" mobile
images+=("$web")
# Ningun push ocurre hasta aprobar las nueve imagenes.
for image in "${images[@]}"; do
  name=${image%:*}
  bash scripts/scan-image.sh "$image" "${name##*/}"
done
# Verifica que la actualizacion de NGINX mantiene el arranque sin root y la SPA.
probe=$(docker run -d "$web")
trap 'docker rm -f "$probe" >/dev/null 2>&1 || true' EXIT
for attempt in {1..45}; do
  if docker exec "$probe" wget -q -O /dev/null http://127.0.0.1:8080/admin/console; then break; fi
  if [[ $attempt == 45 ]]; then docker logs "$probe"; exit 1; fi
  sleep 2
done
docker rm -f "$probe" >/dev/null
trap - EXIT
printf '%s' "$NX_PASS" | docker login "$registry" -u "$NX_USER" --password-stdin
trap 'docker logout "$registry" >/dev/null 2>&1 || true' EXIT
for image in "${images[@]}"; do docker push "$image"; done
printf '%s\n' "${images[@]}" > security-reports/published-candidate-images.txt
printf '%s\n' "$revision" > security-reports/candidate-revision.txt

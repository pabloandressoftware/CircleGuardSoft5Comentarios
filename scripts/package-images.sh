#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || ! $1 =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ || ${2:-} != "" && ${2:-} != --push ]]; then
  echo "Uso: $0 vX.Y.Z [--push]" >&2
  exit 2
fi

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
version=$1
registry=${REGISTRY:-127.0.0.1:5000}
revision=$(git rev-parse HEAD)
if [[ -n $(git status --porcelain) ]]; then
  revision="$revision-dirty"
fi
services=(
  auth:8180
  notification:8082
  identity:8083
  dashboard:8084
  file:8085
  form:8086
  gateway:8087
  promotion:8088
)
labels=(--label "org.opencontainers.image.version=$version" --label "org.opencontainers.image.revision=$revision")
images=()
for entry in "${services[@]}"; do
  service="circleguard-${entry%:*}-service"
  image="$registry/circleguard/$service:$version"
  docker build --build-arg "SERVICE=$service" --build-arg "SERVICE_PORT=${entry#*:}" "${labels[@]}" -t "$image" .
  images+=("$image")
done

image="$registry/circleguard/web:$version"
docker build "${labels[@]}" -t "$image" mobile
images+=("$image")

if [[ ${2:-} == --push ]]; then
  for image in "${images[@]}"; do
    docker push "$image"
  done
fi
printf '%s\n' "${images[@]}"

#!/usr/bin/env bash
# Conserva el resultado original y la evaluacion VEX vinculada al digest.
set -euo pipefail
image=${1:?Indica la imagen}
name=${2:?Indica el nombre del reporte}
[[ $name =~ ^[a-z0-9-]+$ ]] || exit 2
reports="$PWD/security-reports"
mkdir -p "$reports"
trivy=(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock
  -v trivy-cache:/root/.cache/ -v "$reports:/reports" aquasec/trivy:0.58.1)
"${trivy[@]}" image --quiet --scanners vuln --severity "${TRIVY_SEVERITY:-CRITICAL}" \
  --ignore-unfixed --format json --output "/reports/trivy-$name-original.json" "$image"
"${trivy[@]}" image --quiet --format cyclonedx --output "/reports/$name.cdx.json" "$image"
node scripts/spring-rendering-vex.mjs "$reports/$name.cdx.json" "$reports/$name.openvex.json"
vex=()
if [[ -s "$reports/$name.openvex.json" ]]; then vex=(--vex "/reports/$name.openvex.json"); fi
"${trivy[@]}" image --scanners vuln --severity "${TRIVY_SEVERITY:-CRITICAL}" --ignore-unfixed \
  "${vex[@]}" --show-suppressed --exit-code 1 --format table "$image" \
  | tee "$reports/trivy-$name.txt"

#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 2 || ( $1 != --local && $1 != --aws ) || ! -s $2 ]]; then
  echo "Uso: $0 <--local|--aws> <archivo-env-de-CI>" >&2
  exit 2
fi

mode=$1
env_file=$2
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)

if [[ $mode == --local ]]; then
  # Evita un nuevo ID de imagen por atestaciones de cada ejecución.
  export BUILDX_NO_DEFAULT_ATTESTATIONS=1
  if [[ -S /var/run/docker.sock ]]; then
    export CI_DOCKER_GID="${CI_DOCKER_GID:-$(stat -c '%g' /var/run/docker.sock)}"
  fi
  docker_cli=(docker)
  if [[ ${CI_DOCKER_USE_SUDO:-0} == 1 ]]; then
    docker_cli=(sudo --preserve-env=CI_DOCKER_GID,BUILDX_NO_DEFAULT_ATTESTATIONS docker)
  fi
  compose=("${docker_cli[@]}" compose -p "${CI_COMPOSE_PROJECT:-circleguard-ci}" --env-file "$env_file" -f "$root/infra/ci/compose.yml")
  "${compose[@]}" up -d --build jenkins sonarqube nexus
  init_status=0
  "${compose[@]}" run --rm --no-deps --build init || init_status=$?
  "${compose[@]}" exec -T jenkins docker version --format '{{.Server.Version}}'
  "${compose[@]}" ps
  exit "$init_status"
fi

export AWS_PAGER=''
export WEB_CIDR="${WEB_CIDR:-${SSH_CIDR:?Define el CIDR IPv4 autorizado para SSH}}"
bash "$root/infra/bootstrap/bootstrap-env.sh" ci
host=$(aws cloudformation describe-stacks --region "$AWS_REGION" --stack-name circleguard-ci \
  --query "Stacks[0].Outputs[?OutputKey=='PublicDns'].OutputValue | [0]" --output text)
[[ $host =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ && $host != None ]] || { echo 'AWS no devolvió un host EC2 válido.' >&2; exit 1; }
target="ubuntu@$host"
ssh_options=(-o BatchMode=yes -o ConnectTimeout=5 -o StrictHostKeyChecking=accept-new)

for attempt in {1..40}; do
  if ssh "${ssh_options[@]}" "$target" true 2>/dev/null; then break; fi
  if [[ $attempt == 40 ]]; then
    echo 'SSH no está disponible. Revisa la llave y el CIDR.' >&2
    exit 1
  fi
  sleep 5
done
ssh "${ssh_options[@]}" "$target" '
  set -eu
  sudo cloud-init status --wait
  if [ "$(sysctl -n vm.max_map_count)" -lt 524288 ]; then
    echo vm.max_map_count=524288 | sudo tee /etc/sysctl.d/99-circleguard-sonar.conf >/dev/null
    sudo sysctl -p /etc/sysctl.d/99-circleguard-sonar.conf
  fi
'
bash "$root/infra/bootstrap/install-env.sh" "$target" "$env_file"
tar -C "$root" -cf - \
  infra/bootstrap/bootstrap-ci.sh infra/ci/compose.yml \
  infra/ci/jenkins/Dockerfile infra/ci/jenkins/casc.yml infra/ci/jenkins/plugins.txt \
  infra/ci/init/Dockerfile infra/ci/init/configure.sh |
  ssh "${ssh_options[@]}" "$target" 'tar -xf - -C /opt/circleguard'
ssh "${ssh_options[@]}" "$target" \
  'CI_DOCKER_USE_SUDO=1 bash /opt/circleguard/infra/bootstrap/bootstrap-ci.sh --local /opt/circleguard/.env'

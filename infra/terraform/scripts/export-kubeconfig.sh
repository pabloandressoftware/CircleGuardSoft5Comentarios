#!/usr/bin/env bash
set -euo pipefail
environment="${1:?Uso: export-kubeconfig.sh <dev|stage|prod> <llave.pem> <archivo-salida>}"
key="${2:?Falta la llave SSH}"
destination="${3:?Falta archivo de salida fuera del repositorio}"
[[ "$environment" =~ ^(dev|stage|prod)$ ]] || exit 1
repo=$(cd "$(dirname "$0")/../../.." && pwd)
ip=$(terraform -chdir="$repo/infra/terraform/envs/$environment" output -raw public_ip)
umask 077
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
# SSH verifica el host; confirmar su huella por un canal de confianza/SSM.
ssh -i "$key" "ubuntu@$ip" 'sudo cloud-init status --wait >/dev/null && sudo cat /etc/rancher/k3s/k3s.yaml' > "$tmp"
sed "s/127\.0\.0\.1/$ip/g" "$tmp" > "$destination"
chmod 600 "$destination"
KUBECONFIG="$destination" kubectl get nodes
echo "Subir $destination a Jenkins como Secret file kubeconfig-$environment; nunca a Git."

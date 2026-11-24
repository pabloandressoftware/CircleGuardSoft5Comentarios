#!/usr/bin/env bash
# Recupera un servidor cuyo EBS conserva PVC ligados al nombre anterior.
# Uso en la VM con sudo: repair-node-identity.sh <nombre-original-del-nodo>
set -euo pipefail
test "$(id -u)" = 0
desired=${1:?Indica el nombre que conservan los PV locales}
[[ $desired =~ ^[a-z0-9][a-z0-9.-]*$ ]] || exit 2
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
current=$(k3s kubectl get nodes --field-selector=metadata.name!="$desired" -o jsonpath='{.items[0].metadata.name}')
mkdir -p /var/lib/rancher/circleguard-node
printf '%s' "$desired" > /var/lib/rancher/circleguard-node/name
if [[ ! -s /var/lib/rancher/circleguard-node/password ]]; then
  install -m 600 /etc/rancher/node/password /var/lib/rancher/circleguard-node/password
fi
chmod 600 /var/lib/rancher/circleguard-node/{name,password}
ln -sf /var/lib/rancher/circleguard-node/password /etc/rancher/node/password
k3s kubectl delete node "$desired" --ignore-not-found
k3s kubectl -n kube-system delete secret "$desired.node-password.k3s" --ignore-not-found
sed -i '/^node-name:/d' /etc/rancher/k3s/config.yaml
printf '\nnode-name: "%s"\n' "$desired" >> /etc/rancher/k3s/config.yaml
# Limpiar el bridge/CIDR de la identidad temporal antes de volver al original.
/usr/local/bin/k3s-killall.sh
systemctl start k3s
for attempt in {1..60}; do
  if k3s kubectl --request-timeout=3s get nodes >/dev/null 2>&1; then break; fi
  sleep 2
done
k3s kubectl wait --for=condition=Ready "node/$desired" --timeout=120s
if [[ -n $current ]]; then k3s kubectl delete node "$current" --ignore-not-found; fi
k3s kubectl get nodes -o wide

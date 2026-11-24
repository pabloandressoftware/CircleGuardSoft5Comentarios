#!/usr/bin/env bash
# Preparación adicional para CI en EC2. Ejecutar con sudo antes de Compose.
set -euo pipefail
test "$(id -u)" = 0
mkdir -p /var/jenkins_home
chown 1000:1000 /var/jenkins_home
chmod 750 /var/jenkins_home
# La ruta es idéntica dentro/fuera de Jenkins para los bind mounts de Trivy,
# gitleaks y Testcontainers cuando usan el socket Docker del host.
if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
  if [[ ! -f /swapfile ]]; then
    fallocate -l 4G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
  fi
  swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
echo 'vm.swappiness=10' > /etc/sysctl.d/98-circleguard-ci-memory.conf
sysctl -p /etc/sysctl.d/98-circleguard-ci-memory.conf

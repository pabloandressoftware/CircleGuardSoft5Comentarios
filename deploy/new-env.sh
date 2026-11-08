#!/usr/bin/env bash
# =============================================================================
# HU-07 · Genera el archivo de variables de un ambiente con secretos aleatorios.
#
#   bash deploy/new-env.sh dev  ec2-3-15-0-1.us-east-2.compute.amazonaws.com > /ruta/segura/circleguard-env-dev
#   bash deploy/new-env.sh prod ec2-...compute.amazonaws.com [IP_PUBLICA]       > /ruta/segura/circleguard-env-prod
#
# El resultado se sube a Jenkins como "Secret file" con el ID circleguard-env-<ambiente>
# (ver docs/sprint-1-persona-a.md). NUNCA se versiona en Git.
# Cada ambiente tiene secretos distintos: un secreto filtrado de dev no abre prod.
# =============================================================================
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 || ! $1 =~ ^(dev|stage|prod)$ ]]; then
  echo "Uso: $0 <dev|stage|prod> <dns-publico-de-la-vm> [ip-publica]" >&2
  exit 2
fi
environment=$1
host=$2
ip=${3:-}
command -v openssl >/dev/null || { echo "Falta openssl" >&2; exit 1; }

rand() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$1"; }
hex() { openssl rand -hex "$1"; }

if [[ $environment == dev ]]; then
  compose_file=compose.yml:compose.dev.yml
else
  compose_file=compose.yml:compose.prod.yml
fi
site=$host
[[ -n $ip ]] && site="$host, $ip"

cat <<EOF
# CircleGuard · variables del ambiente ${environment} · generado $(date -u +%Y-%m-%dT%H:%MZ)
# Secret file de Jenkins: circleguard-env-${environment}. No subir a Git.

# --- Compose ---------------------------------------------------------------
COMPOSE_PROJECT_NAME=circleguard-${environment}
COMPOSE_FILE=${compose_file}
REGISTRY=127.0.0.1:5000
# Jenkins la reemplaza en cada despliegue con IMAGE_TAG=vX.Y.Z
IMAGE_TAG=v0.0.1

# --- Entrada publica (proxy HTTPS) ----------------------------------------
SITE_ADDRESS=${site}
CORS_ALLOWED_ORIGINS=https://${host}

# --- PostgreSQL (una instancia, una base por servicio) ---------------------
DB_USERNAME=circleguard
DB_PASSWORD=$(rand 32)
JPA_DDL_AUTO=update

# --- Tokens (auth, gateway, identity, promotion, notification) -------------
# JWT_SECRET y QR_SECRET: minimo 32 caracteres (HMAC-SHA256)
JWT_SECRET=$(rand 48)
JWT_EXPIRATION=3600000
# QR_SECRET debe ser el mismo en auth y gateway (ya lo es: sale de este archivo)
QR_SECRET=$(rand 48)
# Milisegundos: 60000 = 60 s
QR_EXPIRATION=60000

# --- Boveda de identidad (identity) ----------------------------------------
# NO cambiar despues del primer despliegue: los datos cifrados dejarian de leerse
VAULT_SECRET=$(hex 32)
VAULT_SALT=$(hex 8)
VAULT_HASH_SALT=$(hex 8)

# --- Middleware --------------------------------------------------------------
NEO4J_PASSWORD=$(rand 24)
REDIS_PASSWORD=$(rand 32)
LDAP_PASSWORD=$(rand 24)

# --- Opcionales (notification) ---------------------------------------------
# SMTP_HOST=
# SMTP_PORT=587
# SMTP_USERNAME=
# SMTP_PASSWORD=
# SMTP_AUTH=true
# SMTP_STARTTLS=true
EOF

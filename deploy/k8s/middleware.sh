#!/usr/bin/env bash
# HU-18 · Middleware con charts de Helm existentes: PostgreSQL, Redis, Kafka y Neo4j.
# OpenLDAP no tiene chart oficial estable: se aplica deploy/k8s/openldap.yaml.
#
#   bash deploy/k8s/middleware.sh dev
#
# Requiere el Secret del ambiente (crear-secretos.sh). Los nombres de los releases son los
# mismos hosts que usa Docker Compose (postgres, redis, kafka, neo4j, openldap), asi las
# variables de los servicios no cambian entre Compose y Kubernetes.
set -euo pipefail

ambiente="${1:?Uso: middleware.sh <dev|stage|prod>}"
ns="circleguard-${ambiente}"
aqui="$(cd "$(dirname "$0")" && pwd)"

secreto() { kubectl -n "$ns" get secret circleguard-secrets -o "jsonpath={.data.$1}" | base64 -d; }

helm repo add neo4j https://helm.neo4j.com/neo4j >/dev/null
helm repo update >/dev/null

kubectl -n "$ns" create configmap postgres-init --from-file=init-db.sql="$aqui/../postgres/init-db.sql" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

helm upgrade --install postgres oci://registry-1.docker.io/bitnamicharts/postgresql -n "$ns" --wait --timeout 5m \
  --set fullnameOverride=postgres \
  --set auth.username="$(secreto DB_USERNAME)" --set auth.database=circleguard \
  --set auth.existingSecret=circleguard-secrets \
  --set auth.secretKeys.adminPasswordKey=DB_PASSWORD --set auth.secretKeys.userPasswordKey=DB_PASSWORD \
  --set primary.initdb.scriptsConfigMap=postgres-init \
  --set primary.resources.requests.memory=128Mi \
  --set primary.livenessProbe.initialDelaySeconds=120 --set primary.readinessProbe.initialDelaySeconds=60 \
  --set primary.startupProbe.enabled=true --set primary.startupProbe.failureThreshold=60

helm upgrade --install redis oci://registry-1.docker.io/bitnamicharts/redis -n "$ns" --wait --timeout 5m \
  --set fullnameOverride=redis --set architecture=standalone \
  --set auth.existingSecret=circleguard-secrets --set auth.existingSecretPasswordKey=REDIS_PASSWORD

helm upgrade --install kafka oci://registry-1.docker.io/bitnamicharts/kafka -n "$ns" --wait --timeout 8m \
  --set fullnameOverride=kafka --set controller.replicaCount=1 \
  --set listeners.client.protocol=PLAINTEXT --set listeners.interbroker.protocol=PLAINTEXT \
  --set controller.resourcesPreset=small

helm upgrade --install neo4j neo4j/neo4j -n "$ns" --wait --timeout 8m \
  --set neo4j.name=neo4j --set neo4j.edition=community --set neo4j.acceptLicenseAgreement=yes \
  --set neo4j.password="$(secreto NEO4J_PASSWORD)" --set volumes.data.mode=defaultStorageClass \
  --set neo4j.resources.requests.memory=1Gi --set neo4j.resources.limits.memory=1Gi

kubectl -n "$ns" apply -f "$aqui/openldap.yaml"
echo "Middleware listo en $ns"

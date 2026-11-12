#!/usr/bin/env bash
# =============================================================================
# HU-09 · Conecta SonarQube y Jenkins del servidor de CI para que el quality gate
# detenga el pipeline (waitForQualityGate abortPipeline: true).
#
# Hace tres cosas y se puede repetir sin duplicar nada:
#   1. Webhook "jenkins" en SonarQube: avisa a Jenkins el resultado del gate.
#      Sin el, waitForQualityGate espera hasta su timeout en vez de cortar.
#   2. Credencial "sonar-token" en Jenkins con un token de analisis de SonarQube.
#   3. Servidor "sonarqube" en Jenkins, el que usa withSonarQubeEnv('sonarqube').
#
# Uso, en la VM de CI con Jenkins y SonarQube ya levantados:
#   bash scripts/conectar-sonar-jenkins.sh /ruta/segura/ci.env
#
# Del archivo lee CI_JENKINS_USER, CI_JENKINS_PASSWORD, CI_SONAR_PASSWORD y, si
# estan, CI_JENKINS_PORT (8181) y CI_SONAR_PORT (9000). Necesita curl y jq.
# =============================================================================
set -euo pipefail

env_file=${1:?Uso: bash scripts/conectar-sonar-jenkins.sh /ruta/segura/ci.env}
[[ -r $env_file ]] || { echo "No se puede leer $env_file" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$env_file"
set +a
: "${CI_JENKINS_USER:?Falta CI_JENKINS_USER en $env_file}"
: "${CI_JENKINS_PASSWORD:?Falta CI_JENKINS_PASSWORD en $env_file}"
: "${CI_SONAR_PASSWORD:?Falta CI_SONAR_PASSWORD en $env_file}"
for cmd in curl jq; do
  command -v "$cmd" >/dev/null || { echo "Falta el comando $cmd" >&2; exit 1; }
done

# Direcciones desde la VM (puertos publicados en loopback)
jenkins=${JENKINS_URL:-http://127.0.0.1:${CI_JENKINS_PORT:-8181}}
sonar=${SONAR_URL:-http://127.0.0.1:${CI_SONAR_PORT:-9000}}
# Direcciones con las que los contenedores se ven entre si en la red del CI
jenkins_internal=${JENKINS_INTERNAL_URL:-http://jenkins:8080}
sonar_internal=${SONAR_INTERNAL_URL:-http://sonarqube:9000}

cookies=$(mktemp)
trap 'rm -f "$cookies"' EXIT
request() { curl --silent --show-error --fail --max-time 20 "$@"; }
sonar_api() { request -u "admin:$CI_SONAR_PASSWORD" "$@"; }
jenkins_api() { request -b "$cookies" -c "$cookies" -u "$CI_JENKINS_USER:$CI_JENKINS_PASSWORD" "$@"; }

request "$sonar/api/system/status" | jq -e '.status == "UP"' >/dev/null ||
  { echo "SonarQube no responde UP en $sonar" >&2; exit 1; }
jenkins_api "$jenkins/api/json" >/dev/null ||
  { echo "Jenkins no responde en $jenkins o rechazo el usuario" >&2; exit 1; }
crumb=$(jenkins_api "$jenkins/crumbIssuer/api/json" | jq -r '.crumbRequestField + ":" + .crumb')

# --- 1. Webhook de SonarQube hacia Jenkins -------------------------------------
if sonar_api "$sonar/api/webhooks/list" | jq -e '.webhooks[] | select(.name == "jenkins")' >/dev/null; then
  echo "OK  webhook jenkins ya existia"
else
  sonar_api -X POST "$sonar/api/webhooks/create" --data name=jenkins \
    --data-urlencode "url=$jenkins_internal/sonarqube-webhook/" >/dev/null
  echo "OK  webhook jenkins creado -> $jenkins_internal/sonarqube-webhook/"
fi

# --- 2. Credencial sonar-token en Jenkins --------------------------------------
# Solo se genera si Jenkins aun no la tiene. El token nunca se imprime.
credential_status=$(curl -s -o /dev/null -w '%{http_code}' -b "$cookies" \
  -u "$CI_JENKINS_USER:$CI_JENKINS_PASSWORD" \
  "$jenkins/credentials/store/system/domain/_/credential/sonar-token/api/json")
if [[ $credential_status == 200 ]]; then
  echo "OK  credencial sonar-token ya existia"
else
  sonar_api -X POST "$sonar/api/user_tokens/revoke" --data name=jenkins-ci >/dev/null 2>&1 || true
  token=$(sonar_api -X POST "$sonar/api/user_tokens/generate" \
    --data name=jenkins-ci --data type=GLOBAL_ANALYSIS_TOKEN | jq -r '.token')
  payload=$(jq -cn --arg secret "$token" '{"": "0", credentials: {scope: "GLOBAL", id: "sonar-token",
    secret: $secret, description: "Token de analisis de SonarQube (HU-09)",
    "$class": "org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl"}}')
  jenkins_api -H "$crumb" -X POST "$jenkins/credentials/store/system/domain/_/createCredentials" \
    --data-urlencode "json=$payload" >/dev/null
  unset token payload
  echo "OK  credencial sonar-token creada"
fi

# --- 3. Servidor "sonarqube" en Jenkins ----------------------------------------
# DescribableModel crea la instalacion por nombre de parametro, asi no depende
# del orden del constructor en cada version del plugin de SonarQube.
groovy=$(cat <<GROOVY
import jenkins.model.GlobalConfiguration
import hudson.plugins.sonar.SonarGlobalConfiguration
import hudson.plugins.sonar.SonarInstallation
import org.jenkinsci.plugins.structs.describable.DescribableModel

def config = GlobalConfiguration.all().get(SonarGlobalConfiguration)
def installation = DescribableModel.of(SonarInstallation).instantiate(
    [name: 'sonarqube', serverUrl: '$sonar_internal', credentialsId: 'sonar-token'])
def others = (config.installations ?: []).findAll { it.name != 'sonarqube' }
config.setInstallations((others + [installation]) as SonarInstallation[])
config.setBuildWrapperEnabled(true)
config.save()
println "OK  servidor sonarqube -> \${installation.serverUrl} con la credencial \${installation.credentialsId}"
GROOVY
)
result=$(jenkins_api -H "$crumb" -X POST "$jenkins/scriptText" --data-urlencode "script=$groovy")
if [[ $result == OK* ]]; then
  echo "$result"
else
  echo "Jenkins no acepto la configuracion del servidor sonarqube:" >&2
  echo "$result" >&2
  exit 1
fi

echo "Listo: el quality gate de SonarQube ya puede detener el pipeline."

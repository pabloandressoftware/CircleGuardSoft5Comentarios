#!/bin/sh
set -eu

sonar=http://sonarqube:9000
nexus=http://nexus:8081/service/rest/v1
jenkins=http://jenkins:8080
gate='CircleGuard'

request() {
  curl --silent --show-error --fail --max-time 10 "$@"
}

wait_until() {
  service=$1
  shift
  count=0
  until "$@" >/dev/null 2>&1; do
    count=$((count + 1))
    if [ "$count" -ge 120 ]; then
      printf '%s\n' "$service no quedó listo. Consulta docker compose logs." >&2
      exit 1
    fi
    sleep 5
  done
}

sonar_ready() {
  request "$sonar/api/system/status" | jq -e '.status == "UP"'
}

sonar_api() {
  request -u "admin:$CI_SONAR_PASSWORD" "$@"
}

nexus_api() {
  request -u "admin:$CI_NEXUS_PASSWORD" "$@"
}

jenkins_api() {
  request -u "$CI_JENKINS_USER:$CI_JENKINS_PASSWORD" "$@"
}

cookie_jar=/tmp/jenkins-cookies
jenkins_web() {
  curl --silent --show-error --fail --max-time 10 \
    -c "$cookie_jar" -b "$cookie_jar" \
    -u "$CI_JENKINS_USER:$CI_JENKINS_PASSWORD" "$@"
}

jenkins_status() {
  curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 \
    -c "$cookie_jar" -b "$cookie_jar" \
    -u "$CI_JENKINS_USER:$CI_JENKINS_PASSWORD" "$@"
}

configure_github_credential() {
  [ -n "${CI_GITHUB_TOKEN:-}" ] || return 0

  github_user=${CI_GITHUB_USER:-x-access-token}
  crumb=$(jenkins_web "$jenkins/crumbIssuer/api/json" | jq -r '.crumb')
  payload=$(jq -nc \
    --arg user "$github_user" \
    --arg token "$CI_GITHUB_TOKEN" \
    '{credentials:{scope:"GLOBAL",id:"circleguard-github",username:$user,password:$token,description:"PAT de GitHub para multibranch y Shared Library","$class":"com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"}}')

  status=$(jenkins_status "$jenkins/credentials/store/system/domain/_/credential/circleguard-github/api/json") || true
  if [ "$status" = 200 ]; then
    jenkins_web -H "Jenkins-Crumb: $crumb" -X POST \
      "$jenkins/credentials/store/system/domain/_/credential/circleguard-github/doDelete" >/dev/null
  fi

  jenkins_web -H "Jenkins-Crumb: $crumb" -X POST \
    "$jenkins/credentials/store/system/domain/_/createCredentials" \
    --data-urlencode "json=$payload" >/dev/null
  echo 'Credencial circleguard-github registrada en Jenkins.'
}

wait_until Jenkins request "$jenkins/login"
jenkins_api "$jenkins/api/json" >/dev/null
configure_github_credential
wait_until SonarQube sonar_ready
wait_until Nexus request "$nexus/status"

if ! sonar_api "$sonar/api/authentication/validate" | jq -e '.valid' >/dev/null; then
  : "${CI_SONAR_INITIAL_PASSWORD:?Define la contraseña inicial o actual de SonarQube para el primer arranque}"
  request -u "admin:$CI_SONAR_INITIAL_PASSWORD" -X POST "$sonar/api/users/change_password" \
    --data-urlencode login=admin \
    --data-urlencode "previousPassword=$CI_SONAR_INITIAL_PASSWORD" \
    --data-urlencode "password=$CI_SONAR_PASSWORD" || {
    echo 'No se pudo cambiar la contraseña de SonarQube. Verifica la contraseña inicial y usa una nueva con mayúsculas, minúsculas, números y símbolos.' >&2
    exit 1
  }
fi

gates=$(sonar_api "$sonar/api/qualitygates/list")
if ! printf '%s' "$gates" | jq -e --arg name "$gate" '.qualitygates[] | select(.name == $name)' >/dev/null; then
  sonar_api -X POST "$sonar/api/qualitygates/create" --data-urlencode "name=$gate" >/dev/null
fi
conditions=$(sonar_api -G "$sonar/api/qualitygates/show" --data-urlencode "name=$gate")
condition_id=$(printf '%s' "$conditions" | jq -r '.conditions[] | select(.metric == "new_coverage") | .id')
if [ -z "$condition_id" ]; then
  sonar_api -X POST "$sonar/api/qualitygates/create_condition" \
    --data-urlencode "gateName=$gate" --data metric=new_coverage --data op=LT --data error=60 >/dev/null
elif ! printf '%s' "$conditions" | jq -e '.conditions[] | select(.metric == "new_coverage" and .op == "LT" and (.error | tonumber) == 60)' >/dev/null; then
  sonar_api -X POST "$sonar/api/qualitygates/update_condition" \
    --data-urlencode "id=$condition_id" --data metric=new_coverage --data op=LT --data error=60 >/dev/null
fi
sonar_api -X POST "$sonar/api/qualitygates/set_as_default" --data-urlencode "name=$gate"
sonar_api -X POST "$sonar/api/settings/set" --data key=sonar.qualitygate.ignoreSmallChanges --data value=false

if ! nexus_api "$nexus/security/realms/active" >/dev/null 2>&1; then
  if [ ! -s /nexus-data/admin.password ]; then
    echo 'Nexus rechazó CI_NEXUS_PASSWORD. Revisa la credencial del volumen existente.' >&2
    exit 1
  fi
  initial_nexus_password=$(cat /nexus-data/admin.password)
  request -u "admin:$initial_nexus_password" -X PUT "$nexus/security/users/admin/change-password" \
    -H 'Content-Type: text/plain' --data-binary "$CI_NEXUS_PASSWORD"
fi

realms=$(nexus_api "$nexus/security/realms/active")
if ! printf '%s' "$realms" | jq -e 'index("DockerToken") != null' >/dev/null; then
  realms=$(printf '%s' "$realms" | jq -c '. + ["DockerToken"]')
  nexus_api -X PUT "$nexus/security/realms/active" -H 'Content-Type: application/json' --data-binary "$realms"
fi

repository=$(nexus_api "$nexus/repositories/docker/hosted/circleguard" 2>/dev/null || true)
if [ -z "$repository" ]; then
  nexus_api -X POST "$nexus/repositories/docker/hosted" -H 'Content-Type: application/json' --data-binary '{
    "name": "circleguard",
    "online": true,
    "storage": {"blobStoreName": "default", "strictContentTypeValidation": true, "writePolicy": "ALLOW_ONCE"},
    "docker": {"v1Enabled": false, "forceBasicAuth": true, "httpPort": 5000}
  }'
else
  printf '%s' "$repository" | jq -e '.online and .docker.httpPort == 5000 and .docker.forceBasicAuth and .storage.writePolicy == "ALLOW_ONCE"' >/dev/null
fi

echo 'Jenkins listo, quality gate CircleGuard en 60 % y repositorio Docker de Nexus configurado.'
eula=$(nexus_api "$nexus/system/eula")
if ! printf '%s' "$eula" | jq -e '.accepted == true' >/dev/null; then
  echo 'Registro pendiente: el administrador debe revisar y aceptar la licencia de Community Edition en la consola de Nexus y repetir el bootstrap.' >&2
  exit 3
fi
wait_until 'Registro Docker de Nexus' request -u "admin:$CI_NEXUS_PASSWORD" http://nexus:5000/v2/
echo 'Registro Docker listo para publicar imágenes.'

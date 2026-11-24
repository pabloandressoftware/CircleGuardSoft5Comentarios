/**
 * HU-19 · Despliegue y promocion con Helm sobre Kubernetes (k3s).
 *
 *   circleGuardHelm('dev')
 *   circleGuardHelm('prod', imageTag: 'v1.2.3')
 *
 * Requiere tres credenciales de Jenkins:
 *   - kubeconfig-<ambiente>       (Secret file con el kubeconfig del cluster)
 *   - circleguard-env-<ambiente>  (Secret file con el .env del ambiente)
 *   - nexus-registry              (usuario y contrasena del registro)
 *
 * El .env aporta REGISTRY y SITE_ADDRESS. El paso crea el namespace, el
 * imagePullSecret del registro, el Secret de la aplicacion y ejecuta
 * deploy/k8s/desplegar.sh, que hace helm upgrade --install y espera el
 * rollout status de cada Deployment. La misma etiqueta SemVer se usa en los
 * tres ambientes; no se reconstruyen imagenes al promover.
 */

// Groovy pasa los argumentos con nombre como un Map en la PRIMERA posicion:
// helm.call('dev', deployMiddleware: true) llega como call(Map, String).
// Sin esta sobrecarga el paso falla con "No such DSL method 'call'".
def call(Map args, String environment) {
    return call(environment, args)
}

def call(String environment, Map args = [:]) {
    if (!(environment in ['dev', 'stage', 'prod'])) {
        error("Ambiente Helm invalido: ${environment}")
    }

    String imageTag = args.get('imageTag', env.IMAGE_TAG)
    if (!imageTag || !(imageTag ==~ /^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/)) {
        error("Falta una etiqueta SemVer vX.Y.Z valida (recibido: ${imageTag})")
    }

    String kubeconfigId = args.get('kubeconfigCredentialId', "kubeconfig-${environment}")
    String envCredentialId = args.get('envCredentialId', "circleguard-env-${environment}")
    String nexusCredentialId = args.get('nexusCredentialId', 'nexus-registry')
    String timeout = args.get('timeout', '10m')
    boolean middleware = args.get('deployMiddleware', false)

    withCredentials([
        file(credentialsId: kubeconfigId, variable: 'KUBECONFIG'),
        file(credentialsId: envCredentialId, variable: 'CIRCLE_ENV_FILE'),
        usernamePassword(credentialsId: nexusCredentialId,
            usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS'),
    ]) {
        withEnv([
            "HELM_ENV=${environment}",
            "HELM_IMAGE_TAG=${imageTag}",
            "HELM_TIMEOUT=${timeout}",
            "DEPLOY_MIDDLEWARE=${middleware}",
        ]) {
            sh '''#!/bin/bash
                set -euo pipefail
                set -a
                . "$CIRCLE_ENV_FILE"
                set +a

                registry="${REGISTRY:?Falta REGISTRY en circleguard-env-${HELM_ENV}}"
                case "$registry" in
                    *127.0.0.1*|*localhost*|*example.invalid*)
                        echo "REGISTRY '$registry' no es alcanzable desde el cluster." >&2
                        exit 1
                        ;;
                esac
                ns="circleguard-${HELM_ENV}"

                kubectl apply -f deploy/k8s/namespaces.yaml >/dev/null

                kubectl -n "$ns" create secret docker-registry nexus-registry \
                    --docker-server="$registry" \
                    --docker-username="$NX_USER" \
                    --docker-password="$NX_PASS" \
                    --dry-run=client -o yaml | kubectl apply -f - >/dev/null

                bash deploy/k8s/crear-secretos.sh "$HELM_ENV"

                if [[ "${TLS_ISSUER:-circleguard-selfsigned}" == circleguard-letsencrypt ]]; then
                    export TLS_ISSUER_MODE=acme
                fi
                bash deploy/k8s/cert-manager.sh

                if [[ "$DEPLOY_MIDDLEWARE" == "true" ]] ||
                    ! kubectl -n "$ns" get statefulset postgres redis-master neo4j >/dev/null 2>&1 ||
                    ! kubectl -n "$ns" get deployment kafka openldap >/dev/null 2>&1; then
                    echo "Desplegando middleware en ${HELM_ENV} (puede tardar varios minutos)..."
                    bash deploy/k8s/middleware.sh "$HELM_ENV"
                fi
                bash deploy/k8s/verificar-postgres.sh "$HELM_ENV"

                REGISTRY="$registry" bash deploy/k8s/desplegar.sh \
                    "$HELM_ENV" "$HELM_IMAGE_TAG"

                node deploy/k8s/releases.mjs snapshot "$HELM_ENV" \
                    ".local-evidence/hu-27/$HELM_ENV/deploy"

                host=$(printf '%s' "${SITE_ADDRESS:-}" | cut -d, -f1 | xargs)
                printf '%s' "$host" > ".helm-host-${HELM_ENV}.txt"
            '''
        }
    }

    String host = readFile(".helm-host-${environment}.txt").trim()
    if (!(host ==~ /^[A-Za-z0-9][A-Za-z0-9.-]*$/)) {
        error("SITE_ADDRESS invalido en circleguard-env-${environment} (recibido: '${host}')")
    }

    writeFile file: "deploy-helm-${environment}.txt",
        text: "env=${environment}\ntag=${imageTag}\nhost=${host}\ntarget=helm\nbuild=${env.BUILD_URL}\n"
    archiveArtifacts artifacts: "deploy-helm-${environment}.txt", allowEmptyArchive: true
    echo "${imageTag} desplegado con Helm en ${environment}: https://${host}"
    return host
}

// HU-27: restaura revisiones existentes; no reconstruye imágenes, aplica
// Terraform, actualiza Secrets ni reinstala middleware.
def rollback(String environment, String imageTag) {
    if (!(environment in ['dev', 'stage', 'prod']) ||
        !(imageTag ==~ /^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/)) {
        error('Ambiente o etiqueta de rollback inválidos')
    }
    withCredentials([
        file(credentialsId: "kubeconfig-${environment}", variable: 'KUBECONFIG'),
        file(credentialsId: "circleguard-env-${environment}", variable: 'CIRCLE_ENV_FILE'),
    ]) {
        withEnv(["HELM_ENV=${environment}", "HELM_IMAGE_TAG=${imageTag}"]) {
            sh '''#!/bin/bash
                set -euo pipefail
                set -a
                . "$CIRCLE_ENV_FILE"
                set +a
                host=$(printf '%s' "${SITE_ADDRESS:?Falta SITE_ADDRESS}" | cut -d, -f1 | xargs)
                node deploy/k8s/releases.mjs rollback "$HELM_ENV" "$HELM_IMAGE_TAG" \
                    "https://$host/" ".local-evidence/hu-27/$HELM_ENV/rollback"
                printf '%s' "$host" > ".helm-host-${HELM_ENV}.txt"
            '''
        }
    }
    return readFile(".helm-host-${environment}.txt").trim()
}

def inventory() {
    withCredentials([
        file(credentialsId: 'kubeconfig-dev', variable: 'KUBECONFIG_DEV'),
        file(credentialsId: 'kubeconfig-stage', variable: 'KUBECONFIG_STAGE'),
        file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG_PROD'),
    ]) {
        sh 'node deploy/k8s/releases.mjs snapshot all .local-evidence/hu-27/overview'
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

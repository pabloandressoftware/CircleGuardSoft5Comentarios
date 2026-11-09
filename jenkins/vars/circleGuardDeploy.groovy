/**
 * HU-10 · Despliegue de una etiqueta inmutable en dev, stage o prod.
 *
 *   circleGuardDeploy('dev')
 *   circleGuardDeploy('prod', imageTag: 'v1.2.3')
 *
 * Hace, en este orden:
 *   1. Lee la credencial file circleguard-env-<ambiente> y saca el DNS del .env.
 *   2. Instala ese .env en /opt/circleguard/.env de la VM (permisos 0600).
 *   3. Copia los archivos de deploy/ a /opt/circleguard/.
 *   4. Abre un tunel inverso 127.0.0.1:5000 -> nexus:5000 y ejecuta run.sh con
 *      la etiqueta. La contrasena de Nexus viaja por stdin, nunca por la linea
 *      de comandos ni por la consola.
 *
 * La VM usa sudo -n docker porque ubuntu no esta en el grupo docker.
 * Devuelve el host del ambiente para las etapas de humo y E2E.
 */

def call(String environment, Map args = [:]) {
    if (!(environment in ['dev', 'stage', 'prod'])) {
        error("Ambiente invalido: ${environment}")
    }

    String sshCredentialId = args.get('sshCredentialId', 'circleguard-env-ssh')
    String nexusCredentialId = args.get('nexusCredentialId', 'nexus-registry')
    String imageTag = args.get('imageTag', env.IMAGE_TAG)
    if (!imageTag || !(imageTag ==~ /^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/)) {
        error("Falta una etiqueta SemVer vX.Y.Z valida (recibido: ${imageTag})")
    }

    String host = null
    sshagent(credentials: [sshCredentialId]) {
        withCredentials([
            file(credentialsId: "circleguard-env-${environment}", variable: 'CIRCLE_ENV_FILE'),
            usernamePassword(credentialsId: nexusCredentialId,
                usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS'),
        ]) {
            host = sh(script: '''
                set -e
                grep -E '^SITE_ADDRESS=' "$CIRCLE_ENV_FILE" | head -n 1 | cut -d= -f2- | cut -d, -f1 | xargs
            ''', returnStdout: true).trim()
            if (!(host ==~ /^[A-Za-z0-9][A-Za-z0-9.-]*$/)) {
                error("No se pudo obtener un DNS valido de circleguard-env-${environment}")
            }

            // install-env.sh exige StrictHostKeyChecking=yes: sembramos la llave.
            sh """
                mkdir -p ~/.ssh && chmod 700 ~/.ssh
                ssh-keygen -R '${host}' >/dev/null 2>&1 || true
                ssh-keyscan -H '${host}' >> ~/.ssh/known_hosts 2>/dev/null || true
            """
            sh 'bash infra/bootstrap/install-env.sh "ubuntu@'"${host}"'" "$CIRCLE_ENV_FILE"'

            // Los archivos del Compose quedan junto al .env en /opt/circleguard/.
            sh '''
                set -eo pipefail
                tar -C deploy -cf - . | ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
                    "ubuntu@''' + host + '''" 'mkdir -p /opt/circleguard && tar -xf - -C /opt/circleguard'
            '''

            echo "Desplegando ${imageTag} en ${environment} (${host})"
            withEnv(["DEPLOY_HOST=${host}", "DEPLOY_TAG=${imageTag}"]) {
                sh '''#!/bin/bash
                    set -eo pipefail
                    printf '%s\n' "$NX_PASS" | ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
                        -o ExitOnForwardFailure=yes -R 5000:nexus:5000 \
                        "ubuntu@$DEPLOY_HOST" "bash /opt/circleguard/run.sh $DEPLOY_TAG"
                '''
            }
        }
    }

    writeFile file: "deploy-${environment}.txt",
        text: "env=${environment}\ntag=${imageTag}\nhost=${host}\nbuild=${env.BUILD_URL}\n"
    archiveArtifacts artifacts: "deploy-${environment}.txt", allowEmptyArchive: true
    echo "${imageTag} desplegado en ${environment}: https://${host}"
    return host
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

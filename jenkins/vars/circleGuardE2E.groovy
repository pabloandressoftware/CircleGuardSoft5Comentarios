/**
 * HU-10 · E2E de Playwright contra dev después del despliegue y el humo.
 *
 *   circleGuardE2E(host: 'ec2-....compute.amazonaws.com')
 *   circleGuardE2E(host: host, environment: 'dev', healthCredentialId: 'circleguard-e2e-health')
 *
 * Prepara lo que la suite espera:
 *   - URLs del proxy HTTPS del ambiente.
 *   - Acepta la CA interna de Caddy (E2E_IGNORE_HTTPS_ERRORS=1).
 *   - Siembra el cuestionario si falta (E2E_SEED_QUESTIONNAIRE=1).
 *   - Abre un tunel SSH a Redis de dev para los casos de QR y lo cierra al final.
 *   - Usa la credencial circleguard-e2e-health (health_user / password sembrada).
 */
def call(Map args = [:]) {
    String host = args.get('host')
    String environment = args.get('environment', 'dev')
    String healthCredentialId = args.get('healthCredentialId', 'circleguard-e2e-health')
    String sshCredentialId = args.get('sshCredentialId', 'circleguard-env-ssh')

    if (!host || !(host ==~ /^[A-Za-z0-9][A-Za-z0-9.-]*$/)) {
        error("Host invalido para E2E: ${host}")
    }
    if (!(environment in ['dev', 'local'])) {
        error("La suite E2E solo admite dev o local; recibido: ${environment}")
    }

    withCredentials([file(credentialsId: "circleguard-env-${environment}", variable: 'CIRCLE_ENV_FILE')]) {
        String redisPassword = sh(script: '''
            set -e
            grep -E '^REDIS_PASSWORD=' "$CIRCLE_ENV_FILE" | head -n 1 | cut -d= -f2-
        ''', returnStdout: true).trim()
        if (!redisPassword) {
            error("No se encontro REDIS_PASSWORD en circleguard-env-${environment}")
        }

        sshagent(credentials: [sshCredentialId]) {
            sh '''#!/bin/bash
                set -eo pipefail
                ssh -f -N -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
                    -o ExitOnForwardFailure=yes -L 6379:127.0.0.1:6379 \
                    "ubuntu@''' + host + '''"
            '''
        }

        try {
            withEnv([
                'CI=true',
                "E2E_ENV=${environment}",
                "E2E_WEB_URL=https://${host}",
                "E2E_AUTH_URL=https://${host}",
                "E2E_FORM_URL=https://${host}",
                "E2E_GATEWAY_URL=https://${host}",
                'E2E_IGNORE_HTTPS_ERRORS=1',
                'E2E_SEED_QUESTIONNAIRE=1',
                "E2E_REDIS_URL=redis://:${redisPassword}@127.0.0.1:6379",
            ]) {
                withCredentials([usernamePassword(credentialsId: healthCredentialId,
                    usernameVariable: 'E2E_HEALTH_USERNAME', passwordVariable: 'E2E_HEALTH_PASSWORD')]) {
                    dir('e2e') {
                        sh 'npm ci --no-audit --no-fund'
                        sh 'npx playwright install --only-shell chromium'
                        try {
                            sh 'npm test'
                        } finally {
                            archiveArtifacts artifacts: 'playwright-report/**', allowEmptyArchive: true
                        }
                    }
                }
            }
        } finally {
            sh 'pkill -f "6379:127.0.0.1:6379" || true'
        }
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

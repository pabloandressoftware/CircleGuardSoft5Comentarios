/**
 * HU-15 · Playwright contra dev después del despliegue o en un build manual.
 * El host sale del archivo de ambiente si el pipeline no acaba de desplegarlo.
 */
def call(Map args = [:]) {
    String host = args.get('host')
    String environment = args.get('environment', 'dev')
    String healthCredentialId = args.get('healthCredentialId', 'circleguard-e2e-health')
    String sshCredentialId = args.get('sshCredentialId', 'circleguard-env-ssh')

    if (!(environment in ['dev', 'local'])) {
        error("La suite E2E solo admite dev o local; recibido: ${environment}")
    }

    withCredentials([file(credentialsId: "circleguard-env-${environment}", variable: 'CIRCLE_ENV_FILE')]) {
        if (!host) {
            host = sh(
                script: '''grep -m1 '^SITE_ADDRESS=' "$CIRCLE_ENV_FILE" | cut -d= -f2- | cut -d, -f1''',
                returnStdout: true
            ).trim()
        }
        if (!(host ==~ /^[A-Za-z0-9][A-Za-z0-9.-]*$/)) {
            error("Host invalido para E2E en circleguard-env-${environment}")
        }

        withEnv([
            'CI=true',
            "E2E_ENV=${environment}",
            "E2E_HOST=${host}",
            "E2E_WEB_URL=https://${host}",
            "E2E_AUTH_URL=https://${host}",
            "E2E_FORM_URL=https://${host}",
            "E2E_GATEWAY_URL=https://${host}",
            'E2E_IGNORE_HTTPS_ERRORS=1',
            'E2E_SEED_QUESTIONNAIRE=1',
        ]) {
            dir('e2e') {
                dir('playwright-report') { deleteDir() }
                sh 'npm ci --no-audit --no-fund'
                sh 'npx playwright install --only-shell chromium'
                try {
                    withCredentials([usernamePassword(credentialsId: healthCredentialId,
                        usernameVariable: 'E2E_HEALTH_USERNAME', passwordVariable: 'E2E_HEALTH_PASSWORD')]) {
                        sshagent(credentials: [sshCredentialId]) {
                            sh '''#!/bin/bash
                                set +x
                                set -euo pipefail
                                redis_password=$(env -u REDIS_PASSWORD node --env-file="$CIRCLE_ENV_FILE" -e '
                                    const password = process.env.REDIS_PASSWORD;
                                    if (!password) {
                                        console.error("Falta REDIS_PASSWORD en el archivo de ambiente");
                                        process.exit(1);
                                    }
                                    process.stdout.write(encodeURIComponent(password));
                                ')
                                tunnel_dir=$(mktemp -d)
                                control_socket="$tunnel_dir/ssh"
                                target="ubuntu@$E2E_HOST"
                                close_tunnel() {
                                    ssh -S "$control_socket" -O exit "$target" >/dev/null 2>&1 || true
                                    rmdir "$tunnel_dir" || true
                                }
                                trap close_tunnel EXIT
                                trap 'exit 143' TERM
                                trap 'exit 130' INT
                                ssh -fNT -M -S "$control_socket" -o BatchMode=yes \\
                                    -o StrictHostKeyChecking=accept-new -o ExitOnForwardFailure=yes \\
                                    -L 127.0.0.1:16379:127.0.0.1:6379 "$target"
                                export E2E_REDIS_URL="redis://:$redis_password@127.0.0.1:16379"
                                npm test
                            '''
                        }
                    }
                } finally {
                    archiveArtifacts artifacts: 'playwright-report/**', allowEmptyArchive: true
                }
            }
        }
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

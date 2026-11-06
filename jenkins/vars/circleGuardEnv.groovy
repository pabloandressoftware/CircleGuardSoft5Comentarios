def call(String environment, String sshTarget, String sshCredentialId) {
    if (!(environment in ['dev', 'stage', 'prod'])) {
        error('Ambiente inválido: ' + environment)
    }

    sshagent(credentials: [sshCredentialId]) {
        withCredentials([file(credentialsId: "circleguard-env-${environment}", variable: 'CIRCLE_ENV_FILE')]) {
            withEnv(["CIRCLE_SSH_TARGET=${sshTarget}"]) {
                sh 'bash infra/bootstrap/install-env.sh "$CIRCLE_SSH_TARGET" "$CIRCLE_ENV_FILE"'
            }
        }
    }
}

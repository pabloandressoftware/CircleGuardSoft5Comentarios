def call(String credentialId) {
    withEnv(['CI=true', 'E2E_ENV=dev']) {
        dir('e2e') {
            sh 'npm ci'
            sh 'npx playwright install --only-shell chromium'

            try {
                withCredentials([usernamePassword(credentialsId: credentialId,
                    usernameVariable: 'E2E_HEALTH_USERNAME', passwordVariable: 'E2E_HEALTH_PASSWORD')]) {
                    sh 'npm test'
                }
            } finally {
                archiveArtifacts artifacts: 'playwright-report/**', allowEmptyArchive: true
            }
        }
    }
}

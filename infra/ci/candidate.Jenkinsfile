// Valida y publica snapshots identificados por commit; no despliega ambientes.
pipeline {
    agent { label 'docker' }
    options { timeout(time: 90, unit: 'MINUTES'); disableConcurrentBuilds(); timestamps() }
    stages {
        stage('Verificar codigo y dependencias') {
            steps {
                sh 'node --test deploy/k8s/releases.test.mjs scripts/check-release-notes.test.mjs scripts/spring-rendering-vex.test.mjs infra/terraform/scripts/check-plan.test.mjs'
                sh 'gradle --no-daemon --max-workers=1 --continue test jacocoTestReport jacocoTestCoverageVerification'
                sh 'gradle --no-daemon --max-workers=1 --continue integrationTest'
            }
        }
        stage('Construir, escanear y publicar snapshot') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-registry',
                    usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
                    sh 'bash infra/ci/publish-candidate.sh'
                }
            }
        }
    }
    post {
        always {
            junit testResults: 'services/*/build/test-results/**/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'security-reports/**', allowEmptyArchive: true
        }
    }
}

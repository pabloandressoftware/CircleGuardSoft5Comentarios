/**
 * HU-09 · Compilacion, pruebas, cobertura y analisis con quality gate bloqueante.
 *
 *   circleGuardQuality()                       // los 8 servicios
 *   circleGuardQuality(sonarInstallation: 'sonarqube', gateTimeoutMinutes: 10)
 *
 * 1. gradle test + jacocoTestReport en todos los servicios (--continue: si uno falla,
 *    los demas igual generan su reporte para el diagnostico).
 * 2. Publica JUnit y el HTML de JaCoCo de cada servicio, tambien si algo fallo.
 * 3. Analiza con SonarQube y espera el quality gate: si falla, aborta el pipeline.
 */
def call(Map args = [:]) {
    String sonarInstallation = args.get('sonarInstallation', 'sonarqube')
    int gateTimeoutMinutes = args.get('gateTimeoutMinutes', 10) as int
    List<String> services = ['auth', 'identity', 'promotion', 'form', 'gateway', 'notification', 'file', 'dashboard']

    try {
        sh 'gradle --no-daemon --continue test jacocoTestReport'
    } finally {
        junit testResults: 'services/*/build/test-results/test/*.xml', allowEmptyResults: true
        for (String service : services) {
            String reportDir = "services/circleguard-${service}-service/build/reports/jacoco/test/html"
            if (fileExists("${reportDir}/index.html")) {
                publishHTML(target: [
                    reportName           : "Cobertura ${service}",
                    reportDir            : reportDir,
                    reportFiles          : 'index.html',
                    keepAll              : true,
                    alwaysLinkToLastBuild: true,
                    allowMissing         : true,
                ])
            }
        }
        archiveArtifacts artifacts: 'services/*/build/reports/jacoco/test/jacocoTestReport.xml', allowEmptyArchive: true
    }

    withSonarQubeEnv(sonarInstallation) {
        sh 'gradle --no-daemon sonar'
    }
    timeout(time: gateTimeoutMinutes, unit: 'MINUTES') {
        // abortPipeline: un gate en ERROR marca el build como fallido y detiene el pipeline
        waitForQualityGate abortPipeline: true
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

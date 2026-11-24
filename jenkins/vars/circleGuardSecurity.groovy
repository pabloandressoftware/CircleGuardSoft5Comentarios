/**
 * HU-20 · Controles de seguridad del pipeline: gitleaks y Trivy.
 *
 *   circleGuardSecurity.secrets()                 // gitleaks: falla si hay un secreto
 *   circleGuardSecurity.filesystem()              // Trivy: dependencias e IaC, falla con CRITICAL
 *   circleGuardSecurity.images(registry: '...', tag: '1.0.0')   // Trivy sobre las imagenes
 *
 * gitleaks revisa los archivos actuales (--no-git): los secretos que ya estan en el historial
 * no se pueden quitar con un commit, se tratan rotandolos (ver docs/seguridad.md).
 * Las herramientas corren en contenedores (el agente ya tiene Docker), asi que no hay
 * que instalar nada en Jenkins. Ningun secreto se imprime: gitleaks usa --redact.
 * El umbral es CRITICAL; para subirlo o bajarlo se cambia SEVERITY aqui o por parametro.
 */
def secrets() {
    sh '''#!/bin/bash
        set -eo pipefail
        mkdir -p security-reports
        # --redact oculta el valor del secreto en el reporte y en el log.
        docker run --rm -v "$PWD":/repo zricethezav/gitleaks:v8.21.2 detect --no-git \
            --source /repo --config /repo/.gitleaks.toml --redact --no-banner \
            --report-format json --report-path /repo/security-reports/gitleaks.json
    '''
    archiveArtifacts artifacts: 'security-reports/gitleaks.json', allowEmptyArchive: true
}

def filesystem(Map args = [:]) {
    String severity = args.get('severity', 'CRITICAL')
    sh """#!/bin/bash
        set -eo pipefail
        mkdir -p security-reports
        # Dependencias (gradle, npm), IaC (Dockerfile, compose, Helm, Terraform) y secretos.
        # --exit-code 1: cualquier hallazgo del nivel indicado rompe el build.
        # .terraform/ y tfplan son artefactos generados por la etapa Terraform plan
        # (providers y estado), no codigo del repositorio. Las excepciones acotadas y
        # con vencimiento viven en .trivyignore.yaml y se muestran como suprimidas.
        docker run --rm -v "\$PWD":/src -v trivy-cache:/root/.cache/ aquasec/trivy:0.58.1 fs \
            --scanners vuln,misconfig --severity ${severity} --ignore-unfixed \
            --skip-dirs 'node_modules,build,bin,.gradle,**/.terraform' --skip-files '**/tfplan' \
            --ignorefile /src/.trivyignore.yaml --show-suppressed \
            --exit-code 1 --format table /src | tee security-reports/trivy-fs.txt
    """
    archiveArtifacts artifacts: 'security-reports/trivy-fs.txt', allowEmptyArchive: true
}

def images(Map args = [:]) {
    String registry = args.get('registry', env.REGISTRY ?: '127.0.0.1:5000')
    String tag = args.get('tag')
    String severity = args.get('severity', 'CRITICAL')
    List<String> names = ['auth', 'identity', 'promotion', 'form', 'gateway', 'notification', 'file', 'dashboard']
        .collect { "circleguard-${it}-service" } + ['web']
    try {
        for (String name : names) {
            withEnv(["TRIVY_IMAGE=${registry}/circleguard/${name}:${tag}",
                     "TRIVY_REPORT_NAME=${name}", "TRIVY_SEVERITY=${severity}"]) {
                sh 'bash scripts/scan-image.sh "$TRIVY_IMAGE" "$TRIVY_REPORT_NAME"'
            }
        }
    } finally {
        archiveArtifacts artifacts: 'security-reports/**', allowEmptyArchive: true
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

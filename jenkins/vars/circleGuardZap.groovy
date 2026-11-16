/**
 * HU-26 · Baseline scan de OWASP ZAP contra el ambiente stage.
 *
 *   circleGuardZap(host: env.STAGE_HOST)
 *
 * El baseline es pasivo: solo observa las respuestas, no ataca. Corre en un contenedor.
 * Codigos de salida de ZAP: 0 sin alertas, 1 hay alertas FAIL, 2 hay alertas WARN.
 * Las advertencias (WARN) no rompen el build, pero quedan en el reporte HTML para el
 * informe; solo las FAIL lo rompen.
 */
def call(Map args = [:]) {
    String host = args.get('host')
    if (!host?.trim()) {
        error 'circleGuardZap necesita el host de stage'
    }
    sh """#!/bin/bash
        mkdir -p security-reports && chmod 777 security-reports
        # -I ignora el codigo 2 (WARN); el certificado interno de stage no es publico
        docker run --rm -v "\$PWD/security-reports":/zap/wrk:rw ghcr.io/zaproxy/zaproxy:2.15.0 \\
            zap-baseline.py -t https://${host} -r zap-baseline.html -J zap-baseline.json -I
    """
    archiveArtifacts artifacts: 'security-reports/zap-baseline.*', allowEmptyArchive: true
    publishHTML(target: [
        reportName: 'OWASP ZAP baseline', reportDir: 'security-reports', reportFiles: 'zap-baseline.html',
        keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true,
    ])
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

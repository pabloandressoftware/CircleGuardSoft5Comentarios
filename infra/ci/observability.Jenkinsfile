// HU-24 · Observabilidad por cluster: dev, stage y prod son clusters distintos y
// cada uno tiene su propio kube-prometheus-stack. No despliega la aplicacion.
//
// Credenciales: kubeconfig-<ambiente>, grafana-admin-password (Secret text) y,
// solo si existe, discord-webhook. Sin webhook las alertas quedan en Alertmanager.
pipeline {
    agent { label 'docker' }
    options { timeout(time: 60, unit: 'MINUTES'); disableConcurrentBuilds(); timestamps() }
    parameters {
        choice(name: 'ENVIRONMENT', choices: ['dev', 'stage', 'prod'], description: 'Cluster destino.')
        choice(name: 'ACTION', choices: ['instalar', 'verificar', 'alertas-service-down',
                                         'alertas-high-errors', 'alertas-gate-denials', 'alertas-all'],
            description: 'instalar = stack + reglas; verificar = targets y reglas; alertas-* = evidencia (no en prod).')
    }
    stages {
        stage('Validar reglas') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    docker run --rm -v "$PWD/observability/alerts:/rules:ro" --entrypoint promtool \
                        prom/prometheus:v2.54.1 test rules /rules/tests.yaml
                '''
            }
        }
        stage('Instalar') {
            when { expression { params.ACTION == 'instalar' } }
            steps {
                script {
                    String webhook = ''
                    try {
                        withCredentials([usernamePassword(credentialsId: 'discord-webhook',
                                usernameVariable: 'DISCORD_USER', passwordVariable: 'DISCORD_WEBHOOK')]) {
                            webhook = 'discord-webhook'
                        }
                    } catch (Exception ignored) {
                        echo 'discord-webhook no existe: Alertmanager sin notificaciones externas (Discord pendiente).'
                    }
                    List creds = [
                        file(credentialsId: "kubeconfig-${params.ENVIRONMENT}", variable: 'KUBECONFIG'),
                        string(credentialsId: 'grafana-admin-password', variable: 'GRAFANA_ADMIN_PASSWORD'),
                    ]
                    if (webhook) {
                        creds << usernamePassword(credentialsId: webhook,
                            usernameVariable: 'DISCORD_USER', passwordVariable: 'CI_DISCORD_WEBHOOK')
                    }
                    withCredentials(creds) {
                        sh 'bash observability/instalar.sh'
                    }
                }
            }
        }
        stage('Verificar') {
            when { expression { params.ACTION in ['instalar', 'verificar'] } }
            steps {
                withCredentials([file(credentialsId: "kubeconfig-${params.ENVIRONMENT}", variable: 'KUBECONFIG')]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        mkdir -p observability-report
                        kubectl -n monitoring get pods,servicemonitors,prometheusrules -o wide \
                            | tee observability-report/monitoring.txt
                        kubectl -n monitoring port-forward svc/prometheus-operated 19090:9090 >/dev/null 2>&1 &
                        pf=$!
                        trap 'kill $pf >/dev/null 2>&1 || true' EXIT
                        for _ in $(seq 1 30); do
                            curl -fsS -o /dev/null http://127.0.0.1:19090/-/ready && break
                            sleep 2
                        done
                        curl -fsS http://127.0.0.1:19090/api/v1/targets?state=active \
                            > observability-report/targets.json
                        curl -fsS http://127.0.0.1:19090/api/v1/rules?type=alert \
                            > observability-report/rules.json
                        node -e '
                            const t = require("./observability-report/targets.json").data.activeTargets
                                .filter(x => (x.labels.namespace || "").startsWith("circleguard-"));
                            const by = {};
                            for (const x of t) by[x.labels.job + " " + x.health] = (by[x.labels.job + " " + x.health] || 0) + 1;
                            console.log("Targets CircleGuard:", JSON.stringify(by));
                            const r = require("./observability-report/rules.json").data.groups
                                .flatMap(g => g.rules).map(x => x.name).filter(n => n.startsWith("Circleguard"));
                            console.log("Reglas CircleGuard cargadas:", r.join(", "));
                            if (!r.includes("CircleguardServiceDown")) process.exit(1);
                        ' | tee observability-report/resumen.txt
                    '''
                }
            }
        }
        stage('Evidencia de alertas') {
            when { expression { params.ACTION.startsWith('alertas-') } }
            steps {
                script {
                    if (params.ENVIRONMENT == 'prod') {
                        error('Las pruebas destructivas de alertas no se ejecutan en prod.')
                    }
                    String mode = params.ACTION.replace('alertas-', '')
                    withCredentials([file(credentialsId: "kubeconfig-${params.ENVIRONMENT}", variable: 'KUBECONFIG')]) {
                        withEnv(["ENV_NAME=${params.ENVIRONMENT}", "ALERT_MODE=${mode}",
                                 "EVIDENCE_DIR=observability-report/alertas-${params.ENVIRONMENT}"]) {
                            sh 'bash observability/evidencias/disparar-alertas.sh "$ALERT_MODE"'
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'observability-report/**', allowEmptyArchive: true
        }
    }
}

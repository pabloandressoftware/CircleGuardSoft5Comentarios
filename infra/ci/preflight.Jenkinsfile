// Comprueba herramientas y accesos reales antes de habilitar releases.
pipeline {
    agent { label 'docker' }
    options { timeout(time: 10, unit: 'MINUTES'); disableConcurrentBuilds() }
    stages {
        stage('Herramientas y Docker') {
            steps {
                sh 'terraform version; helm version --short; kubectl version --client; docker version --format "{{.Server.Version}}"'
                writeFile file: 'workspace-mount-check.txt', text: 'CircleGuard CI\n'
                sh 'docker run --rm -v "$PWD:/workspace:ro" alpine:3.21 test -s /workspace/workspace-mount-check.txt'
            }
        }
        stage('Registro HTTPS autenticado') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-registry',
                    usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
                    sh '''#!/bin/bash
                        set +x
                        set -euo pipefail
                        curl -fsS --max-time 20 -u "$NX_USER:$NX_PASS" "https://$REGISTRY/v2/" >/dev/null
                        echo 'Nexus: autenticacion y confianza TLS verificadas.'
                    '''
                }
            }
        }
        stage('Acceso a Kubernetes dev') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-dev', variable: 'KUBECONFIG')]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        kubectl wait --for=condition=Ready node --all --timeout=120s
                        kubectl get nodes -o wide
                        for deployment in coredns local-path-provisioner metrics-server traefik; do
                            kubectl -n kube-system rollout status "deployment/$deployment" --timeout=120s
                        done
                        kubectl auth can-i create deployments -n circleguard-dev | grep -qx yes
                        kubectl -n cert-manager rollout status deployment/cert-manager-webhook --timeout=120s
                    '''
                }
            }
        }
    }
}

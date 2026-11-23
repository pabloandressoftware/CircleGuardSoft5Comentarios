/**
 * HU-21 · Instala cert-manager y el ClusterIssuer autofirmado en el clúster.
 *
 *   circleGuardCertManager.install()
 *
 * Un solo clúster atiende dev, stage y prod, así que basta con instalar
 * cert-manager una vez por tag antes de crear los Ingress. El paso es
 * idempotente: `deploy/k8s/cert-manager.sh` usa `helm upgrade --install` y
 * espera a que el ClusterIssuer quede Ready.
 *
 * Requiere la credencial de Jenkins `kubeconfig-dev` (Secret file). La CA es
 * autofirmada, por eso el humo y las pruebas usan `curl -k`.
 */

def install(Map args = [:]) {
    String kubeconfigId = args.get('kubeconfigCredentialId', 'kubeconfig-dev')

    withCredentials([file(credentialsId: kubeconfigId, variable: 'KUBECONFIG')]) {
        sh '''#!/bin/bash
            set -euo pipefail
            bash deploy/k8s/cert-manager.sh
            kubectl get clusterissuer circleguard-selfsigned \
                -o jsonpath='{.metadata.name}{"\\n"}'
        '''
    }
    echo 'cert-manager y el ClusterIssuer circleguard-selfsigned están listos.'
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

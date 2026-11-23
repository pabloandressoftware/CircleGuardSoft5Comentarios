/**
 * HU-24 · Instala observabilidad en el clúster compartido.
 *
 *   circleGuardObservability.install()
 *
 * Instala kube-prometheus-stack, el ServiceMonitor de los tres namespaces
 * (dev/stage/prod), las alertas y los tableros de Grafana mediante
 * `observability/instalar.sh`. Es idempotente (`helm upgrade --install` y
 * `kubectl apply`), por eso puede ejecutarse en cada tag.
 *
 * Credenciales de Jenkins:
 *   - `kubeconfig-dev`          (Secret file) clúster compartido.
 *   - `discord-webhook`         (usuario/contraseña) webhook de Spidey; la
 *                               contraseña es la URL que usa Alertmanager.
 *   - `grafana-admin-password`  (Secret text) contraseña del usuario admin.
 *
 * La evidencia de alertas (`observability/evidencias/disparar-alertas.sh`) es
 * destructiva y se ejecuta de forma manual, nunca desde este paso.
 */

def install(Map args = [:]) {
    String kubeconfigId = args.get('kubeconfigCredentialId', 'kubeconfig-dev')
    String discordId = args.get('discordCredentialId', 'discord-webhook')
    String grafanaId = args.get('grafanaCredentialId', 'grafana-admin-password')

    withCredentials([
        file(credentialsId: kubeconfigId, variable: 'KUBECONFIG'),
        usernamePassword(credentialsId: discordId,
            usernameVariable: 'DISCORD_USER', passwordVariable: 'DISCORD_WEBHOOK'),
        string(credentialsId: grafanaId, variable: 'GRAFANA_ADMIN_PASSWORD'),
    ]) {
        sh '''#!/bin/bash
            set -euo pipefail
            export CI_DISCORD_WEBHOOK="$DISCORD_WEBHOOK"
            bash observability/instalar.sh
            kubectl -n monitoring get servicemonitor circleguard-services \
                -o jsonpath='{.metadata.name}{"\\n"}'
        '''
    }
    echo 'kube-prometheus-stack, ServiceMonitor, alertas y tableros instalados.'
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

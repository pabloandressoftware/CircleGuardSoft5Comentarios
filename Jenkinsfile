@Library('circleguard') _

// =============================================================================
// CircleGuard · Pipeline (job multibranch de Jenkins)
//
// HU-08 (rol C): disparo por push y deteccion de modulos modificados.
// HU-09 (rol D): calidad (JaCoCo, SonarQube, quality gate bloqueante) y publicacion.
// HU-10 (rol C): despliegue en dev, E2E, promocion de la misma etiqueta a stage
//                y aprobacion manual para prod.
// HU-19 (rol C): Terraform plan/apply por ambiente y despliegue Helm con espera
//                de rollout status; Compose queda como respaldo (DEPLOY_TARGET).
//
// Los pasos de HU-09/HU-10 viven en jenkins/vars/ y se cargan con "load" desde
// el mismo commit del build, asi un PR prueba su propia version de los pasos.
// La deteccion de modulos (circleGuardChanges) llega por la Shared Library.
//
// Despliegue oficial solo en tags vX.Y.Z (Release Please). Un merge normal a
// main no publica ni despliega: la promocion usa siempre la misma imagen
// inmutable en dev, stage y prod.
// =============================================================================
def quality
def publish
def deploy
def e2e
def smoke
def security
def zap
def terraform
def helm
def changed = []
def infraChanged = false

// HU-11: avisos por Discord (webhook en la credencial 'discord-webhook') y por correo.
// Un fallo al avisar nunca cambia el resultado del build.
def notifyByMail(String subject, String body) {
    notifyDiscord(subject, body)
    String to = env.NOTIFY_EMAILS?.trim()
    if (!to) {
        echo 'NOTIFY_EMAILS vacio: no se envia correo (HU-11).'
        return
    }
    try {
        mail to: to, from: env.NOTIFY_FROM ?: 'jenkins@circleguard.local', subject: subject, body: body
        echo "Notificacion enviada a ${to}"
    } catch (Exception e) {
        echo "No se pudo enviar el correo: ${e.message}"
    }
}

def notifyDiscord(String subject, String body) {
    try {
        withCredentials([usernamePassword(credentialsId: 'discord-webhook',
                usernameVariable: 'DISCORD_USER', passwordVariable: 'DISCORD_WEBHOOK')]) {
            String content = ("**${subject}**\n${body}" as String)
            writeFile file: 'discord-message.json',
                text: groovy.json.JsonOutput.toJson([content: content.take(1900)])
            // La URL llega por variable de entorno enmascarada: no aparece en la consola.
            sh 'curl -fsS -X POST -H "Content-Type: application/json" --data @discord-message.json "$DISCORD_WEBHOOK" >/dev/null'
            echo 'Notificacion enviada a Discord'
        }
    } catch (Exception e) {
        echo "Discord no disponible (se omite): ${e.message}"
    }
}

pipeline {
    agent { label 'docker' }

    triggers { githubPush() }

    parameters {
        booleanParam(
            name: 'BUILD_ALL',
            defaultValue: false,
            description: 'Construir los ocho servicios aunque git diff no detecte cambios.'
        )
        booleanParam(
            name: 'SKIP_DEPLOY',
            defaultValue: false,
            description: 'Validar el pipeline sin tocar ninguna VM (util en local).'
        )
        string(
            name: 'ROLLBACK_TAG',
            defaultValue: '',
            description: 'HU-11: etiqueta vX.Y.Z anterior a redesplegar en ROLLBACK_ENV, sin reconstruir imagenes. Vacio = pipeline normal.'
        )
        choice(
            name: 'ROLLBACK_ENV',
            choices: ['dev', 'stage', 'prod'],
            description: 'Ambiente donde se hace el rollback (solo si ROLLBACK_TAG tiene valor).'
        )
        booleanParam(
            name: 'RUN_E2E_DEV',
            defaultValue: false,
            description: 'En la rama dev, probar el ambiente ya desplegado con Playwright.'
        )
        choice(
            name: 'DEPLOY_TARGET',
            choices: ['helm', 'compose'],
            description: 'HU-19: destino oficial de dev/stage/prod. helm = Kubernetes (k3s); compose = respaldo en las VM actuales.'
        )
        booleanParam(
            name: 'DEPLOY_MIDDLEWARE',
            defaultValue: false,
            description: 'HU-19: desplegar PostgreSQL, Redis, Kafka, Neo4j y OpenLDAP en el namespace antes de los servicios. Solo para el primer despliegue de un cluster.'
        )
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        // Holgado: incluye Terraform, el middleware y la aprobacion de prod.
        timeout(time: 300, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Preparar') {
            steps {
                script {
                    sh 'git fetch --no-tags --prune origin +refs/heads/*:refs/remotes/origin/*'
                    sh 'git fetch --tags origin'
                    quality = load 'jenkins/vars/circleGuardQuality.groovy'
                    publish = load 'jenkins/vars/circleGuardPublish.groovy'
                    deploy = load 'jenkins/vars/circleGuardDeploy.groovy'
                    e2e = load 'jenkins/vars/circleGuardE2E.groovy'
                    smoke = load 'jenkins/vars/circleGuardSmoke.groovy'
                    security = load 'jenkins/vars/circleGuardSecurity.groovy'
                    zap = load 'jenkins/vars/circleGuardZap.groovy'
                    terraform = load 'jenkins/vars/circleGuardTerraform.groovy'
                    helm = load 'jenkins/vars/circleGuardHelm.groovy'
                    sh 'node --test deploy/k8s/releases.test.mjs scripts/check-release-notes.test.mjs'

                    String baseRef = env.CHANGE_TARGET
                        ? "origin/${env.CHANGE_TARGET}"
                        : (env.BRANCH_NAME == 'dev' ? 'HEAD^' : 'origin/dev')
                    infraChanged = sh(
                        script: "git diff --name-only \"${baseRef}...HEAD\" -- infra/ | grep -q .",
                        returnStatus: true
                    ) == 0
                }
            }
        }

        // HU-08: decide que servicios cambiaron respecto de dev (o todos con BUILD_ALL).
        stage('Detectar módulos modificados') {
            steps {
                script {
                    changed = circleGuardChanges(buildAll: params.BUILD_ALL, baseBranch: 'dev')
                    writeFile file: 'changed-services.txt', text: (changed ? changed.join('\n') : '')
                    archiveArtifacts artifacts: 'changed-services.txt', allowEmptyArchive: true
                }
            }
        }

        // HU-19: cada PR que toca infra/ (o cada tag) revisa los tres ambientes con
        // terraform plan. Los applies van mas adelante, antes del despliegue de cada
        // ambiente y despues de la aprobacion correspondiente. Si HU-17 aun no
        // agrego infra/terraform, el paso lo avisa y no rompe el build.
        stage('Terraform plan') {
            when {
                expression { !params.ROLLBACK_TAG }
                expression { terraform.available() }
                expression { infraChanged || buildingTag() }
            }
            steps {
                script {
                    for (String ambiente : ['dev', 'stage', 'prod']) {
                        terraform.plan(ambiente)
                    }
                }
            }
        }

        // HU-09: compilacion, pruebas, JaCoCo, SonarQube y quality gate bloqueante.
        // Corre cuando algun modulo cambio y siempre en tags de release.
        stage('Calidad de servicios') {
            when {
                allOf {
                    expression { !params.ROLLBACK_TAG }
                    anyOf {
                        expression { !changed.isEmpty() }
                        buildingTag()
                    }
                }
            }
            steps {
                script { quality.call() }
            }
        }

        // HU-20: gitleaks falla si hay un secreto; Trivy falla con hallazgos CRITICAL
        // en dependencias e IaC. No corre en un rollback (no construye nada nuevo).
        stage('Seguridad') {
            when { expression { !params.ROLLBACK_TAG } }
            steps {
                script {
                    security.secrets()
                    security.filesystem()
                }
            }
        }

        stage('Pruebas del cliente web') {
            when { expression { !params.ROLLBACK_TAG } }
            steps {
                dir('mobile') {
                    sh 'npm ci --no-audit --no-fund'
                    sh 'npx jest --ci'
                }
            }
        }

        // HU-09: la publicacion oficial solo ocurre en builds de un tag de release
        // (vX.Y.Z), que Release Please crea desde main. Ramas y PR nunca publican.
        // HU-11: rollback basico. Redespliega una etiqueta ya publicada (inmutable en Nexus)
        // con los mismos pasos y la misma prueba de humo del despliegue normal.
        stage('Rollback') {
            when {
                expression { params.ROLLBACK_TAG?.trim() && !params.SKIP_DEPLOY }
            }
            steps {
                script {
                    String tag = params.ROLLBACK_TAG.trim()
                    if (!(tag ==~ /^v\d+\.\d+\.\d+$/)) {
                        error("ROLLBACK_TAG invalido: ${tag} (se espera vX.Y.Z)")
                    }
                    if (params.ROLLBACK_ENV == 'prod') {
                        def approver = input(
                            message: "¿Volver prod a ${tag}?",
                            submitter: 'aprobador',
                            submitterParameter: 'APPROVER',
                            ok: 'Hacer rollback en prod'
                        )
                        echo "Rollback de prod aprobado por ${approver}"
                        writeFile file: 'approval-prod-rollback.txt',
                            text: "env=prod\ntag=${tag}\napprover=${approver}\nbuild=${env.BUILD_URL}\n"
                    }
                    String host = params.DEPLOY_TARGET == 'compose'
                        ? deploy.call(params.ROLLBACK_ENV, imageTag: tag)
                        : helm.rollback(params.ROLLBACK_ENV, tag)
                    smoke.call(host)
                    writeFile file: 'deploy-rollback.txt',
                        text: "env=${params.ROLLBACK_ENV}\ntag=${tag}\nhost=${host}\nbuild=${env.BUILD_URL}\n"
                    notifyByMail(
                        "[CircleGuard] ROLLBACK ${params.ROLLBACK_ENV} a ${tag}",
                        "Se restauro ${tag} en ${params.ROLLBACK_ENV} (${host}) y paso la prueba de humo.\nBuild: ${env.BUILD_URL}"
                    )
                }
            }
        }

        // HU-20: primero se construye, despues Trivy bloquea y solo entonces se publica.
        stage('Construir imagenes') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                }
            }
            steps {
                script { env.IMAGE_TAG = publish.call() }
            }
        }

        // HU-20: Trivy sobre las imagenes construidas; CRITICAL bloquea antes del push.
        stage('Escanear imagenes') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                }
            }
            steps {
                script { security.images(tag: env.IMAGE_TAG) }
            }
        }

        // HU-09: publicacion inmutable solo si el escaneo anterior paso.
        stage('Publicar imagenes') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                }
            }
            steps {
                script { publish.push() }
            }
        }

        // HU-19: la infraestructura de dev se aplica antes de desplegar la aplicacion.
        stage('Terraform apply dev') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY && !params.ROLLBACK_TAG && terraform.available() }
                }
            }
            steps {
                script { terraform.apply('dev') }
            }
        }

        // HU-10: dev recibe la etiqueta recien publicada y debe superar el humo.
        stage('Desplegar dev') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script {
                    env.DEV_HOST = params.DEPLOY_TARGET == 'compose'
                        ? deploy.call('dev')
                        : helm.call('dev', deployMiddleware: params.DEPLOY_MIDDLEWARE)
                }
            }
        }

        stage('Humo dev') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { smoke.call(env.DEV_HOST) }
            }
        }

        // HU-15: la suite completa debe pasar al 100 % antes de promover a stage.
        stage('E2E dev') {
            when {
                allOf {
                    expression { !params.SKIP_DEPLOY && !params.ROLLBACK_TAG }
                    anyOf {
                        allOf {
                            buildingTag()
                            tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                            expression { !params.ROLLBACK_TAG }
                        }
                        allOf {
                            branch 'dev'
                            expression { params.RUN_E2E_DEV }
                        }
                    }
                }
            }
            steps {
                script { e2e.call(host: env.DEV_HOST, environment: 'dev', deployTarget: params.DEPLOY_TARGET) }
            }
        }

        // HU-19: la infraestructura de stage se aplica antes de promover la misma etiqueta.
        stage('Terraform apply stage') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY && !params.ROLLBACK_TAG && terraform.available() }
                }
            }
            steps {
                script { terraform.apply('stage') }
            }
        }

        // Promocion: MISMA etiqueta ya validada en dev, sin reconstruir imagenes.
        stage('Desplegar stage') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script {
                    env.STAGE_HOST = params.DEPLOY_TARGET == 'compose'
                        ? deploy.call('stage')
                        : helm.call('stage', deployMiddleware: params.DEPLOY_MIDDLEWARE)
                }
            }
        }

        stage('Humo stage') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { smoke.call(env.STAGE_HOST) }
            }
        }

        // HU-26: baseline scan de OWASP ZAP contra stage. Las alertas FAIL rompen el build.
        stage('ZAP stage') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { zap.call(host: env.STAGE_HOST) }
            }
        }

        // HU-10: prod exige aprobacion de un usuario restringido y deja registro.
        stage('Aprobacion prod') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script {
                    def approver = input(
                        message: "¿Desplegar ${env.IMAGE_TAG} en prod?",
                        submitter: 'aprobador',
                        submitterParameter: 'APPROVER',
                        ok: 'Desplegar prod'
                    )
                    writeFile file: 'approval-prod.txt',
                        text: "env=prod\ntag=${env.IMAGE_TAG}\napprover=${approver}\nbuild=${env.BUILD_URL}\n"
                    archiveArtifacts artifacts: 'approval-prod.txt'
                    echo "Prod aprobado por ${approver}"
                }
            }
        }

        // HU-19: el apply de prod ocurre despues de la aprobacion del usuario aprobador.
        stage('Terraform apply prod') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY && !params.ROLLBACK_TAG && terraform.available() }
                }
            }
            steps {
                script { terraform.apply('prod') }
            }
        }

        stage('Desplegar prod') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script {
                    env.PROD_HOST = params.DEPLOY_TARGET == 'compose'
                        ? deploy.call('prod')
                        : helm.call('prod', deployMiddleware: params.DEPLOY_MIDDLEWARE)
                }
            }
        }

        stage('Humo prod') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.ROLLBACK_TAG }
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { smoke.call(env.PROD_HOST) }
            }
        }

        stage('Versiones por ambiente') {
            when {
                expression { !params.SKIP_DEPLOY && params.DEPLOY_TARGET == 'helm' }
                anyOf {
                    expression { params.ROLLBACK_TAG?.trim() }
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                }
            }
            steps {
                script { helm.inventory() }
            }
        }
    }

    post {
        failure {
            script {
                notifyByMail(
                    "[CircleGuard] FALLO ${env.JOB_NAME.replace('%2F', '/')} #${env.BUILD_NUMBER}",
                    "El pipeline fallo.\n\nRama/tag: ${env.BRANCH_NAME ?: env.TAG_NAME}\nEtiqueta: ${env.IMAGE_TAG ?: '-'}\nBuild: ${env.BUILD_URL ?: '(define la URL de Jenkins)'}\nConsola: ${env.BUILD_URL ? env.BUILD_URL + 'console' : '-'}\n\nPara volver a una version anterior, ejecuta el job con ROLLBACK_TAG=vX.Y.Z y ROLLBACK_ENV."
                )
            }
        }
        always {
            archiveArtifacts artifacts: 'services/*/build/reports/tests/test/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'deploy-*.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'approval-prod-rollback.txt,.local-evidence/hu-27/**', allowEmptyArchive: true
        }
    }
}

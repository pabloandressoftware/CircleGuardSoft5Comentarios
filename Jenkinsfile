@Library('circleguard') _

// =============================================================================
// CircleGuard · Pipeline (job multibranch de Jenkins)
//
// HU-08 (rol C): disparo por push y deteccion de modulos modificados.
// HU-09 (rol D): calidad (JaCoCo, SonarQube, quality gate bloqueante) y publicacion.
// HU-10 (rol C): despliegue en dev, E2E, promocion de la misma etiqueta a stage
//                y aprobacion manual para prod.
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
def changed = []

// HU-11: aviso por correo. Un fallo al enviar nunca cambia el resultado del build.
def notifyByMail(String subject, String body) {
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
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        // Holgado: incluye la espera de la aprobacion de prod.
        timeout(time: 180, unit: 'MINUTES')
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

        // HU-09: compilacion, pruebas, JaCoCo, SonarQube y quality gate bloqueante.
        // Corre cuando algun modulo cambio y siempre en tags de release.
        stage('Calidad de servicios') {
            when {
                anyOf {
                    expression { !changed.isEmpty() }
                    buildingTag()
                }
            }
            steps {
                script { quality.call() }
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
                    }
                    String host = deploy.call(params.ROLLBACK_ENV, imageTag: tag)
                    smoke.call(host)
                    writeFile file: 'deploy-rollback.txt',
                        text: "env=${params.ROLLBACK_ENV}\ntag=${tag}\nhost=${host}\nbuild=${env.BUILD_URL}\n"
                    notifyByMail(
                        "[CircleGuard] ROLLBACK ${params.ROLLBACK_ENV} a ${tag}",
                        "Se redesplego ${tag} en ${params.ROLLBACK_ENV} (${host}) y paso la prueba de humo.\nBuild: ${env.BUILD_URL}"
                    )
                }
            }
        }

        stage('Publicar imagenes') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                }
            }
            steps {
                script { env.IMAGE_TAG = publish.call() }
            }
        }

        // HU-10: dev recibe la etiqueta recien publicada y debe superar el humo.
        stage('Desplegar dev') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { env.DEV_HOST = deploy.call('dev') }
            }
        }

        stage('Humo dev') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
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
                    expression { !params.SKIP_DEPLOY }
                    anyOf {
                        allOf {
                            buildingTag()
                            tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                        }
                        allOf {
                            branch 'dev'
                            expression { params.RUN_E2E_DEV }
                        }
                    }
                }
            }
            steps {
                script { e2e.call(host: env.DEV_HOST, environment: 'dev') }
            }
        }

        // Promocion: MISMA etiqueta ya validada en dev, sin reconstruir imagenes.
        stage('Desplegar stage') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { env.STAGE_HOST = deploy.call('stage') }
            }
        }

        stage('Humo stage') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { smoke.call(env.STAGE_HOST) }
            }
        }

        // HU-10: prod exige aprobacion de un usuario restringido y deja registro.
        stage('Aprobacion prod') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
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

        stage('Desplegar prod') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { env.PROD_HOST = deploy.call('prod') }
            }
        }

        stage('Humo prod') {
            when {
                allOf {
                    buildingTag()
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                    expression { !params.SKIP_DEPLOY }
                }
            }
            steps {
                script { smoke.call(env.PROD_HOST) }
            }
        }
    }

    post {
        failure {
            script {
                notifyByMail(
                    "[CircleGuard] FALLO ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    "El pipeline fallo.\n\nRama/tag: ${env.BRANCH_NAME ?: env.TAG_NAME}\nEtiqueta: ${env.IMAGE_TAG ?: '-'}\nBuild: ${env.BUILD_URL}\nConsola: ${env.BUILD_URL}console\n\nPara volver a una version anterior, ejecuta el job con ROLLBACK_TAG=vX.Y.Z y ROLLBACK_ENV."
                )
            }
        }
        always {
            archiveArtifacts artifacts: 'services/*/build/reports/tests/test/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'deploy-*.txt', allowEmptyArchive: true
        }
    }
}

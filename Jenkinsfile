@Library('circleguard') _

// =============================================================================
// CircleGuard · Pipeline (job multibranch de Jenkins)
//
// HU-08 (rol C): disparo por push y deteccion de modulos modificados.
// HU-09 (rol D): calidad (JaCoCo, SonarQube, quality gate bloqueante) y publicacion.
// Despliegue y promocion entre ambientes: HU-10 (rol C), al final del archivo.
//
// Los pasos de HU-09 viven en jenkins/vars/ y se cargan con "load" desde el mismo
// commit del build, asi un PR prueba su propia version de los pasos. La deteccion
// de modulos (circleGuardChanges) llega por la Shared Library 'circleguard'.
// =============================================================================
def quality
def publish
def changed = []

pipeline {
    agent { label 'docker' }

    triggers { githubPush() }

    parameters {
        booleanParam(
            name: 'BUILD_ALL',
            defaultValue: false,
            description: 'Construir los ocho servicios aunque git diff no detecte cambios.'
        )
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Preparar') {
            steps {
                script {
                    sh 'git fetch --no-tags --prune origin +refs/heads/*:refs/remotes/origin/*'
                    quality = load 'jenkins/vars/circleGuardQuality.groovy'
                    publish = load 'jenkins/vars/circleGuardPublish.groovy'
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
            steps {
                dir('mobile') {
                    sh 'npm ci --no-audit --no-fund'
                    sh 'npx jest --ci'
                }
            }
        }

        // HU-09: la publicacion oficial solo ocurre en builds de un tag de release
        // (vX.Y.Z), que Release Please crea desde main. Ramas y PR nunca publican.
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

        // HU-10 (rol C): desplegar en dev, E2E (circleGuardE2E), promover el mismo
        // IMAGE_TAG a stage y aprobar prod. Ver docs/sprint-1-persona-d.md, seccion
        // "Despliegue de un ambiente", para los comandos del Compose de HU-07.
    }

    post {
        always {
            archiveArtifacts artifacts: 'services/*/build/reports/tests/test/**', allowEmptyArchive: true
        }
    }
}

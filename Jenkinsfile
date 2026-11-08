// =============================================================================
// CircleGuard · Pipeline (job multibranch de Jenkins)
//
// Etapas de calidad y publicacion: HU-09 (rol D).
// Despliegue y promocion entre ambientes: HU-10 (rol C), al final del archivo.
//
// Los pasos viven en jenkins/vars/ con la convencion de Shared Library. Se cargan
// con "load" desde el mismo commit del build, asi un PR prueba su propia version
// de los pasos sin configurar nada mas en Jenkins.
// =============================================================================
def quality
def publish

pipeline {
    agent { label 'docker' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
    }

    stages {
        stage('Preparar') {
            steps {
                script {
                    quality = load 'jenkins/vars/circleGuardQuality.groovy'
                    publish = load 'jenkins/vars/circleGuardPublish.groovy'
                }
            }
        }

        // HU-09: compilacion, pruebas, JaCoCo, SonarQube y quality gate bloqueante.
        // Corre en TODOS los builds: ramas, PR y tags.
        stage('Calidad de servicios') {
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

/**
 * HU-09 · Publicacion OFICIAL de las 9 imagenes en Nexus.
 *
 *   circleGuardPublish()                                   // credencial nexus-registry
 *   circleGuardPublish(credentialsId: 'nexus-registry', registry: '127.0.0.1:5000')
 *
 * Solo se llama desde builds de un tag de release (ver Jenkinsfile). Release Please
 * crea esos tags al fusionar el PR de release en main, asi que la publicacion
 * oficial queda limitada a main. La version sale de scripts/release-image-tag.mjs,
 * que falla si el commit no coincide con version.txt y con un tag vX.Y.Z publicado.
 */
def call(Map args = [:]) {
    String credentialsId = args.get('credentialsId', 'nexus-registry')
    String registry = args.get('registry', '127.0.0.1:5000')

    String imageTag = sh(script: 'node scripts/release-image-tag.mjs', returnStdout: true).trim()
    withCredentials([usernamePassword(credentialsId: credentialsId,
                                      usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
        withEnv(["REGISTRY=${registry}", "IMAGE_TAG=${imageTag}"]) {
            sh 'printf "%s" "$NX_PASS" | docker login "$REGISTRY" -u "$NX_USER" --password-stdin'
            // bash con pipefail: si falla un push, el paso falla aunque la salida pase por tee
            sh '''#!/bin/bash
                set -eo pipefail
                bash scripts/package-images.sh "$IMAGE_TAG" --push | tee publish.log
                tail -n 9 publish.log > published-images.txt
            '''
        }
    }
    archiveArtifacts artifacts: 'published-images.txt'
    echo "Imagenes publicadas con la etiqueta ${imageTag}"
    return imageTag
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

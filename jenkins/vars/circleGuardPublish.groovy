/**
 * HU-09 �� Construccion y publicacion OFICIAL de las 9 imagenes en Nexus.
 *
 *   circleGuardPublish.call()      // construye localmente (sin push) y devuelve la etiqueta
 *   circleGuardPublish.push()      // publica las imagenes ya construidas y escaneadas
 *
 * Solo se llama desde builds de un tag de release (ver Jenkinsfile). Release Please
 * crea esos tags al fusionar el PR de release en main, asi que la publicacion
 * oficial queda limitada a main. La version sale de scripts/release-image-tag.mjs,
 * que falla si el commit no coincide con version.txt y con un tag vX.Y.Z publicado.
 *
 * HU-20: separar build y push permite que Trivy bloquee ANTES de subir una
 * imagen inmutable (Nexus usa writePolicy ALLOW_ONCE).
 */
def call(Map args = [:]) {
    String credentialsId = args.get('credentialsId', 'nexus-registry')
    String registry = args.get('registry', env.REGISTRY ?: '127.0.0.1:5000')

    String imageTag = sh(script: '''
        git merge-base --is-ancestor HEAD refs/remotes/origin/main || {
            echo 'El commit de release debe estar integrado en main.' >&2
            exit 1
        }
        node scripts/release-image-tag.mjs "$TAG_NAME"
    ''', returnStdout: true).trim()
    withEnv(["RELEASE_NOTES_TAG=${imageTag}"]) {
        sh 'node scripts/check-release-notes.mjs "$RELEASE_NOTES_TAG"'
    }
    archiveArtifacts artifacts: 'release-notes.md'
    withCredentials([usernamePassword(credentialsId: credentialsId,
                                      usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
        withEnv(["REGISTRY=${registry}", "IMAGE_TAG=${imageTag}"]) {
            sh 'printf "%s" "$NX_PASS" | docker login "$REGISTRY" -u "$NX_USER" --password-stdin'
            // Construye sin publicar: el escaneo de imagenes ocurre antes del push.
            sh '''#!/bin/bash
                set -eo pipefail
                bash scripts/package-images.sh "$IMAGE_TAG" | tee build-images.log
                tail -n 9 build-images.log > built-images.txt
            '''
        }
    }
    archiveArtifacts artifacts: 'built-images.txt'
    echo "Imagenes construidas con la etiqueta ${imageTag} (aun sin publicar)"
    return imageTag
}

def push(Map args = [:]) {
    String credentialsId = args.get('credentialsId', 'nexus-registry')
    String registry = args.get('registry', env.REGISTRY ?: '127.0.0.1:5000')
    withCredentials([usernamePassword(credentialsId: credentialsId,
                                      usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
        withEnv(["REGISTRY=${registry}"]) {
            sh 'printf "%s" "$NX_PASS" | docker login "$REGISTRY" -u "$NX_USER" --password-stdin'
            sh '''#!/bin/bash
                set -eo pipefail
                while read -r image; do
                    [[ -n "$image" ]] && docker push "$image"
                done < built-images.txt
                tail -n 9 built-images.txt > published-images.txt
            '''
        }
    }
    archiveArtifacts artifacts: 'published-images.txt'
    echo 'Imagenes publicadas en el registro'
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

def call(Map config = [:]) {
    def buildAll = params.BUILD_ALL ?: false
    def gradleCommand = config.gradleCommand ?: 'gradle'
    def gradleArgs = config.gradleArgs ?: '--no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs=-Xmx512m'

    stage('Checkout') {
        checkout scm
        sh 'git fetch --no-tags --prune origin +refs/heads/*:refs/remotes/origin/*'
    }

    def changed = []
    stage('Detectar módulos modificados') {
        changed = circleGuardChanges(buildAll: buildAll)
        writeFile file: 'changed-services.txt', text: (changed ? changed.join('\n') : '')
        archiveArtifacts artifacts: 'changed-services.txt', allowEmptyArchive: true
    }

    if (changed.isEmpty()) {
        echo 'Ningún módulo de backend cambió; se omite la compilación.'
        return
    }

    stage('Compilar y probar módulos') {
        def maxParallel = (config.maxParallel ?: 2) as int
        for (batch in changed.collate(maxParallel)) {
            def branches = [:]
            for (name in batch) {
                branches[name] = {
                    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                        stage(name) {
                            sh "${gradleCommand} ${gradleArgs} :services:${name}:build"
                        }
                    }
                }
            }
            parallel branches
        }
    }

    junit allowEmptyResults: true, testResults: 'services/*/build/test-results/test/*.xml'
}

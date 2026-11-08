def call(Map config = [:]) {
    def services = circleGuardServices()
    def allNames = services*.name

    if (config.buildAll) {
        echo 'BUILD_ALL activo: se construirán los ocho servicios.'
        return allNames
    }

    def base = resolveBase(config)
    if (!base) {
        echo 'No hay commit base comparable (primer build): se construirán todos los servicios.'
        return allNames
    }

    if (!commitExists(base)) {
        echo "El commit base ${base} no está disponible: se construirán todos los servicios."
        return allNames
    }

    def diff = sh(
        script: "git diff --name-only ${base}...HEAD",
        returnStdout: true
    ).trim()
    if (!diff) {
        echo "Sin cambios respecto a ${base}: no hay módulos por construir."
        return []
    }

    def paths = diff.readLines()
    def changed = allNames.findAll { name ->
        paths.any { it.startsWith("services/${name}/") }
    }

    def rootChanged = paths.any { path ->
        path == 'Jenkinsfile' ||
        path == 'build.gradle.kts' ||
        path == 'settings.gradle.kts' ||
        path == 'gradle.properties' ||
        path.startsWith('gradle/') ||
        path == 'gradlew' ||
        path == 'gradlew.bat' ||
        path.startsWith('jenkins/')
    }

    if (rootChanged) {
        echo 'Cambio en la raíz o en el pipeline: se construirán todos los servicios.'
        return allNames
    }

    echo "Servicios modificados: ${changed ? changed.join(', ') : 'ninguno'}"
    return changed
}

private String resolveBase(Map config) {
    if (config.baseCommit) {
        return config.baseCommit
    }
    if (env.CHANGE_TARGET) {
        return "origin/${env.CHANGE_TARGET}"
    }
    if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
        return env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
    }
    if (env.GIT_PREVIOUS_COMMIT) {
        return env.GIT_PREVIOUS_COMMIT
    }

    def baseBranch = config.baseBranch ?: 'dev'
    def remoteBase = "origin/${baseBranch}"
    if (env.BRANCH_NAME && env.BRANCH_NAME != baseBranch && commitExists(remoteBase)) {
        return remoteBase
    }
    return null
}

private boolean commitExists(String ref) {
    return sh(
        script: "git rev-parse --verify --quiet '${ref}^{commit}' >/dev/null",
        returnStatus: true
    ) == 0
}

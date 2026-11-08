@Library('circleguard') _

properties([
    disableConcurrentBuilds(),
    pipelineTriggers([githubPush()]),
    parameters([
        booleanParam(
            name: 'BUILD_ALL',
            defaultValue: false,
            description: 'Construir los ocho servicios aunque git diff no detecte cambios.'
        )
    ])
])

timestamps {
    timeout(time: 30, unit: 'MINUTES') {
        node(label: 'docker') {
            circleGuardPipeline()
        }
    }
}

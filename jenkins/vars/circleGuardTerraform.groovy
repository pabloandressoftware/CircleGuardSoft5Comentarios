/**
 * HU-19 · Terraform dentro del pipeline.
 *
 *   circleGuardTerraform.available()
 *   circleGuardTerraform.plan('dev')
 *   circleGuardTerraform.plan('stage')
 *   circleGuardTerraform.plan('prod')
 *   circleGuardTerraform.apply('dev')
 *
 * HU-17 es la duena del codigo en infra/terraform. Este paso solo asume el
 * contrato documentado en docs/hu-17-terraform-contrato.md:
 *
 *   infra/terraform/envs/<dev|stage|prod>/
 *     *.tf, terraform.tfvars, backend.hcl (opcional)
 *
 * Si la carpeta del ambiente no existe todavia, el paso no rompe el build:
 * deja un aviso claro para que HU-19 no bloquee el resto del repositorio.
 * Las credenciales de AWS llegan por la credencial de Jenkins `aws-terraform`
 * (username = access key, password = secret key); nunca se imprimen.
 */

def available() {
    return fileExists('infra/terraform')
}

def plan(String environment, Map args = [:]) {
    String dir = envDir(environment)
    if (!fileExists(dir)) {
        echo "Terraform: ${dir} no existe todavia (pendiente HU-17); se omite el plan de ${environment}."
        return false
    }

    String region = args.get('region', env.AWS_REGION ?: 'us-east-2')
    String credentialsId = args.get('credentialsId', 'aws-terraform')

    withAws(credentialsId, region) {
        dir(dir) {
            sh '''#!/bin/bash
                set -euo pipefail
                terraform fmt -check -recursive ../..
                if [[ -f backend.hcl ]]; then
                    terraform init -input=false -backend-config=backend.hcl
                else
                    terraform init -input=false
                fi
                terraform validate
                terraform plan -input=false -lock-timeout=5m -out=tfplan
                terraform show -no-color tfplan > terraform-plan.txt
            '''
        }
    }
    archiveArtifacts artifacts: "${dir}/terraform-plan.txt", allowEmptyArchive: true
    echo "Terraform plan de ${environment} listo."
    return true
}

def apply(String environment, Map args = [:]) {
    String dir = envDir(environment)
    if (!fileExists("${dir}/tfplan")) {
        echo "Terraform: no hay plan guardado para ${environment} (pendiente HU-17 o plan omitido); se omite el apply."
        return false
    }

    String region = args.get('region', env.AWS_REGION ?: 'us-east-2')
    String credentialsId = args.get('credentialsId', 'aws-terraform')

    try {
        withAws(credentialsId, region) {
            dir(dir) {
                sh '''#!/bin/bash
                    set -euo pipefail
                    terraform apply -input=false -lock-timeout=5m -auto-approve tfplan
                    # HU-17: exit 2 (cambios) o 1 (error) bloquea el despliegue.
                    terraform plan -input=false -lock-timeout=5m -detailed-exitcode -no-color \
                        > terraform-no-changes.txt 2>&1
                '''
            }
        }
    } finally {
        archiveArtifacts artifacts: "${dir}/terraform-no-changes.txt", allowEmptyArchive: true
    }
    echo "Terraform apply de ${environment} completado."
    return true
}

private String envDir(String environment) {
    if (!(environment in ['dev', 'stage', 'prod'])) {
        error("Ambiente Terraform invalido: ${environment}")
    }
    return "infra/terraform/envs/${environment}"
}

private void withAws(String credentialsId, String region, Closure body) {
    withCredentials([usernamePassword(credentialsId: credentialsId,
                                      usernameVariable: 'AWS_ACCESS_KEY_ID',
                                      passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
        withEnv(["AWS_REGION=${region}", "AWS_DEFAULT_REGION=${region}", 'AWS_PAGER=']) {
            body()
        }
    }
}

// Permite cargar este paso con "load" desde el Jenkinsfile
return this

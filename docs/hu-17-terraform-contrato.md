# Contrato de Terraform para HU-17

Este documento es para quien implemente **HU-17 (Terraform)**. HU-19 ya dejó el
pipeline listo para ejecutar `terraform plan` y `terraform apply`, pero **no hay
archivos `.tf` en el repositorio todavía**. Si HU-17 no respeta esta estructura,
sus etapas de Terraform se omitirán o fallarán en Jenkins.

## Qué espera el pipeline

La etapa `Terraform plan` (HU-19) corre cuando un PR o la rama `dev` modifican
`infra/`, y en todos los tags `vX.Y.Z`. La etapa `Terraform apply <ambiente>`
corre en tags, antes del despliegue Helm de ese ambiente; el de `prod` ocurre
después de la aprobación del usuario `aprobador`.

El paso `jenkins/vars/circleGuardTerraform.groovy` ejecuta, dentro de:

```
infra/terraform/envs/<dev|stage|prod>/
```

```bash
terraform fmt -check -recursive ..
terraform init -input=false -backend-config=backend.hcl   # si backend.hcl existe
terraform validate
terraform plan -input=false -lock-timeout=5m -out=tfplan
terraform show -no-color tfplan > terraform-plan.txt
```

y, en el apply:

```bash
terraform apply -input=false -lock-timeout=5m -auto-approve tfplan
```

## Estructura mínima requerida

```
infra/terraform/
├── modules/
│   ├── red/                 # VPC, subredes, IGW, security groups
│   ├── computo/             # EC2 k3s (o EKS, si se decide después)
│   ├── registro/            # ECR o integración con Nexus
│   └── observabilidad/      # almacenamiento/logs, si aplica
└── envs/
    ├── dev/
    │   ├── main.tf
    │   ├── providers.tf
    │   ├── variables.tf
    │   ├── terraform.tfvars
    │   ├── backend.hcl      # backend remoto de este ambiente
    │   └── outputs.tf
    ├── stage/  (misma estructura)
    └── prod/   (misma estructura)
```

Cada carpeta de ambiente debe ser un **root module independiente**, no una copia
del código: reutiliza los módulos de `modules/` y cambia solo variables.

## Backend remoto y bloqueo

Para cumplir E2.1:

- Bucket S3 versionado y cifrado para el estado.
- Tabla DynamoDB con `LockID` (String) para el bloqueo.
- Una clave de estado por ambiente, por ejemplo:
  - `circleguard/dev/terraform.tfstate`
  - `circleguard/stage/terraform.tfstate`
  - `circleguard/prod/terraform.tfstate`

Ejemplo de `envs/dev/backend.hcl` (sin secretos):

```hcl
bucket         = "circleguard-tfstate-<cuenta>-<region>"
key            = "circleguard/dev/terraform.tfstate"
region         = "us-east-2"
dynamodb_table = "circleguard-tf-locks"
encrypt        = true
```

El bucket y la tabla se crean una vez (módulo de bootstrap o pasos manuales
documentados). No se versionan estados, planes ni credenciales.

## Credenciales y variables

- Jenkins usa la credencial **`aws-terraform`** (Username with password):
  - username = `AWS_ACCESS_KEY_ID`
  - password = `AWS_SECRET_ACCESS_KEY`
- La región se toma de la variable global `AWS_REGION`; si no existe, el paso
  usa `us-east-2`.
- No pongas secretos en `terraform.tfvars`. Usa variables de entorno,
  `data` sources o SSM/Secrets Manager.
- Si se necesita `AWS_SESSION_TOKEN`, avísalo para ampliar el paso; hoy solo
  cubre access key + secret key.

Todos los comandos deben ser **no interactivos**: el pipeline no tiene TTY.

## Clúster k3s y kubeconfig

El pipeline no crea el clúster; HU-17 debe dejarlo operativo y entregar un
kubeconfig por ambiente al operador de Jenkins:

```
Jenkins → Manage Jenkins → Credentials → Secret file
  kubeconfig-dev
  kubeconfig-stage
  kubeconfig-prod
```

Recomendaciones:

- Crear un clúster k3s por ambiente (o uno por ambiente lógico, según costo).
- El usuario del kubeconfig debe poder crear namespaces, secrets, deployments,
  services, ingresses, HPAs y ejecutar `helm upgrade --install` con `--wait`.
- No subir kubeconfigs al repositorio.
- Si el k3s es privado, Jenkins debe alcanzar el API server (VPN, bastión o
  security group).

## Registro de imágenes

`deploy/k8s/desplegar.sh` usa `REGISTRY` (variable del archivo
`circleguard-env-<ambiente>`) y arma:

```
<REGISTRY>/circleguard/circleguard-<servicio>:<vX.Y.Z>
<REGISTRY>/circleguard/web:<vX.Y.Z>
```

Opciones compatibles:

1. **Nexus actual en el host de CI**: el k3s debe alcanzar el puerto 5000 del
   host de Jenkins. Si Nexus es HTTP, configura `registries.yaml` en k3s con
   `insecure_skip_verify` o el endpoint HTTP correspondiente.
2. **ECR**: el módulo de registro de HU-17 puede crear el repositorio; el k3s
   necesita credenciales de pull (imagePullSecret) y el pipeline debe poder
   autenticarse. Documenta el mecanismo elegido.

En ambos casos, actualiza `REGISTRY` en los `.env` de Jenkins.

## Ingress, DNS y TLS

- HU-19 reutiliza `SITE_ADDRESS` de `circleguard-env-<ambiente>` para las
  pruebas de humo y E2E.
- Para que el humo funcione, el Ingress debe estar publicado y responder HTTPS.
  El controlador de Ingress, DNS y TLS corresponden a HU-17/HU-21.
- Si aún no hay TLS, documenta cómo se valida el despliegue (por ejemplo,
  `kubectl port-forward`) para no romper el pipeline.

## Qué NO debe hacer HU-17

- No crear namespaces, Secrets de aplicación ni releases de Helm: eso lo hace
  `deploy/k8s/` (HU-18/HU-19).
- No desplegar los servicios: el pipeline los despliega con Helm después del
  apply.
- No versionar `*.tfstate`, `tfplan`, `.terraform/` ni kubeconfigs.

## Checklist de compatibilidad con HU-19

- [ ] Existe `infra/terraform/envs/dev|stage|prod/` con un root module válido.
- [ ] `terraform fmt -check -recursive` pasa en `infra/terraform`.
- [ ] `terraform validate` pasa en los tres ambientes.
- [ ] `terraform plan` funciona con la credencial `aws-terraform` y sin TTY.
- [ ] `backend.hcl` usa S3 + DynamoDB y una clave distinta por ambiente.
- [ ] `terraform apply` es idempotente y no pide confirmación.
- [ ] El kubeconfig de cada ambiente está cargado como credencial de Jenkins.
- [ ] `REGISTRY` en `circleguard-env-*` es alcanzable desde k3s.
- [ ] `SITE_ADDRESS` apunta al Ingress de cada ambiente.
- [ ] Trivy (`security.filesystem()`) no reporta hallazgos CRITICAL en el IaC.

## Otros pendientes entre HU

El estado consolidado de HU-18, HU-19, HU-20, HU-21 y HU-24, el orden de
integración y los riesgos de fusión están en
[pendientes-integracion.md](pendientes-integracion.md).

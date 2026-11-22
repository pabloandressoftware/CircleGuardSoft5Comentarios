# Pendientes de integración entre HU-17, HU-18, HU-19, HU-20, HU-21 y HU-24

Este documento consolida lo que falta para llevar a un ambiente real las HU ya
trabajadas. La rama base es `feat/hu-19-pipeline-terraform-helm`, que parte de
`dev`. Las HU-21 y HU-24 están en ramas separadas y todavía no se han fusionado.

Fecha de este estado: 22 de noviembre de 2026.

## 1. Estado de las ramas

| HU | Rama | Estado | Pendiente principal |
| --- | --- | --- | --- |
| HU-18 | `feat/hu-18-kubernetes-helm` | Fusionada en `dev` (PR #44) | Validación en clúster real, Ingress, web y middleware persistente |
| HU-19 | `feat/hu-19-pipeline-terraform-helm` | Implementada y pusheada, sin PR | Terraform de HU-17, credenciales, clúster y despliegue real |
| HU-20 | `feat/hu-20-seguridad-pipeline` | Fusionada en `dev` (PR #43) | Decisión sobre CVEs de Spring Boot y prueba real del umbral |
| HU-21 | `feat/hu-21-seguridad-ingress` | Implementada y pusheada, sin PR | Credenciales, cert-manager y validación en clúster |
| HU-24 | `feat/hu-24-observabilidad` | Implementada y pusheada, sin PR | Instalar observabilidad y disparar las alertas reales |

## 2. Pendientes por HU

### HU-18 · Servicios, web y middleware en Kubernetes

- Probar el cliente `web` dentro del clúster con una imagen posterior a HU-20.
- Instalar un controlador de Ingress real; en kind solo se creó el objeto Ingress.
- Decidir persistencia/alta disponibilidad de Kafka y PostgreSQL.
- Ejecutar las pruebas funcionales entre servicios
  (`form` → Kafka → `promotion` → `notification`).
- Reconstruir las imágenes posteriores a Actuator: las `v1.0.0` no tienen
  `/actuator/health` y fallan las sondas.
- Confirmar el host, registro e Ingress de cada ambiente.

Referencia: `docs/kubernetes.md`.

### HU-19 · Terraform y Helm en Jenkins

- Crear el PR de `feat/hu-19-pipeline-terraform-helm` a `dev` y fusionarlo.
- Reconstruir la imagen de Jenkins para tener `terraform`, `helm`, `kubectl` y
  AWS CLI (`infra/ci/jenkins/Dockerfile`).
- Esperar el código de HU-17; mientras no exista `infra/terraform/envs/<ambiente>`,
  `Terraform plan` se omite con un aviso.
- Cargar `aws-terraform` y `kubeconfig-dev/stage/prod` en Jenkins.
- Hacer alcanzable `REGISTRY` desde k3s y actualizar los `circleguard-env-*`.
- Configurar `SITE_ADDRESS` con el Ingress y HTTPS reales.
- Resolver el bloqueo de E2E con Helm: `circleGuardE2E.groovy` espera Redis por
  SSH en el host; con Helm hay que usar `kubectl port-forward` o exponer Redis.
- Ejecutar el primer tag con `DEPLOY_TARGET=helm` y `DEPLOY_MIDDLEWARE=true`
  para instalar el middleware en el clúster.

Referencia: `docs/hu-19-pipeline.md` y `docs/hu-17-terraform-contrato.md`.

### HU-20 · Vulnerabilidades y secretos

- Decidir entre subir Spring Boot a una versión 3.5.x parcheada o aceptar y
  documentar los CRITICAL de `tomcat-embed-core`, `netty-handler` y
  `spring-webmvc` de Spring Boot 3.2.4.
- Ejecutar el pipeline con un CRITICAL real para demostrar que el umbral bloquea.
- Rotar los secretos que gitleaks encontró en el historial.
- Confirmar que las imágenes Java nuevas pasan Trivy antes de un tag.

Referencia: `docs/seguridad.md`.

### HU-21 · RBAC, TLS y autenticación de rutas

- Crear el PR de `feat/hu-21-seguridad-ingress` a `dev` y fusionarlo.
- Agregar `CI_DEVELOPER_PASSWORD` al `ci.env` y reconstruir Jenkins.
- Regenerar o actualizar los `circleguard-env-*` con `INTERNAL_API_KEY`.
- Instalar cert-manager (`deploy/k8s/cert-manager.sh`) y verificar el Ingress con
  `deploy/k8s/verificar-tls.sh <host>` o `curl -k`.
- Validar los roles `admin`, `developer` y `approver` en Jenkins.
- Probar en clúster que:
  - `/identities/map` sin JWT devuelve 401;
  - `/admin/**` sin `dashboard:view` devuelve 403;
  - auth-service no publica `/api/v1/users` en el Ingress;
  - las ServiceAccounts no montan token.
- Revisar el orden con HU-19: si HU-21 introduce un kubeconfig limitado, el
  pipeline no podrá crear namespaces con `kubectl apply -f deploy/k8s/namespaces.yaml`.

Referencia:
<https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/blob/feat/hu-21-seguridad-ingress/docs/seguridad-k8s.md>

### HU-24 · Observabilidad

- Crear el PR de `feat/hu-24-observabilidad` a `dev` y fusionarlo.
- Ejecutar `observability/instalar.sh` con `CI_DISCORD_WEBHOOK` (webhook de
  Spidey) y `GRAFANA_ADMIN_PASSWORD`.
- Verificar que el ServiceMonitor descubre los 8 servicios Java de dev/stage/prod
  (24 targets).
- Abrir Grafana por `kubectl port-forward` y revisar los tableros técnico y de
  negocio.
- Ejecutar `observability/evidencias/disparar-alertas.sh` y guardar la evidencia
  de `ServiceDown`, `High5xxRate` y `GateDenialSpike` en
  `observability/evidencias/<fecha>/`.
- Confirmar que Spidey recibe las alertas en Discord.

Referencia:
<https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/blob/feat/hu-24-observabilidad/docs/observabilidad.md>

## 3. Lo que debe entregar HU-17

El contrato detallado está en `docs/hu-17-terraform-contrato.md`. En resumen:

### 3.1 Estructura

```
infra/terraform/
├── modules/
│   ├── red/                 # VPC, subredes, IGW, security groups
│   ├── computo/             # EC2 k3s (o EKS)
│   ├── registro/            # ECR o integración con Nexus
│   └── observabilidad/      # almacenamiento/logs, si aplica
└── envs/
    ├── dev/
    │   ├── main.tf
    │   ├── providers.tf
    │   ├── variables.tf
    │   ├── terraform.tfvars
    │   ├── backend.hcl
    │   └── outputs.tf
    ├── stage/
    └── prod/
```

Cada ambiente debe ser un root module independiente que reutilice `modules/`.

### 3.2 Backend remoto

- Bucket S3 versionado y cifrado.
- Tabla DynamoDB con `LockID` para bloqueo.
- Una clave de estado por ambiente:
  - `circleguard/dev/terraform.tfstate`
  - `circleguard/stage/terraform.tfstate`
  - `circleguard/prod/terraform.tfstate`

### 3.3 Clúster y kubeconfigs

- k3s por ambiente, o un clúster con tres namespaces.
- Entregar un kubeconfig por ambiente y cargarlo en Jenkins como
  `kubeconfig-dev`, `kubeconfig-stage` y `kubeconfig-prod`.
- El kubeconfig debe permitir crear namespaces, Secrets, Deployments, Services,
  Ingress, HPA y ejecutar `helm upgrade --install --wait`.
- Si se usa un permiso limitado, HU-19 debe dejar de crear namespaces y HU-17
  debe crearlos antes.

### 3.4 Registro

- `REGISTRY` debe ser alcanzable desde k3s.
- Si se usa Nexus HTTP, configurar `registries.yaml` en k3s.
- Si se usa ECR, entregar el mecanismo de autenticación para el pull.

### 3.5 Ingress, DNS y TLS

- Publicar Ingress, DNS y certificados para `SITE_ADDRESS` de cada ambiente.
- HU-21 agrega cert-manager y TLS; HU-17 debe dejar el controlador de Ingress y
  la red listos.
- Sin HTTPS, el humo y E2E de HU-19 fallarán.

### 3.6 Lo que HU-17 no debe hacer

- No crear Secrets de aplicación (los crea `crear-secretos.sh`).
- No desplegar releases Helm (los despliega HU-19).
- No versionar estados, planes, `.terraform/` ni kubeconfigs.
- No duplicar la configuración de cada ambiente copiando módulos.

## 4. Orden de integración recomendado

1. **HU-17:** fusionar Terraform, provisionar el clúster y entregar kubeconfigs.
2. **HU-19:** fusionar el pipeline, reconstruir Jenkins, cargar credenciales y
   desplegar el primer tag con Helm.
3. **HU-21:** fusionar RBAC/TLS, actualizar `INTERNAL_API_KEY`, instalar
   cert-manager y validar rutas.
4. **HU-24:** fusionar observabilidad, instalar kube-prometheus-stack y verificar
   24 targets.
5. Ejecutar las tres alertas de HU-24 y guardar la evidencia para HU-29.

## 5. Riesgos y conflictos entre ramas

- HU-19, HU-21 y HU-24 parten de `dev`; comparten cambios en `README.md` y
  `docs/kubernetes.md`, que pueden generar conflictos menores.
- HU-19 modifica `Jenkinsfile`; HU-21 toca `casc.yml`, `plugins.txt`, `compose.yml`
  y `ci.env.example`. El conflicto esperado es bajo, pero hay que fusionar en orden.
- HU-19 crea namespaces desde el pipeline; HU-21 puede introducir credenciales
  limitadas. Decidir si los namespaces los crea HU-17 o el pipeline.
- HU-24 necesita servicios desplegados para que el ServiceMonitor tenga targets.
- HU-21 introduce `INTERNAL_API_KEY`; los `.env` de HU-19 deben actualizarse antes
  de desplegar.
- El webhook de Spidey se reutiliza en Alertmanager, pero se guarda como Secret
  de Kubernetes; no debe quedar en Git.

## 6. Checklist final

- [ ] HU-17 fusionada y `terraform plan/apply` ejecutado en dev, stage y prod.
- [ ] `kubeconfig-*` y `aws-terraform` cargados en Jenkins.
- [ ] Imagen de Jenkins reconstruida con Terraform, Helm, kubectl y AWS CLI.
- [ ] Registro alcanzable desde k3s y `REGISTRY` actualizado.
- [ ] `SITE_ADDRESS` y TLS funcionando; humo y E2E en verde.
- [ ] HU-21 fusionada y validada (401/403/ServiceAccounts/TLS).
- [ ] HU-24 fusionada; 24 targets y dashboards visibles.
- [ ] Tres alertas críticas disparadas con evidencia y aviso a Spidey.
- [ ] `INTERNAL_API_KEY` y `CI_DEVELOPER_PASSWORD` cargados sin exponerlos en Git.

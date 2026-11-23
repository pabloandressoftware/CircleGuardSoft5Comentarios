# Pendientes de integración entre HU-17, HU-18, HU-19, HU-20, HU-21 y HU-24

Este documento consolida lo que falta para llevar a un ambiente real las HU ya
trabajadas. La rama base es `dev`: HU-19, HU-21 y HU-24 ya están fusionadas. El
código del pipeline quedó cerrado en `feat/hu-19-21-24-cierre-pipeline`; lo que
sigue es sobre todo operación y validación en un clúster real.

Fecha de este estado: 22 de noviembre de 2026.

## 1. Estado de las ramas

| HU | Rama | Estado | Pendiente principal |
| --- | --- | --- | --- |
| HU-18 | `feat/hu-18-kubernetes-helm` | Fusionada en `dev` (PR #44) | Validación en clúster real, Ingress, web y middleware persistente |
| HU-19 | `feat/hu-19-pipeline-terraform-helm` | Fusionada en `dev` | Credenciales, clúster y despliegue real |
| HU-20 | `feat/hu-20-seguridad-pipeline` | Fusionada en `dev` (PR #43) | Decisión sobre CVEs de Spring Boot y prueba real del umbral |
| HU-21 | `feat/hu-21-seguridad-ingress` | Fusionada en `dev` (PR #51) | Validación en clúster (401/403, TLS y ServiceAccounts) |
| HU-24 | `feat/hu-24-observabilidad` | Fusionada en `dev` (PR #51) | Instalar el stack y disparar las alertas reales |

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

### HU-17 · Terraform

- Código implementado en `hu-17`, creada desde HU-19; ver
  [runbook](hu-17-terraform.md) y [evidencias](evidencias/hu-17.md).
- Cuenta del operador sin créditos en Free plan: no se aprovisionó AWS.
- Completar cuenta, AMI, CIDR, Nexus y backend antes de plan/apply reales.
- Demostrar bloqueo DynamoDB y plan sin cambios después del apply.
- k3s usa Traefik: HU-21 debe usar ese IngressClass en cert-manager.

### HU-19 · Terraform y Helm en Jenkins

- Fusionada en `dev`. El E2E con Helm ya usa `kubectl port-forward` hacia
  `svc/redis` en `circleguard-dev`; falta validarlo contra el clúster real.
- Reconstruir la imagen de Jenkins para tener `terraform`, `helm`, `kubectl` y
  AWS CLI (`infra/ci/jenkins/Dockerfile`).
- Integrar `hu-17` después de HU-19. Su código ya existe; faltan la cuenta
  autorizada, la configuración real y las credenciales para ejecutar Terraform.
- Cargar `aws-terraform` y `kubeconfig-dev/stage/prod` en Jenkins.
- Hacer alcanzable `REGISTRY` desde k3s y actualizar los `circleguard-env-*`.
- Configurar `SITE_ADDRESS` con el Ingress y HTTPS reales.
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

- Fusionada en `dev` (PR #51).
- Agregar `CI_DEVELOPER_PASSWORD` al `ci.env` y reconstruir Jenkins.
- Regenerar o actualizar los `circleguard-env-*` con `INTERNAL_API_KEY`.
- El stage `TLS con cert-manager` instala cert-manager y el `ClusterIssuer` en
  cada tag con Helm; falta confirmar que el certificado se emite y validar el
  Ingress con `deploy/k8s/verificar-tls.sh <host>` o `curl -k`.
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

- Fusionada en `dev` (PR #51).
- El stage `Observabilidad` ejecuta `observability/instalar.sh` en cada tag con
  Helm; requiere las credenciales `discord-webhook` y `grafana-admin-password`.
- Verificar que el ServiceMonitor descubre los 8 servicios Java de dev/stage/prod
  (24 targets).
- Abrir Grafana por `kubectl port-forward` y revisar los tableros técnico y de
  negocio.
- Ejecutar manualmente `observability/evidencias/disparar-alertas.sh` y guardar
  la evidencia de `ServiceDown`, `High5xxRate` y `GateDenialSpike` en
  `observability/evidencias/<fecha>/`.
- Confirmar que Spidey recibe las alertas en Discord.

Referencia: [docs/observabilidad.md](observabilidad.md).

## 3. Lo que debe entregar HU-17

El contrato detallado está en `docs/hu-17-terraform-contrato.md`. En resumen:

### 3.1 Estructura

```
infra/terraform/
├── modules/
│   ├── network/             # VPC, subredes, IGW, security groups
│   ├── cluster/             # EC2 k3s
│   ├── registry/            # integración con Nexus
│   ├── storage/             # EBS cifrado para k3s/PVC
│   ├── observability/       # logs CloudWatch; dashboards en HU-24
│   └── environment/         # composición compartida de los cinco módulos
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

- La decisión para este proyecto es **un clúster con tres namespaces**
  (`circleguard-dev`, `circleguard-stage`, `circleguard-prod`).
- Entregar un kubeconfig por ambiente y cargarlo en Jenkins como
  `kubeconfig-dev`, `kubeconfig-stage` y `kubeconfig-prod`. Los stages de
  cert-manager y observabilidad usan `kubeconfig-dev`, que apunta al clúster
  compartido.
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

1. **HU-17:** provisionar el clúster y entregar kubeconfigs.
2. **HU-19:** reconstruir Jenkins, cargar credenciales y desplegar el primer tag
   con Helm.
3. **HU-21:** los stages `TLS con cert-manager` y `Observabilidad` corren
   automáticamente en el tag; validar rutas y certificados.
4. **HU-24:** verificar los 24 targets y los tableros en Grafana.
5. Ejecutar las tres alertas de HU-24 y guardar la evidencia para HU-29.

## 5. Riesgos y conflictos entre ramas

- HU-19, HU-21 y HU-24 ya están fusionadas en `dev`; los conflictos de
  `README.md` y `docs/kubernetes.md` quedaron resueltos.
- Se asume **un clúster con tres namespaces**; si se usan clústeres separados,
  los stages `TLS con cert-manager` y `Observabilidad` deben ejecutarse en cada
  uno (hoy usan `kubeconfig-dev`).
- HU-19 crea namespaces desde el pipeline; si HU-21 introduce credenciales
  limitadas, decidir si los namespaces los crea HU-17 o el pipeline.
- HU-24 necesita servicios desplegados para que el ServiceMonitor tenga targets
  y los stages automáticos alargan cada tag (`--wait` de Helm).
- HU-21 introduce `INTERNAL_API_KEY`; los `.env` de HU-19 deben actualizarse antes
  de desplegar.
- El webhook de Spidey se reutiliza en Alertmanager, pero se guarda como Secret
  de Kubernetes; no debe quedar en Git.

## 6. Checklist final

- [x] Código HU-17 con cinco módulos y composición compartida en su rama.

- [ ] HU-17 fusionada y `terraform plan/apply` ejecutado en dev, stage y prod.
- [ ] `kubeconfig-*` y `aws-terraform` cargados en Jenkins.
- [ ] Imagen de Jenkins reconstruida con Terraform, Helm, kubectl y AWS CLI.
- [ ] Registro alcanzable desde k3s y `REGISTRY` actualizado.
- [ ] `SITE_ADDRESS` y TLS funcionando; humo y E2E en verde.
- [x] HU-21 fusionada; falta validarla en clúster (401/403/ServiceAccounts/TLS).
- [x] HU-24 fusionada; falta verificar 24 targets y dashboards.
- [ ] Cert-manager y observabilidad instalados por el pipeline en un tag real.
- [ ] Tres alertas críticas disparadas con evidencia y aviso a Spidey.
- [ ] `INTERNAL_API_KEY`, `CI_DEVELOPER_PASSWORD` y `grafana-admin-password`
      cargados sin exponerlos en Git.

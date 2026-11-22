# HU-19 · Terraform y Helm en el pipeline de Jenkins

HU-19 conecta la infraestructura como código y Kubernetes al pipeline que ya
existía (HU-08/09/10/11/15/18/20). El despliegue oficial de dev, stage y prod
pasa a **Helm sobre k3s**; Docker Compose queda como respaldo para las VM
actuales.

## Flujo del pipeline

```
Preparar (detecta cambios en infra/)
  → Detectar módulos
  → Terraform plan (los 3 ambientes; PR con cambios en infra/ o tag)
  → Calidad → Seguridad → Web
  → [tag] Publicar imágenes → Escanear imágenes
  → [tag] Terraform apply dev → Desplegar dev → Humo → E2E
  → [tag] Terraform apply stage → Desplegar stage → Humo → ZAP
  → [tag] Aprobación prod (usuario aprobador)
  → [tag] Terraform apply prod → Desplegar prod → Humo
```

- En un **PR** solo se ejecuta `terraform plan`; nunca se aplica ni se despliega.
- En un **tag `vX.Y.Z`** se aplica la infraestructura y se promueve la **misma
  etiqueta** de imagen por los tres ambientes.
- `SKIP_DEPLOY=true` omite applies y despliegues, pero conserva el plan.
- Un **rollback** (`ROLLBACK_TAG` + `ROLLBACK_ENV`) usa el mismo destino de
  despliegue y pide aprobación si el ambiente es `prod`.
- Un fallo dispara los avisos de HU-11 (Discord y correo) desde `post.failure`.

## Parámetros nuevos

| Parámetro | Valor por defecto | Uso |
| --- | --- | --- |
| `DEPLOY_TARGET` | `helm` | `helm` despliega en k3s; `compose` conserva el flujo anterior por SSH. |
| `DEPLOY_MIDDLEWARE` | `false` | Despliega PostgreSQL, Redis, Kafka, Neo4j y OpenLDAP antes de los servicios. Solo para el primer despliegue de un cluster. |

## Credenciales de Jenkins

| ID | Tipo | Uso |
| --- | --- | --- |
| `kubeconfig-dev`, `kubeconfig-stage`, `kubeconfig-prod` | Secret file | Kubeconfig del clúster k3s de cada ambiente. |
| `aws-terraform` | Username with password | Access key y secret key de AWS para Terraform. |
| `circleguard-env-dev/stage/prod` | Secret file | `.env` del ambiente; aporta `REGISTRY` y `SITE_ADDRESS`. |
| `nexus-registry` | Username with password | Login al registro y creación del `imagePullSecret`. |

Las credenciales `circleguard-env-*` ya existían para HU-10. Solo deben
actualizarse si cambia el registro o el host del Ingress.

## Piezas nuevas

| Archivo | Responsabilidad |
| --- | --- |
| `jenkins/vars/circleGuardTerraform.groovy` | `plan` y `apply` por ambiente, con credenciales AWS y artefacto `terraform-plan.txt`. |
| `jenkins/vars/circleGuardHelm.groovy` | Namespace, secrets, `imagePullSecret`, Helm y espera de `rollout status`. |
| `deploy/k8s/desplegar.sh` | `helm upgrade --install` con registro configurable y `kubectl rollout status`. |
| `infra/ci/jenkins/Dockerfile` | Instala `terraform`, `helm`, `kubectl` y AWS CLI con versiones fijas. |
| `docs/hu-17-terraform-contrato.md` | Qué debe entregar HU-17 para que estas etapas funcionen. |

## Puesta en marcha (pasos manuales)

1. **Reconstruir la imagen de Jenkins** para obtener las herramientas nuevas:

   ```bash
   bash infra/bootstrap/bootstrap-ci.sh --local /ruta/segura/ci.env
   ```

2. **Provisionar k3s y kubeconfigs** (HU-17). Subir un kubeconfig por ambiente a
   Jenkins como `kubeconfig-dev`, `kubeconfig-stage` y `kubeconfig-prod`.

3. **Permitir el pull de imágenes** desde el registro:
   - Si es Nexus HTTP, configurar `registries.yaml` en k3s.
   - Actualizar `REGISTRY` en cada `circleguard-env-<ambiente>`.
   - Verificar que el k3s alcanza el registro.

4. **Ajustar `SITE_ADDRESS`** de cada `.env` al host del Ingress. Sin Ingress,
   TLS ni DNS (HU-17/HU-21), las etapas de humo y E2E fallarán.

5. **Cargar `aws-terraform`** cuando HU-17 agregue los `.tf`. Mientras no exista
   `infra/terraform/envs/<ambiente>`, las etapas de Terraform se omiten con un
   aviso y no bloquean el build.

6. **Primer despliegue de un cluster:** ejecutar el tag con
   `DEPLOY_MIDDLEWARE=true` para instalar el middleware. Los siguientes tags
   pueden dejarlo en `false`.

7. **Validar** con un build de tag y `SKIP_DEPLOY=true` para el resto del
   pipeline, y luego un tag real con `DEPLOY_TARGET=helm`.

## Evidencias que produce

- `terraform-plan.txt` por ambiente (artefacto de Jenkins).
- `deploy-helm-<ambiente>.txt` con etiqueta, host y build.
- `approval-prod.txt` con el usuario que aprobó prod (HU-10).
- Salida de `kubectl rollout status` en la consola del build.
- Avisos de fallo por Discord y correo.

## Limitaciones conocidas

- HU-17 no está implementada: no hay `.tf` en el repositorio. La etapa de
  Terraform está cableada pero se omite hasta que exista el código.
- El middleware se despliega solo con `DEPLOY_MIDDLEWARE=true`; no se reinstala
  en cada release.
- El humo/E2E siguen usando HTTPS contra `SITE_ADDRESS`; requieren Ingress, DNS
  y TLS operativos.
- El registro debe ser alcanzable por k3s. Si es Nexus HTTP, hace falta
  configuración adicional de containerd.

## Pendientes de integración

La lista completa de pendientes de HU-18, HU-19, HU-20, HU-21 y HU-24, junto al
contrato resumido de HU-17, está en
[pendientes-integracion.md](pendientes-integracion.md).

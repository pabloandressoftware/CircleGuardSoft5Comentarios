# HU-19 · Evidencia local de despliegue con Helm

Ejecutado el 23 de noviembre de 2026 en un clúster `kind` desechable
(`circleguard-evidencia`, Kubernetes 1.32.0) sobre Docker Desktop, con Helm
3.16.4. Las nueve imágenes se construyeron localmente con `v0.1.0` y se cargaron
en el nodo con `kind load docker-image`; no se publicaron en Nexus ni GitHub.

| Comprobación | Resultado |
| --- | --- |
| Middleware en `circleguard-dev` | PostgreSQL, Redis, Kafka, Neo4j y OpenLDAP `1/1 Running` |
| Releases Helm desplegados | 9/9 (`identity`, `auth`, `form`, `gateway`, `promotion`, `notification`, `file`, `dashboard`, `web`) |
| Pods de aplicación | 8 servicios Java y `web` en `1/1 Running` |
| Ingress | 8 objetos con `ingressClassName: traefik` y host `dev.circleguard.local` |
| Humo HTTPS | `/` → 200 y `/api/v1/health` → 401 (auth cerrado por defecto) |
| Etiqueta usada | `v0.1.0`, la misma en todos los releases |

Archivos: [pods](pods.txt), [ingress](ingress.txt) y [releases](releases.txt).

## Límites de esta evidencia

- No es un build de Jenkins: se invocó `deploy/k8s/desplegar.sh` directamente con
  `REGISTRY=localhost`. El tag real y la aprobación de prod siguen pendientes.
- `terraform plan/apply` no se ejecutó: `infra/terraform/envs/*/terraform.tfvars`
  y `backend.hcl` siguen siendo plantillas sin cuenta AWS.
- La suite E2E no se ejecutó porque el archivo `hosts` de Windows requiere
  permisos de administrador. El humo HTTPS sí se validó con `curl --resolve`.
- stage y prod no se desplegaron completos: Docker Desktop tiene 7.5 GB y el
  segundo ambiente agotó la memoria (`OOMKilled`/`Pending`). Ver HU-24.

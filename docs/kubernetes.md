# Kubernetes con Helm (HU-18)

Los 8 servicios Java y el cliente web se despliegan con **un solo chart genérico** y un archivo `values` por servicio. El middleware usa charts públicos de Helm.

```
deploy/
├── helm/
│   ├── circleguard-service/   # chart genérico: Deployment, Service, Ingress, HPA
│   ├── values/<servicio>.yaml # puerto, variables, secretos, rutas del Ingress
│   └── envs/<ambiente>.yaml   # host y CORS de dev, stage y prod
└── k8s/
    ├── namespaces.yaml        # circleguard-dev / -stage / -prod
    ├── crear-secretos.sh      # Secret circleguard-secrets desde variables de entorno
    ├── middleware.sh          # PostgreSQL, Redis, Kafka, Neo4j (Helm) y OpenLDAP
    ├── openldap.yaml
    └── desplegar.sh           # helm upgrade --install de los servicios
```

## Sondas y métricas

Actuator y Micrometer están en el build raíz (`build.gradle.kts`) para todos los servicios, y cada `application.yml` los configura:

- `/actuator/health/liveness` y `/actuator/health/readiness`: las sondas del Deployment.
- `/actuator/prometheus`: métricas para HU-24.
- El Ingress **solo publica rutas de la API**; `/actuator` queda dentro del cluster.
- El cliente web no usa Actuator: sus sondas piden `/`.

## Qué incluye cada ambiente

- **Namespace** por ambiente, con un Secret `circleguard-secrets` (10 claves) que crea `crear-secretos.sh`. Los valores vienen de Jenkins Credentials y no están en el repositorio.
- **Ingress** por servicio con los mismos prefijos que usa el proxy de Docker Compose (`deploy/Caddyfile`).
- **HPA** en `gateway-service` y `promotion-service`: 70 % de CPU, de 2 a 5 réplicas (valores propuestos, por confirmar).
- **Imagen inmutable:** la misma etiqueta SemVer (`vX.Y.Z`) en dev, stage y prod.
- Pods sin root (`runAsNonRoot`), sin escalada de privilegios y sin capacidades extra.

## Uso

```bash
kubectl apply -f deploy/k8s/namespaces.yaml

# 10 variables de entorno con los secretos (ver el encabezado del script)
bash deploy/k8s/crear-secretos.sh dev

bash deploy/k8s/middleware.sh dev
bash deploy/k8s/desplegar.sh dev v1.0.0
```

Cada servicio se instala con el nombre de su release igual a su host (`identity-service`, `auth-service`, …) y el middleware con los hosts `postgres`, `redis`, `kafka`, `neo4j` y `openldap`. Por eso las variables de entorno son las mismas que en Docker Compose.

## Prueba local con kind (16-nov-2026)

Se probó en un cluster kind de un nodo (v1.31) creado en la máquina de desarrollo:

| Qué | Resultado |
| --- | --- |
| `helm lint` de los 9 values (8 servicios y web) | Sin errores |
| Compilación de los 8 servicios con Actuator y pruebas de gateway e identity | Pasan |
| Namespaces y Secret `circleguard-secrets` con las 10 claves (`crear-secretos.sh`) | Creados |
| Redis desde el chart público de Helm, con la contraseña tomada del Secret | `1/1 Running` |
| `gateway-service` desplegado con el chart genérico (`desplegar.sh`) | `1/1 Running`, readiness y liveness en `UP` |
| `/actuator/prometheus` | 200 con métricas de la JVM |
| Ingress de gateway | Solo publica `/api/v1/gate` (no `/actuator`) |
| Pod | Sin escalada de privilegios y sin capacidades extra |
| `middleware.sh` en kind: PostgreSQL (chart Bitnami), Redis, Neo4j (chart oficial), OpenLDAP y Kafka | Los cinco `1/1 Running` (tras los ajustes de abajo) |
| Los 8 servicios Java con imágenes reconstruidas desde esta rama (`desplegar.sh`), contra PostgreSQL, Redis, Kafka, Neo4j y OpenLDAP del cluster | `1/1 Running` los 8; `/actuator/health/liveness`, `/readiness` y `/actuator/prometheus` responden 200 en todos. `promotion` conectó con Kafka |
| `middleware.sh` y `desplegar.sh` repetidos sobre un cluster ya desplegado | Idempotentes: todos los releases quedan `deployed` |
| Apagar la máquina y volver a encenderla (17-nov) | El cluster y los datos sobrevivieron; hubo que borrar 4 pods atascados (`failed to reserve container name`, problema de containerd con disco lento) y los controladores los recrearon |
| metrics-server (`--kubelet-insecure-tls`) y HPA de gateway con 6 pods generando carga | CPU 3 % → 480 %; escaló de 2 a 5 réplicas (el máximo) |

### Hallazgos de la prueba (ya corregidos en esta rama)

- **Kafka:** Bitnami retiró de Docker Hub las imágenes con etiqueta fija (`bitnami/kafka:4.0.0-debian-12-r10` da `not found`), así que el chart no sirve. Se reemplazó por `deploy/k8s/kafka.yaml` con `apache/kafka:3.9.0` en modo KRaft, un nodo y datos en `emptyDir`.
- **Neo4j:** el chart exige como mínimo 2 Gi de memoria (con 1 Gi no instala) y en disco lento tarda más de 8 min; el timeout subió a 15 min.
- **PostgreSQL:** con disco lento el liveness por defecto lo reiniciaba durante la inicialización; se agregaron retrasos y `startupProbe`.
- **Neo4j y `--wait`:** el chart crea un Service `LoadBalancer` que en kind nunca recibe IP externa, y `helm --wait` no terminaba (release `failed` aunque el pod estaba `1/1`). Se desactivó con `services.neo4j.enabled=false`; el servicio interno `neo4j` basta.
- **Timeout de Helm en servicios:** con el disco lento los servicios tardan más de 5 min en quedar listos; `desplegar.sh` ahora espera hasta 10 min.
- **HPA durante el arranque:** `promotion-service` subió a 5 réplicas con 18 % de CPU porque las réplicas nuevas aún no reportaban métricas y el arranque de la JVM consume mucha CPU; bajó a 2 solo. Si molesta en el cluster real, se puede agregar `behavior.scaleUp.stabilizationWindowSeconds` al HPA.
- **Servicios Spring:** arrancan en 35–110 s según la carga de la máquina. Se agregó `startupProbe` (hasta 5 min) y `timeoutSeconds: 5` a todas las sondas del chart; con ello `identity-service` dejó de reiniciarse.
- **Imágenes `v1.0.0`:** son anteriores a Actuator, por lo que no tienen `/actuator/health` y fallan las sondas. Los servicios solo funcionan en el cluster con imágenes construidas desde un commit que incluya esta HU (la primera etiqueta tras el merge).

**No se probó todavía:**
- El cliente `web` dentro del cluster (su imagen nueva está en la rama de HU-20; la `v1.0.0` tiene CVE críticos).
- Un controlador de Ingress (nginx): el objeto Ingress se creó pero no hay controlador en kind.
- Persistencia de Kafka y alta disponibilidad de la base de datos: esta HU deja un nodo por componente.
- Las pruebas funcionales entre servicios (flujo de encuesta → Kafka → promotion): solo se comprobó que cada servicio arranca, se conecta y responde sus rutas de salud.

## Pipeline (HU-19)

HU-19 integra este despliegue en Jenkins:

- `Terraform plan` revisa los tres ambientes cuando un PR toca `infra/` o en cada
  tag. Los applies de dev y stage son automáticos en un tag; el de prod ocurre
  después de la aprobación del usuario `aprobador`.
- `circleGuardHelm.groovy` crea el namespace, el `Secret` de la aplicación y el
  `imagePullSecret`, ejecuta `deploy/k8s/desplegar.sh` y espera el
  `kubectl rollout status` de cada Deployment.
- El despliegue oficial usa `DEPLOY_TARGET=helm`; `compose` queda como respaldo.
- El registro se toma de `REGISTRY` en `circleguard-env-<ambiente>`.

Detalles, credenciales y pasos manuales: [HU-19 · pipeline](hu-19-pipeline.md).
El contrato que debe cumplir Terraform: [HU-17 · contrato](hu-17-terraform-contrato.md).

## Pendiente (otras HU)

- HU-17 crea el cluster real con Terraform y entrega los kubeconfigs; el
  pipeline ya está cableado y omite las etapas mientras no exista
  `infra/terraform/envs/<ambiente>`.
- HU-21 agrega TLS, RBAC y autenticación de rutas.
- HU-24 y HU-25 consumen `/actuator/prometheus` y los logs.

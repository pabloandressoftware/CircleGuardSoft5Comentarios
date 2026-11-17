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
| `identity-service` y `auth-service` con la imagen reconstruida desde esta rama, contra PostgreSQL, Redis y OpenLDAP del cluster | `1/1 Running`; `/actuator/health/liveness`, `/readiness` y `/actuator/prometheus` responden 200 |
| metrics-server (`--kubelet-insecure-tls`) y HPA de gateway con 6 pods generando carga | CPU 3 % → 480 %; escaló de 2 a 5 réplicas (el máximo) |

### Hallazgos de la prueba (ya corregidos en esta rama)

- **Kafka:** Bitnami retiró de Docker Hub las imágenes con etiqueta fija (`bitnami/kafka:4.0.0-debian-12-r10` da `not found`), así que el chart no sirve. Se reemplazó por `deploy/k8s/kafka.yaml` con `apache/kafka:3.9.0` en modo KRaft, un nodo y datos en `emptyDir`.
- **Neo4j:** el chart exige como mínimo 2 Gi de memoria (con 1 Gi no instala) y en disco lento tarda más de 8 min; el timeout subió a 15 min.
- **PostgreSQL:** con disco lento el liveness por defecto lo reiniciaba durante la inicialización; se agregaron retrasos y `startupProbe`.
- **Servicios Spring:** arrancan en 35–110 s según la carga de la máquina. Se agregó `startupProbe` (hasta 5 min) y `timeoutSeconds: 5` a todas las sondas del chart; con ello `identity-service` dejó de reiniciarse.
- **Imágenes `v1.0.0`:** son anteriores a Actuator, por lo que no tienen `/actuator/health` y fallan las sondas. Los servicios solo funcionan en el cluster con imágenes construidas desde un commit que incluya esta HU (la primera etiqueta tras el merge).

**No se probó todavía:**
- Los servicios `promotion`, `form`, `notification`, `file` y `dashboard` y el cliente `web` dentro del cluster: solo se renderizaron y pasaron `helm lint`. Reconstruir cada imagen tarda unos 35 min en la máquina de desarrollo (disco duro y 19 de carga).
- Un controlador de Ingress (nginx): el objeto Ingress se creó pero no hay controlador en kind.
- Persistencia de Kafka y alta disponibilidad de la base de datos: esta HU deja un nodo por componente.

## Pendiente (otras HU)

- HU-17 crea el cluster real con Terraform; esta HU solo depende de que exista.
- HU-19 llama a `desplegar.sh` desde Jenkins.
- HU-21 agrega TLS, RBAC y autenticación de rutas.
- HU-24 y HU-25 consumen `/actuator/prometheus` y los logs.

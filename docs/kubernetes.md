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

**No se probó todavía:**
- PostgreSQL, Kafka, Neo4j y OpenLDAP con `middleware.sh`, ni los otros 7 servicios en el cluster (solo se renderizaron y pasaron `helm lint`).
- El HPA: el objeto se crea con 70 % de CPU y de 2 a 5 réplicas, pero kind no trae metrics-server, así que no escaló. En el cluster de HU-17 hay que tener metrics-server.
- Un controlador de Ingress (nginx): el objeto Ingress se creó pero no hay controlador en kind.

## Pendiente (otras HU)

- HU-17 crea el cluster real con Terraform; esta HU solo depende de que exista.
- HU-19 llama a `desplegar.sh` desde Jenkins.
- HU-21 agrega TLS, RBAC y autenticación de rutas.
- HU-24 y HU-25 consumen `/actuator/prometheus` y los logs.

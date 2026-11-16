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

## Prueba local con kind

Se puede probar todo en una máquina con Docker usando kind: `kind create cluster`, cargar las imágenes con `kind load docker-image` y seguir los pasos de arriba. Las evidencias de esa prueba están en `docs/informe/` (ver el informe de la Parte 2).

## Pendiente (otras HU)

- HU-17 crea el cluster real con Terraform; esta HU solo depende de que exista.
- HU-19 llama a `desplegar.sh` desde Jenkins.
- HU-21 agrega TLS, RBAC y autenticación de rutas.
- HU-24 y HU-25 consumen `/actuator/prometheus` y los logs.

# Arquitectura y pipeline de CircleGuard

Estado verificado el 24 de noviembre de 2026: release **v1.1.2** en dev, stage y prod
(AWS `us-east-2`). Diagramas en Mermaid: GitHub los dibuja al abrir este archivo.
La versión de la Parte 1 (Compose por SSH) está en el historial de Git y en
[informe-parte-1.md](informe/informe-parte-1.md).

## 1. Arquitectura de la aplicación

```mermaid
flowchart LR
    User["Navegador"] -->|"HTTPS (Let's Encrypt)"| Ingress["Traefik Ingress<br/>cert-manager"]
    Ingress --> Web["web<br/>Expo + NGINX"]
    Ingress --> Gateway["gateway-service<br/>valida QR"]
    Ingress --> Auth["auth-service<br/>LDAP, JWT, permisos"]
    Ingress --> Form["form-service<br/>cuestionarios"]
    Ingress --> Promotion["promotion-service<br/>motor de estados"]
    Ingress --> Dashboard["dashboard-service<br/>analítica"]
    Ingress --> File["file-service<br/>adjuntos"]
    Ingress -->|"rutas protegidas"| Identity

    Auth -->|"POST /identities/map<br/>X-Internal-Api-Key<br/>Retry + Circuit Breaker"| Identity["identity-service<br/>bóveda cifrada"]
    Auth --> LDAP[("OpenLDAP")]
    Auth --> PGAuth[("PostgreSQL<br/>circleguard_auth")]
    Identity --> PGId[("PostgreSQL<br/>circleguard_identity")]
    Form --> PGForm[("PostgreSQL<br/>circleguard_form")]
    Gateway --> Redis[("Redis")]

    Form -->|"survey.submitted"| Kafka{{"Kafka"}}
    Kafka --> Promotion
    Promotion --> Neo4j[("Neo4j<br/>grafo de contactos")]
    Promotion --> Redis
    Promotion -->|"cambios de estado"| Kafka
    Kafka --> Notification["notification-service"]
    Dashboard -->|"Retry + Circuit Breaker"| Promotion
    Dashboard --> PGDash[("PostgreSQL<br/>circleguard_dashboard")]
```

notification-service no tiene Ingress: solo consume eventos de Kafka.

- **Base de datos por servicio:** una instancia de PostgreSQL con cinco bases y usuario propio.
- **Eventos:** Kafka desacopla form → promotion → notification.
- **Cache-aside:** el estado de salud se sirve desde Redis/Caffeine.
- **Máquina de estados:** las transiciones de salud se validan en promotion.
- **Retry + Circuit Breaker (Resilience4j):** auth → identity y dashboard → promotion.
- **Feature toggle (HU-23):** ver [patrones/](patrones/README.md).
- **Seguridad (HU-21):** cerrada por defecto. Solo login, verificación de QR y
  `/actuator/health|prometheus` son públicos dentro del clúster; las llamadas internas
  exigen `X-Internal-Api-Key`; los permisos salen de roles (migración V6 de auth).

## 2. Infraestructura en AWS (HU-17)

```mermaid
flowchart TB
    subgraph AWS["AWS us-east-2 · proyecto 001784022331 · VPC propia por ambiente"]
        subgraph CI["CI · m7i-flex.large · EIP 18.220.203.182"]
            Caddy["Caddy HTTPS"] --> Jenkins
            Caddy --> Nexus["Nexus<br/>registro Docker"]
            Caddy --> Sonar["SonarQube"]
        end
        subgraph DEV["dev · k3s 2 nodos · EIP 3.143.61.218"]
            DevNS["circleguard-dev<br/>9 releases + middleware"]
            DevMon["monitoring<br/>kube-prometheus-stack"]
        end
        subgraph STAGE["stage · k3s 2 nodos · EIP 3.134.68.77"]
            StNS["circleguard-stage"]
            StMon["monitoring"]
        end
        subgraph PROD["prod · k3s 2 nodos · EIP 13.59.35.181"]
            PrNS["circleguard-prod"]
            PrMon["monitoring"]
        end
        State[("S3 tfstate versionado<br/>+ DynamoDB locks")]
        Logs[("CloudWatch Logs<br/>/circleguard/*/k3s")]
    end
    Jenkins -->|"kubeconfig-env<br/>helm upgrade"| DevNS
    Jenkins --> StNS
    Jenkins --> PrNS
    Jenkins -->|"CircleGuardCiTerraformRole"| State
    Nexus -.->|"pull de imágenes"| DevNS
    Nexus -.-> StNS
    Nexus -.-> PrNS
```

| Pieza | Implementación |
| --- | --- |
| Terraform | Módulos `network`, `cluster`, `environment`; raíces `envs/ci`, `dev`, `stage`, `prod`; estado remoto por ambiente |
| Cómputo | m7i-flex.large (2 vCPU, 8 GiB); k3s servidor + worker por ambiente |
| Acceso | SSH y API 6443 limitados al operador y a CI; tráfico entre nodos solo dentro de su security group |
| Protección | `prevent_destroy` en CI; `check-plan.mjs` bloquea bajas y reemplazos en el pipeline; `ignore_changes` en la IP pública del worker |
| Identidad | Jenkins asume `CircleGuardCiTerraformRole` (sin access keys); nodos con permissions boundary (SSM + logs) |
| DNS / TLS | Nombres sslip.io sobre las EIP; cert-manager 1.21.3 y Let's Encrypt (ACME HTTP-01 por Traefik) |
| Costos | [costos.md](costos.md) |

## 3. Pipeline de CI/CD (HU-19, HU-20, HU-27)

```mermaid
flowchart TD
    PR["PR o push a main<br/>commitlint en GitHub Actions"] --> Q["Calidad: tests, JaCoCo ≥ 60 %,<br/>Testcontainers, SonarQube gate"]
    Q --> Sec["Seguridad: gitleaks + Trivy fs"]
    Sec --> Plan["Terraform plan ×3<br/>check-plan.mjs"]
    Plan --> WebT["Jest del cliente web"]

    Tag["Tag vX.Y.Z (Release Please)"] --> Notes["Notas de la release publicadas"]
    Notes --> Build["Construir 9 imágenes"]
    Build --> Scan["Trivy: CRITICAL bloquea<br/>VEX ligado al digest"]
    Scan --> Push["Publicar en Nexus"]
    Push --> TfDev["Terraform apply dev"] --> HDev["Helm dev + humo + TLS"] --> E2E["E2E Playwright"]
    E2E --> TfSt["Terraform apply stage"] --> HSt["Helm stage + humo"] --> ZAP["OWASP ZAP baseline"]
    ZAP --> Approve{"Aprobación manual<br/>usuario aprobador"}
    Approve --> TfPr["Terraform apply prod"] --> HPr["Helm prod + humo"] --> Inv["Versiones por ambiente"]

    RB["ROLLBACK_TAG + ROLLBACK_ENV"] --> RBH["helm rollback de los 9 releases<br/>a la revisión con esa etiqueta"] --> RBS["Readiness + humo<br/>(prod pide aprobación)"]
```

La misma imagen inmutable `vX.Y.Z` recorre dev, stage y prod; el rollback reutiliza
revisiones Helm existentes y no reconstruye imágenes.

## 4. Observabilidad (HU-24, HU-25)

- **Métricas:** cada servicio expone `/actuator/prometheus`; `ServiceMonitor` por
  servicio; dashboards técnico y de negocio en Grafana.
- **Alertas:** `CircleguardServiceDown`, `High5xxRate`, `GateDenialSpike`,
  `ContainmentSlaBreach` y `AvailabilityLow`, probadas con `promtool`. Alertmanager
  agrupa y notificaría a Discord si hubiera webhook (pendiente).
- **Trazas y logs:** agente OpenTelemetry en el chart y logs con `traceId`. Jaeger y
  Loki se instalan con `observability/trazas-logs/`; **no** están instalados en los
  clústeres de AWS.

## 5. Estado y límites conocidos

| Tema | Estado |
| --- | --- |
| auth y dashboard sin `/actuator/prometheus` | Corregido en PR #64; pendiente de release |
| Notificaciones | Discord sin webhook y SMTP con `530 Authentication Required`: hoy solo queda Alertmanager |
| Cabeceras HTTP (HSTS, CSP, X-Frame-Options) | 12 avisos de ZAP sin corregir |
| Trivy fs y `node_modules` anidados | Falso positivo en workspaces reutilizados; excluir `**/node_modules` |
| Trazas en AWS | Jaeger/Loki sin instalar |

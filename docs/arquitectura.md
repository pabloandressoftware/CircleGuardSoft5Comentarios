# Arquitectura y pipeline de CircleGuard

Diagramas en Mermaid: GitHub los dibuja directamente al abrir este archivo.

## 1. Arquitectura de la aplicación

```mermaid
flowchart LR
    Web["Cliente web<br/>(Expo)"] --> Caddy["Caddy<br/>HTTPS"]
    Caddy --> Gateway["gateway-service<br/>valida QR y JWT"]
    Caddy --> Auth["auth-service<br/>LDAP y usuarios locales"]
    Caddy --> Form["form-service<br/>cuestionarios"]
    Caddy --> Promotion["promotion-service<br/>motor de estados"]

    Auth -->|"POST /identities/map"| Identity["identity-service<br/>bóveda de identidad"]
    Auth --> LDAP[("LDAP")]
    Auth --> PGAuth[("PostgreSQL")]
    Identity --> PGId[("PostgreSQL<br/>identidad cifrada")]
    Form --> PGForm[("PostgreSQL<br/>respuestas JSONB")]

    Form -->|"survey.submitted"| Kafka{{"Apache Kafka"}}
    Kafka --> Promotion
    Promotion --> Neo4j[("Neo4j<br/>grafo de contactos")]
    Promotion --> Redis[("Redis")]
    Promotion -->|"cambios de estado"| Kafka
    Kafka --> Notification["notification-service<br/>(Parte 2)"]
    Dashboard["dashboard-service<br/>(Parte 2)"] --> PGAuth
    File["file-service<br/>(Parte 2)"]
```

La Parte 1 despliega auth, identity, promotion, form, gateway, el cliente web y el middleware. notification, file y dashboard se compilan, prueban y publican, pero quedan detrás del perfil `part2` de Compose.

## 2. Pipeline de CI/CD (Jenkins)

```mermaid
flowchart TD
    Push["Push o Pull Request<br/>(commitlint en GitHub Actions)"] --> Prep["Preparar<br/>cargar pasos compartidos"]
    Prep --> Detect["Detectar módulos modificados"]
    Detect --> Quality["Calidad de servicios<br/>tests · JaCoCo ≥ 60 % · integración · SonarQube gate"]
    Quality --> Web["Pruebas del cliente web (Jest)"]

    Tag["Tag vX.Y.Z<br/>(Release Please desde main)"] --> Publish["Publicar 9 imágenes en Nexus"]
    Publish --> Dev["Desplegar dev + humo"]
    Dev --> E2E["E2E con Playwright"]
    E2E --> Stage["Promover a stage + humo<br/>(misma imagen)"]
    Stage --> Approve{"Aprobación manual<br/>usuario aprobador"}
    Approve --> Prod["Desplegar prod + humo"]

    Quality -. "falla" .-> Notify
    Dev -. "falla" .-> Notify
    Prod -. "falla" .-> Notify
    Notify["Aviso de fallo<br/>Discord y correo"]

    Rollback["Ejecución con ROLLBACK_TAG y ROLLBACK_ENV"] --> Redeploy["Redesplegar etiqueta anterior<br/>+ humo (prod pide aprobación)"]
    Redeploy --> Notify2["Aviso de rollback"]
```

La misma imagen inmutable (la etiqueta `vX.Y.Z` publicada en Nexus) recorre dev, stage y prod; el rollback reutiliza una etiqueta ya publicada sin reconstruir imágenes.

## 3. Infraestructura

```mermaid
flowchart LR
    Dev["Desarrollador"] -->|"PR a dev"| GH["GitHub<br/>ramas protegidas"]
    GH -->|"webhook / escaneo"| Jenkins
    subgraph CI["Servidor de CI"]
        Jenkins --> Sonar["SonarQube"]
        Jenkins --> Nexus["Nexus<br/>registro de imágenes"]
    end
    Jenkins -->|"SSH + docker compose"| VDev["EC2 dev"]
    Jenkins -->|"SSH + docker compose"| VStage["EC2 stage"]
    Jenkins -->|"SSH + docker compose"| VProd["EC2 prod"]
```

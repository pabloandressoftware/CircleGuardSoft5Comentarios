# Evidencia consolidada del Sprint 2 (HU-17 a HU-27)

Verificado el 24 de noviembre de 2026 contra Jenkins, los tres clústeres, AWS y Jira.
Cada fila indica dónde está la prueba. Los artefactos completos (planes, informes de
Trivy/ZAP, JSON de rollback) están en los builds de Jenkins; aquí solo se copian
nombres, digests, resultados y enlaces, nunca secretos.

Jenkins: https://jenkins.18-220-203-182.sslip.io/

| Build | Qué demuestra |
| --- | --- |
| `circleguard » v1.1.2` #2 | Release oficial: imágenes, Trivy, dev → E2E → stage → ZAP → aprobación → prod (SUCCESS, 6.007 s) |
| `circleguard » v1.1.2` #3 | Rollback dev v1.1.2 → v1.1.1 |
| `circleguard » v1.1.2` #4 | Regreso dev v1.1.1 → v1.1.2 |
| `circleguard-observability` #7 | Alertas dev |
| `circleguard-observability` #8 | Alertas stage |
| `circleguard-ensayo-despliegue` #4 | E2E 5/5 en dev después de volver a v1.1.2 (job de ensayo no oficial: solo humo, E2E y ZAP) |
| `circleguard » PR-59` #1, `PR-60` #1 | Plan de Terraform sin cambios con stage/prod detenidos, Sonar, gitleaks, Jest |

## Release v1.1.2

- Tag `v1.1.2` en `f24127f` (release de Release Please, `version.txt` = 1.1.2). `v1.1.0` y `v1.1.1` no se movieron.
- Parámetros oficiales: `BUILD_ALL=true`, `DEPLOY_TARGET=helm`, `DEPLOY_MIDDLEWARE=true`, `SKIP_DEPLOY=false`.
- Aprobación de prod: `aprobador`, 24-nov 18:44:43 UTC (`approval-prod.txt`).

Digests publicados en `registry.18-220-203-182.sslip.io/circleguard/*:v1.1.2`:

| Imagen | Digest |
| --- | --- |
| circleguard-auth-service | `sha256:2f751a2896fe6feb2a6f5dd1c996c260a3172c83a82d562e68582f4895059298` |
| circleguard-identity-service | `sha256:c7e04bc5980448bdae89851c7ddedcf7a9cbedbda8de13aa9723a33ab5d1f969` |
| circleguard-promotion-service | `sha256:5b646bac3d6e0ddd9bf75e21b8653f210d0d5d768af24ed8af11b8b9cf701b15` |
| circleguard-form-service | `sha256:233aa88f792558c81da9aacc69d134679ae6e457ae33b5cd2a1937297cabc670` |
| circleguard-gateway-service | `sha256:8cef74fe0ec16deabbaaaa3af3cc338f7ec18d4fae9fcbc17aa772368625b701` |
| circleguard-notification-service | `sha256:84724862dc40c42542a7c3807435406693b662deae7f4af890d8988ea54b72bd` |
| circleguard-file-service | `sha256:c322c661b0357c40884ece5cedf4fbfc167a783e1a604078e1006e04459e1a4c` |
| circleguard-dashboard-service | `sha256:408e34107866e60691cb33f53cdadec7218bdc0463ab7668dec201fe6c120a48` |
| web | `sha256:a57d3fc80320a08e2fb0fd2b194db42af847ef48a32aaec3abaea184e1ea5874` |

## HU-17 · Infraestructura con Terraform

| Criterio | Evidencia | Resultado |
| --- | --- | --- |
| CI y tres ambientes reproducibles | `infra/terraform/envs/{ci,dev,stage,prod}`; módulos `network`, `cluster`, `environment` | 7 VMs m7i-flex.large, VPC por ambiente |
| Estado remoto y bloqueo | S3 `circleguard-tfstate-001784022331-us-east-2` (versionado, cifrado) + DynamoDB `circleguard-tf-locks` | Un estado por ambiente |
| Apply desde el pipeline sin destruir | Build #2, etapas *Terraform apply dev/stage/prod* | `Plan revisado: 0 recursos con cambios` y `0 added, 0 changed, 0 destroyed` en los tres |
| Protección | `check-plan.mjs`, `prevent_destroy` en CI, `ignore_changes` en el worker | PR-59 #1: sin cambios con stage/prod detenidos |
| Identidad | `CircleGuardCiTerraformRole`, sin access keys | Política generada desde planes reales |

## HU-18 · Servicios, web y middleware en Kubernetes

Etapa *Versiones por ambiente* del build #2 (18:55 UTC):

| Ambiente | Releases | Pods listos/deseados | Revisión Helm |
| --- | --- | --- | --- |
| dev | 9/9 en v1.1.2 | 1/1, promotion y gateway 2/2 | 2 (la 1 es v1.1.1) |
| stage | 9/9 en v1.1.2 | ídem | 1 |
| prod | 9/9 en v1.1.2 | ídem | 1 |

Middleware por ambiente: PostgreSQL (5 bases con usuario propio y permiso CREATE
comprobado por SQL en cada despliegue), Redis, Neo4j, Kafka y OpenLDAP.

## HU-19 · Terraform y Helm en Jenkins

Orden ejecutado en el build #2: Terraform plan → Calidad de servicios → Seguridad →
Pruebas del cliente web → Construir imágenes → Escanear → Publicar → Terraform apply
dev → Desplegar dev → Humo dev → E2E dev → Terraform apply stage → Desplegar stage →
Humo stage → ZAP stage → **Aprobación prod** → Terraform apply prod → Desplegar prod →
Humo prod → Versiones por ambiente. Duración total 100 min, de los cuales 49 fueron
de espera de la aprobación.

## HU-20 · Vulnerabilidades y secretos

| Control | Resultado |
| --- | --- |
| Trivy de imágenes (CRITICAL bloquea) | 0 CRITICAL en las 9 imágenes |
| Excepción VEX | 2 suprimidas: CVE-2026-47884 y CVE-2026-47890 en `spring-webmvc` (`not_affected`, `vulnerable_code_not_in_execute_path`), ligadas al digest; revisar antes del 27-nov |
| gitleaks | Sin hallazgos (PR-59 #1, PR-60 #1) |
| Secretos | Solo en Jenkins Credentials y `circleguard-secrets` (11 claves por ambiente) |
| Exposición detectada | El chart de Neo4j imprimió su clave en el log del build #2 (stage y prod). **Rotada** el 24-nov en ambos; verificado: la clave nueva entra, la anterior se rechaza, los pods de promotion y Jenkins tienen la nueva (comparación por SHA-256). Causa corregida en **PR #61** (integrado) |
| Falso positivo conocido | Trivy fs ve `mobile/node_modules/.../Gemfile.lock` en workspaces reutilizados; `--skip-dirs 'node_modules'` no excluye carpetas anidadas. Corrección pendiente: `**/node_modules` |

## HU-21 · RBAC, TLS y autenticación

| Ambiente | `/` | `/api/v1/health` | Certificado |
| --- | --- | --- | --- |
| dev | 200 | 401 | CN=dev.3-143-61-218.sslip.io, Let's Encrypt YR2, vence 22-feb-2027 |
| stage | 200 | 401 | CN=stage.3-134-68-77.sslip.io, Let's Encrypt YR1, vence 22-feb-2027 |
| prod | 200 | 401 | CN=prod.13-59-35-181.sslip.io, Let's Encrypt YR2, vence 22-feb-2027 |

- E2E 5/5 en dev contra la URL pública: contraseña incorrecta (401), login válido,
  envío del cuestionario (sembrado con el token de la cuenta de pruebas, que necesita
  `questionnaire:manage`), QR de una cuenta ACTIVE (GREEN) y SUSPECT (RED).
- auth → identity exige `X-Internal-Api-Key`. `IdentityClientTest` devuelve 401 sin
  la cabecera; se comprobó que la prueba falla si se quita.

## HU-23 · Feature toggle y patrón adicional (evidencia parcial)

Jira la marca *Done*. Contrastada con sus criterios:

| Criterio | Lo que existe | Estado |
| --- | --- | --- |
| 1. Toggle en **ConfigMap** que controle unconfirmed-fencing o el umbral, con prueba de que surte efecto sin redesplegar | `system_settings` en PostgreSQL editable por `/api/v1/admin/settings`. `encounterWindowDays` y `mandatoryFenceDays` sí cambian la lógica de `HealthStatusService` y `StatusLifecycleService` sin redesplegar. El flag `unconfirmedFencingEnabled` se guarda (`toggle-unconfirmed-fencing`, probado en `PromotionControllersTest`), pero **ningún servicio lo lee**. No hay ConfigMap | **Parcial** |
| 2. Patrón adicional con prueba y trade-offs (sugeridos: Bulkhead o rate limit en el Ingress) | Retry + Circuit Breaker con pruebas y trade-offs en [patrones/](../patrones/README.md), con la etiqueta HU-22. No hay Bulkhead ni rate limit | **Parcial** |

## HU-24 · Métricas, tableros y alertas

Builds `circleguard-observability` #7 (dev) y #8 (stage), ambos SUCCESS, con el script
corregido en #63. Antes de provocar cada alerta se exige que esté inactiva;
`ServiceDown` se identifica por `namespace` y `deployment`. El script guarda las réplicas
originales y las restaura siempre.

| Alerta | Cómo se provoca | dev FIRING | dev RESOLVED | stage FIRING | stage RESOLVED |
| --- | --- | --- | --- | --- | --- |
| `CircleguardServiceDown` (auth-service) | auth-service a 0 réplicas | 75 s | 30 s | 90 s | 45 s |
| `CircleguardHigh5xxRate` | PostgreSQL a 0 y carga sobre form-service | 300 s | 240 s | 255 s | 270 s |
| `CircleguardGateDenialSpike` | Tokens QR inválidos al gateway | 181 s | 300 s | 180 s | 300 s |

FIRING se cuenta desde la provocación; RESOLVED, desde la restauración. Los tiempos son
coherentes con las reglas: `for: 1m` en ServiceDown, y `for: 2m` sobre una tasa de 5
minutos en las otras dos. Registros: [dev](alertas/dev-registro.txt) y
[stage](alertas/stage-registro.txt). Los JSON de Alertmanager de cada fase están en
los artefactos de cada build.

**Primeros intentos descartados:**

- #5 abortó por un error del script.
- #6 tomó como FIRING una alerta de dashboard-service que ya estaba activa. Se detuvo y
  se corrigió el script (#63).

**Hallazgo:** auth-service y dashboard-service no exponían `/actuator/prometheus`
(401 y 404), así que sus targets figuraban caídos y `CircleguardAvailabilityLow` seguía
activa. Corregido en **#64** con una prueba de regresión, pendiente de release.

**Notificaciones:**

- **Discord:** no hay webhook (credencial `discord-webhook` inexistente).
- **Correo:** también falla. El aviso del rollback (#3) devolvió
  `530 Authentication Required` del SMTP de Gmail.

Ninguna notificación externa se entregó; la evidencia es la API de Alertmanager.

## HU-25 · Trazas y logs

El chart inyecta el agente OpenTelemetry y los logs llevan `traceId`
([trazas-y-logs.md](../trazas-y-logs.md)). **Jaeger y Loki no están instalados en los
clústeres de AWS:** los servicios registran `Failed to export spans` hacia
`jaeger.monitoring.svc`. La verificación de los tres criterios corresponde a la
instalación descrita en ese documento, no a los ambientes de AWS.

## HU-26 · Locust y OWASP ZAP

| Prueba | Resultado |
| --- | --- |
| ZAP baseline contra stage (build #2) | 21 URL, **0 FAIL**, 55 PASS, 12 WARN: Cache-control, anti-clickjacking, X-Content-Type-Options, comentarios sospechosos, HSTS, versión del servidor, CSP, contenido cacheable, Permissions-Policy, timestamps, funciones JS peligrosas y versión de ZAP |
| Locust (100 usuarios, 60 s, máquina local) | `/gate/validate` p95 7–9 ms (cumple); `/surveys` p95 2,9–6,2 s (no cumple). No medido en AWS ([pruebas-rendimiento-seguridad.md](../pruebas-rendimiento-seguridad.md)) |

## HU-27 · Versiones por ambiente y rollback

Inventario: la etapa *Versiones por ambiente* corre al final de cada release o
rollback y archiva `.local-evidence/hu-27/**`.

**Rollback real en dev (24-nov-2026):**

| Build | Operación | Inicio (UTC) | Revisión restaurada | Recuperación | Humo | Resultado |
| --- | --- | --- | --- | --- | --- | --- |
| `v1.1.2` #3 | v1.1.2 → **v1.1.1** | 19:54:35 | rev. 1 de los 9 releases (quedan como rev. 3) | **581,2 s** | `/` 200, `/api/v1/health` 401, TLS válido | succeeded |
| `v1.1.2` #4 | v1.1.1 → **v1.1.2** | 20:25:46 | rev. 2 de los 9 releases (quedan como rev. 4) | **567,1 s** | ídem | succeeded |

Tras cada operación, el inventario muestra los tres ambientes así:

- Después de #3: dev en v1.1.1, stage y prod en v1.1.2, todos sanos.
- Después de #4: los tres en v1.1.2.

Evidencia copiada del build (sin values ni secretos):

- [rollback a v1.1.1](hu-27/aws-dev-v1.1.1/rollback.json), con su
  [inventario](hu-27/aws-dev-v1.1.1/overview-after.md);
- [regreso a v1.1.2](hu-27/aws-dev-v1.1.2/rollback.json), con su
  [inventario](hu-27/aws-dev-v1.1.2/overview-after.md).

**Cómo leer el resultado:**

- **Duración:** la recuperación (≈ 9,5–9,7 min) es secuencial: `helm rollback --wait`
  y `rollout status` de los 9 releases, uno tras otro, más el humo.
- **Por qué solo dev:** es el único ambiente con dos releases en su historial (v1.1.1
  y v1.1.2). Stage y prod solo tienen v1.1.2, y no se usa v1.1.0 por ser vulnerable.
- **Límite del humo:** v1.1.1 conserva el defecto del login (auth sin
  `X-Internal-Api-Key`) que corrigió v1.1.2. El humo pasa, pero un login real no; por
  eso dev volvió a v1.1.2 de inmediato.
- **Comprobación posterior:** después de #4 se ejecutó el E2E contra dev
  (`circleguard-ensayo-despliegue` #4, 20:35 UTC): humo correcto, **E2E 5/5** con login real, cuestionario y QR, y ZAP con 0 FAIL.
- **Notificaciones:** ninguna se entregó. Discord no está configurado y el SMTP
  respondió `530`.

## Seguimiento abierto

| Tema | Dónde |
| --- | --- |
| Métricas de auth y dashboard | PR #64; requiere release |
| Filtro de alertas por etiquetas | PR #63 |
| Discord | [manual-operaciones.md](../manual-operaciones.md#7-alertas-y-notificaciones) |
| HU-23: ConfigMap y Bulkhead/rate limit | Backlog |
| Cabeceras de ZAP, Trivy `**/node_modules`, `neo4j.passwordFromSecret` | Backlog |

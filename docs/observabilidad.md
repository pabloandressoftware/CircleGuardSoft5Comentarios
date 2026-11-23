# Observabilidad (HU-24)

HU-24 agrega métricas de negocio, dashboards y alertas sobre los servicios que
HU-18 ya expone en `/actuator/prometheus`. El stack de monitoreo es
**kube-prometheus-stack** (Prometheus, Grafana, Alertmanager y kube-state-metrics).

## Qué se mide

### Técnico

- Targets de los 8 servicios Java en dev, stage y prod.
- Solicitudes por segundo, latencia p95 y errores 5xx.
- JVM heap, reinicios de contenedores y duración del scrape.

### Negocio

- `circleguard_promotions_total{status,admin_override}`: promociones por estado.
- `circleguard_containment_seconds`: tiempo desde `CONFIRMED` hasta `ACTIVE` o
  `RECOVERED`; la meta es p95 < 60 s.
- `circleguard_gate_validations_total{result,reason}`: validaciones GREEN/RED
  en la puerta.

`HealthStatusService` guarda en Redis `user:confirmed_at:<id>` para calcular la
contención. `QrValidationService` registra el resultado y la duración de cada
validación. Prometheus agrega el tag `application` a todas las métricas.

## Instalación

Requisitos: `helm`, `kubectl`, `jq` y un cluster con los namespaces
`circleguard-dev`, `circleguard-stage` y `circleguard-prod`.

El pipeline lo instala automáticamente en cada tag con el stage
**Observabilidad**, que usa `kubeconfig-dev` y las credenciales
`discord-webhook` y `grafana-admin-password`. Para hacerlo a mano:

```bash
CI_DISCORD_WEBHOOK='https://discord.com/api/webhooks/...' \
GRAFANA_ADMIN_PASSWORD='<contrasena>' \
bash observability/instalar.sh
```

El webhook es el mismo de **Spidey**, el bot que ya usa Jenkins en la credencial
`discord-webhook` (`CI_DISCORD_WEBHOOK` en `ci.env`). El script lo guarda dentro
del Secret `alertmanager-config` y no lo escribe en el repositorio. Se usa
`webhook_url` (no `webhook_url_file`) porque Alertmanager v0.27.0, el que trae el
chart 65.5.1, rechaza esa variante.

El script instala el stack, aplica `observability/servicemonitors.yaml`, las
reglas de `observability/alerts/` y los dashboards de `observability/grafana/`.

### Acceso

```bash
# Grafana
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80
# http://127.0.0.1:3000  (admin / GRAFANA_ADMIN_PASSWORD)

# Prometheus
kubectl -n monitoring port-forward svc/kube-prometheus-stack-prometheus 9090:9090

# Alertmanager
kubectl -n monitoring port-forward svc/kube-prometheus-stack-alertmanager 9093:9093
```

## Dashboards

- `CircleGuard · Técnico`: salud de los targets, tráfico, latencia, errores,
  JVM, reinicios y scrape.
- `CircleGuard · Negocio`: promociones por estado, validaciones GREEN/RED,
  contención p50/p95 y disponibilidad.

La disponibilidad usa `circleguard:availability:ratio5m` y se evalúa contra la
meta **99,5 %** en horario **7:00–22:00 de Colombia**. En PromQL eso es
`hour() >= 12 or hour() < 3` porque Prometheus usa UTC.

## Alertas

`observability/alerts/rules.yaml` define:

| Alerta | Condición |
| --- | --- |
| `CircleguardServiceDown` | `up == 0` durante 1 min |
| `CircleguardHigh5xxRate` | más de 5 % de 5xx durante 2 min |
| `CircleguardGateDenialSpike` | más de 1 validación RED/s durante 2 min |
| `CircleguardContainmentSlaBreach` | p95 de contención > 60 s durante 5 min |
| `CircleguardAvailabilityLow` | disponibilidad < 99,5 % en horario de negocio |

Todas llevan `severity: critical` y se envían al receiver `discord` de
Alertmanager (mismo webhook de Spidey).

### Validar las reglas sin clúster

```bash
promtool check rules observability/alerts/rules.yaml
promtool test rules observability/alerts/tests.yaml
```

## Evidencia de las tres alertas

La evidencia es **manual y nunca se ejecuta en el pipeline**: el script escala
`web` a 0 y detiene PostgreSQL. Con un cluster real y el stack instalado:

```bash
bash observability/evidencias/disparar-alertas.sh
```

El script hace, en orden:

1. Escala `web` a 0 → `CircleguardServiceDown`.
2. Detiene PostgreSQL y golpea `form-service` → `CircleguardHigh5xxRate`.
3. Envía tokens QR inválidos al gateway → `CircleguardGateDenialSpike`.

Después de cada disparo espera a que Alertmanager reciba la alerta y guarda en
`observability/evidencias/<fecha>/`:

- `pods.txt` del namespace,
- `alerts.json` con la respuesta de `/api/v2/alerts`,
- `alertmanager.json`.

El `trap` restaura `web` y PostgreSQL al terminar. **No ejecutar en prod.**

## Límites

- La evidencia real necesita un cluster con los servicios y el middleware de
  HU-17/HU-18. Sin clúster, `promtool test rules` valida las expresiones.
- La CA y el acceso a Grafana son internos; se usa `port-forward`, no un
  Ingress público.
- Las métricas de negocio se calculan a partir de eventos reales; para poblar
  los dashboards se necesitan promociones y validaciones de la aplicación.

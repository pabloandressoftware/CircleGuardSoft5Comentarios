# Trazas y logs centralizados (HU-25)

HU-25 permite seguir una encuesta de punta a punta: una sola **traza** en Jaeger
(`form → Kafka → promotion → notification`) y los **logs** de todos los pods en Loki,
filtrables por servicio y por `traceId`. Se apoya en los despliegues de HU-18 y en el
Grafana de HU-24, y deja evidencia para HU-29.

## Cómo funciona

```
form-service ──survey.submitted──▶ promotion-service ──promotion.status.changed──▶ notification-service
     │  (agente OpenTelemetry en cada pod: spans + cabecera traceparent en cada mensaje de Kafka)
     └──────────────────────────────▶ Jaeger (OTLP/HTTP :4318)

stdout de todos los pods ──▶ Promtail (un pod por nodo) ──▶ Loki ──▶ Grafana (Explore)
```

- **Agente Java de OpenTelemetry.** Un `initContainer` copia el agente a un volumen y el
  servicio arranca con `-javaagent`. La imagen del servicio y el código Java no cambian.
- **Una sola traza a través de Kafka.** El agente instrumenta el productor y el consumidor de
  Kafka: guarda el contexto de la traza en la cabecera `traceparent` del mensaje y el
  consumidor la continúa. Por eso `survey.submitted` y `promotion.status.changed` quedan
  dentro de la misma traza que la petición HTTP inicial.
- **Logs con `traceId`.** El agente pone `trace_id` y `span_id` en el MDC del log; los tres
  servicios del flujo los imprimen (`logging.pattern.level` en su `application.yml`). Sin el
  agente (Docker Compose, pruebas) esos campos salen vacíos y nada más cambia.
- **Loki recoge todo.** Promtail lee los logs de todos los pods del cluster. Solo los tres
  servicios del flujo imprimen `trace_id`, así que el filtro por `traceId` aplica a ellos.

## Qué se agregó

| Archivo | Para qué |
| --- | --- |
| `deploy/helm/circleguard-service/templates/deployment.yaml` | `initContainer`, volumen y variables `OTEL_*`, solo si `otel.enabled` |
| `deploy/helm/circleguard-service/values.yaml` | Bloque `otel` (apagado por defecto), imagen del agente y endpoint de Jaeger |
| `deploy/helm/values/{form,promotion,notification}-service.yaml` | Activan `otel` y suben el límite de memoria a 768Mi |
| `services/circleguard-{form,promotion,notification}-service/.../application.yml` | Formato de log con `trace_id` y `span_id` |
| `observability/trazas-logs/jaeger.yaml` | Jaeger all-in-one (OTLP 4317/4318, UI 16686) |
| `observability/trazas-logs/loki-stack-values.yaml` | Loki y Promtail (chart `grafana/loki-stack`) |
| `observability/trazas-logs/datasources.yaml` | Loki y Jaeger en Grafana, con enlace de `trace_id` a la traza |
| `observability/trazas-logs/instalar.sh` | Instala todo lo anterior en el namespace `monitoring` |

Los otros seis servicios renderizan exactamente igual que antes (se comparó `helm template`
antes y después).

## Despliegue

Requiere el stack de HU-24 (`observability/instalar.sh`) y los servicios de HU-18.

```bash
# 1. Jaeger, Loki, Promtail y los orígenes de datos de Grafana
bash observability/trazas-logs/instalar.sh

# 2. Redesplegar los tres servicios del flujo para que arranquen con el agente
bash deploy/k8s/desplegar.sh dev vX.Y.Z form-service promotion-service notification-service
```

El namespace es `monitoring` (el mismo de Prometheus y Grafana) y sirve a los tres
ambientes. El endpoint del agente es `http://jaeger.monitoring.svc:4318`.

## Verificación

### Criterio 1 · el agente envía trazas a Jaeger

```bash
kubectl -n monitoring port-forward svc/jaeger 16686:16686
curl -s http://127.0.0.1:16686/api/services
```

Deben aparecer `form-service`, `promotion-service` y `notification-service`. En la UI
(`http://127.0.0.1:16686`) cada uno aparece en el selector **Service**.

### Criterio 2 · una misma traza recorre los cuatro pasos

El mensaje solo llega hasta notification si la encuesta **tiene síntomas**: hace falta un
cuestionario activo y responder `YES` a una pregunta cuyo texto contenga `fever`, `cough` o
`breathing`.

```bash
kubectl -n circleguard-dev port-forward svc/form-service 8086:8086

# id de una pregunta del cuestionario activo
curl -s http://127.0.0.1:8086/api/v1/questionnaires/active

# enviar la encuesta (anonymousId = cualquier UUID; <ID_PREGUNTA> de la respuesta anterior)
curl -s -X POST http://127.0.0.1:8086/api/v1/surveys -H 'Content-Type: application/json' \
  -d '{"anonymousId":"11111111-1111-1111-1111-111111111111","responses":{"<ID_PREGUNTA>":"YES"}}'
```

En Jaeger, buscar el servicio `form-service` y abrir la traza más reciente. Debe mostrar
spans de `form-service` (la petición `POST /api/v1/surveys` y el envío a `survey.submitted`),
de `promotion-service` (el consumo y el envío de `promotion.status.changed`) y de
`notification-service` (el consumo), todos con el mismo `traceId`.

### Criterio 3 · logs en Loki por servicio y por `traceId`

En Grafana, **Explore → Loki**:

```logql
{app="form-service"}
{namespace="circleguard-dev", app="promotion-service"} |= "trace_id="
{namespace="circleguard-dev"} |= "<traceId>"
```

La última consulta devuelve las líneas de los tres servicios para la misma encuesta. Si no
se ve la etiqueta `app`, `GET /loki/api/v1/labels` (por `port-forward svc/loki 3100`) lista
las etiquetas que sí existen. En Loki, el `trace_id` de cada línea es un enlace que abre la
traza en Jaeger.

## Trade-offs

| Se gana | Se paga |
| --- | --- |
| Se ve el recorrido completo de una encuesta, con tiempos por paso | El agente suma memoria fuera del heap: el límite de los tres pods sube de 512Mi a 768Mi |
| No se toca el código ni las imágenes: el agente se enciende por chart | Un `initContainer` más por pod y una descarga de imagen extra al arrancar |
| Loki indexa solo etiquetas (no el texto de los logs), así que cuesta poco | Buscar por `traceId` es una búsqueda de texto: más lenta que una etiqueta indexada |
| Jaeger all-in-one en memoria es simple de operar | Las trazas se pierden si el pod de Jaeger se reinicia y solo guarda las últimas 20 000 |
| Se reutiliza el Grafana de HU-24 | Depende de que ese stack esté instalado antes |

## Límites

- Probado en esta HU: el renderizado del chart (`helm template` y `helm lint`) y el cambio
  sin efecto en los servicios que no participan. **El despliegue real en el cluster, la traza
  completa en Jaeger y las consultas en Loki necesitan un cluster con los servicios y están
  pendientes de ejecutar**; las capturas de esas pantallas son la evidencia para HU-29.
- `loki-stack` está marcado como obsoleto por Grafana, pero se mantiene instalable y es la
  forma más simple de tener Loki con Promtail en un solo comando. Migrar a Grafana Alloy
  queda como mejora.
- Promtail necesita leer `/var/log/pods` de cada nodo; en un cluster con políticas de
  seguridad que lo impidan habría que ajustarlas.
- Loki guarda en un volumen de 5Gi sin política de retención: si se llena, deja de aceptar
  logs. Para un uso prolongado conviene agregar retención.

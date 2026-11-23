# HU-24 · Evidencia local de observabilidad

Ejecutado el 23 de noviembre de 2026 en el clúster `kind` compartido con
kube-prometheus-stack 65.5.1 (Alertmanager v0.27.0) y el ServiceMonitor de
CircleGuard.

| Comprobación | Resultado |
| --- | --- |
| Targets de CircleGuard en dev | **8/8 `up`** (identity, auth, form, gateway, promotion, notification, file, dashboard) |
| ServiceMonitor | `circleguard-services` selecciona los 3 namespaces |
| Grafana | dashboards técnico y de negocio provisionados por sidecar |
| Alertmanager | `Ready=True`, receiver `discord` configurado vía `configSecret` |
| Alerta disparada | `CircleguardGateDenialSpike` activa desde `2026-11-23T03:55:07Z` |

Archivos: [conteo de targets](targets-count.txt),
[targets por namespace](targets-by-namespace.txt),
[salud de targets](targets-health.txt),
[dashboards](grafana-dashboards.json),
[estado de Alertmanager](alertmanager-status.json) y
[alerta de denegaciones](alert-gate-denial.json).

La alerta se disparó con
`observability/evidencias/disparar-alertas.sh gate-denials`
(600 tokens inválidos al gateway; umbral >1 RED/s durante 2 min).

## Correcciones encontradas con esta prueba

- Alertmanager v0.27.0 no soporta `webhook_url_file`; se cambió a un
  `configSecret` con `webhook_url` (`8ed4d8e`).
- Se corrigieron los nombres de los servicios de Alertmanager/Prometheus
  (`9fc2538`), el umbral de la evidencia (`3f4820a`) y la captura bajo `set -u`
  (`bdf8aec`).

## Pendiente

- **24 targets**: Docker Desktop solo tiene 7.5 GB y el despliegue del segundo
  ambiente agotó la memoria (`OOMKilled`/`Pending`). Se necesitan más recursos
  para stage y prod.
- **Discord real**: se usó un webhook provisional, por lo que Alertmanager
  reporta `AlertmanagerFailedToSendAlerts`. Falta cargar el webhook de Spidey y
  confirmar la notificación.
- Disparar también `CircleguardServiceDown` y `CircleguardHigh5xxRate` con la
  misma evidencia.

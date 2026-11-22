# Rendimiento y seguridad: Locust y OWASP ZAP (HU-26)

## Qué se mide y con qué meta

| Endpoint | Servicio | Meta (propuesta, por confirmar con el equipo) |
| --- | --- | --- |
| `POST /api/v1/gate/validate` | gateway-service | p95 < 500 ms y errores < 1 % con 100 usuarios |
| `POST /api/v1/surveys` | form-service | p95 < 500 ms y errores < 1 % con 100 usuarios |

La prueba está en `locust/locustfile.py`. Cada endpoint se evalúa por separado y Locust termina con código de salida 1 si alguno no cumple, así que se puede usar como criterio automático. La clave del QR se pasa por variable de entorno y no está en el repositorio.

```bash
QR_SECRET=... docker run --rm --network <red> -e QR_SECRET \
  -e GATE_HOST=http://gateway-service:8087 -e FORM_HOST=http://form-service:8086 \
  -v "$PWD/locust":/mnt/locust:ro locustio/locust:2.32.2 -f /mnt/locust/locustfile.py \
  --headless -u 100 -r 20 -t 60s --host http://gateway-service:8087
```

## Resultados (16-nov-2026, máquina de desarrollo)

Entorno: Docker Compose en un solo equipo, con PostgreSQL, Redis, Kafka, gateway-service y form-service, **una réplica de cada uno**, memoria de Java limitada a 320 MB y disco duro mecánico. Mientras tanto estaban corriendo Jenkins, SonarQube y Nexus en la misma máquina. **Los números no representan un ambiente en AWS.** 100 usuarios, 60 segundos, 0 errores en ambas corridas.

| Corrida | Endpoint | Solicitudes | p50 | p95 | Errores | Meta |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `/gate/validate` | 3.718 | 5 ms | 9 ms | 0 % | Cumple |
| 1 | `/surveys` | 1.201 | 440 ms | 2.900 ms | 0 % | **No cumple** |
| 2 | `/gate/validate` | 3.124 | — | 7 ms | 0 % | Cumple |
| 2 | `/surveys` | 967 | — | 6.200 ms | 0 % | **No cumple** |

## Interpretación

- **Rendimiento.** La validación del QR es muy rápida (p95 menor a 10 ms): solo verifica una firma y consulta Redis. El envío de encuestas no cumple la meta y además varía mucho entre corridas (2,9 s y 6,2 s de p95), aunque no falla ninguna solicitud. La causa **no se aisló**. Lo probable, por revisar, es una combinación de: (1) cada encuesta es una transacción con escritura en PostgreSQL y el disco mecánico es lento; (2) el pool de conexiones por defecto (10) con 100 usuarios simultáneos forma cola; (3) una sola réplica con poca memoria.
- **Cobertura y calidad** (Parte 1, build del tag v1.0.0): cobertura de líneas auth 74,8 %, identity 85,0 %, form 63,3 %, gateway 91,7 %, promotion 78,0 %, todas sobre el mínimo de 60 %. El quality gate de SonarQube salió en OK.
- **Seguridad.** gitleaks y Trivy están en el pipeline (HU-20) y el baseline de ZAP está configurado en la etapa `ZAP stage` del Jenkinsfile. **Contra stage todavía no se ha ejecutado**, porque ese ambiente no existe. Sí se probó la herramienta (16-nov-2026) con `zap-baseline.py` (imagen `ghcr.io/zaproxy/zaproxy:2.15.0`, mismas opciones que el pipeline) contra el `gateway-service` desplegado en kind por `port-forward` (HTTP, sin TLS ni Ingress): generó el HTML y el JSON, con 2 alertas: «ZAP is Out of Date» (Medium, es de la propia herramienta) y «Storable and Cacheable Content» (informativa). Es poca evidencia: el baseline es pasivo y el gateway sin autenticar casi no expone páginas. Cuando exista stage, el reporte HTML queda publicado en el build.

## Mejoras propuestas

1. Repetir la prueba en stage (AWS) antes de dar la meta por incumplida; el resultado local no es concluyente.
2. Si `/surveys` sigue lento: subir el tamaño del pool de conexiones, poner `form-service` bajo HPA como gateway y promotion, y revisar el disco de la base de datos.
3. Confirmar la meta (p95 y usuarios) con el equipo; hoy es una propuesta.
4. Ejecutar el baseline de ZAP en stage y documentar las alertas que salgan.

## Pendiente

- Correr ZAP contra stage (depende de HU-19 y HU-21).
- Repetir Locust en stage y actualizar la tabla.

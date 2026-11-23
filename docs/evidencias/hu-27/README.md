# HU-27 · Evidencia de rollback local

Ejecutado el 22 de noviembre de 2026, 17:05 Colombia, con Helm 3.16.4 y kind
0.26.0 (Kubernetes 1.32.0). Contexto `kind-circleguard-hu27`. Imagen sintética
BusyBox, chart real `deploy/helm/circleguard-service`, release `web`.

| Comprobación | Resultado |
| --- | --- |
| Versión antes | v0.2.0, revisión 2, 1/1 Ready |
| Operación | `helm rollback web 1 -n circleguard-dev --wait --timeout 10m --cleanup-on-fail` |
| Versión después | v0.1.0, nueva revisión 3, 1/1 Ready |
| HTTP antes/después | 200; el cuerpo cambia de v0.2.0 a v0.1.0 |
| Recuperación hasta humo correcto | **12.717 s** |
| Stage y prod | Consultados; web no desplegado |

Archivos: [antes](before.json), [después](after.json), [resultado y tiempo](rollback.json),
[tabla anterior](versions-before.md), [tabla posterior](versions-after.md),
[HTTP anterior](http-before.txt) y [HTTP posterior](http-after.txt).

Los JSON guardan las imágenes y sus digests observados. No contienen kubeconfig,
Secrets, estado Terraform ni values completos. Es una prueba local del mecanismo;
la validación con los nueve servicios y la aprobación de prod en Jenkins sigue
pendiente. Las etiquetas v0.1.0/v0.2.0 de esta demo no se publicaron en GitHub
ni Nexus. El clúster es desechable y se elimina al terminar.

# HU-27 · Versiones por ambiente y rollback verificable

Rama `hu-27`, creada desde `feat/hu-19-pipeline-terraform-helm`. Conserva SemVer
y Release Please de HU-03. Usa los releases por servicio de HU-18/HU-19.

## Notas de cada versión publicada

Release Please genera `CHANGELOG.md`, tag `vX.Y.Z` y GitHub Release al integrar
su PR en `main`. Antes de publicar imágenes, `circleGuardPublish.groovy` ejecuta
`scripts/check-release-notes.mjs`: exige una release publicada de ese mismo tag
con notas no vacías y archiva `release-notes.md` en Jenkins. Un error de GitHub,
una release inexistente, un borrador o notas vacías bloquean la publicación.
La consulta usa el repositorio de `origin` o `GITHUB_REPOSITORY`; en repositorios
privados requiere un `GITHUB_TOKEN` de lectura inyectado por el operador.

Se verificó la release existente
[v1.0.0](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/releases/tag/v1.0.0)
con el script. No se crearon releases ni tags públicos para la demostración.

## Tabla observada de versiones

```bash
export KUBECONFIG_DEV=/ruta/segura/dev.kubeconfig
export KUBECONFIG_STAGE=/ruta/segura/stage.kubeconfig
export KUBECONFIG_PROD=/ruta/segura/prod.kubeconfig
node deploy/k8s/releases.mjs snapshot all .local-evidence/hu-27/inventario
```

El script consulta Helm, Deployments y pods. Produce `versions.md` (tabla de
dev/stage/prod y detalle de los nueve servicios) y `versions.json` (imagen,
digest observado, revisión, réplicas y contexto). Muestra una mezcla de versiones
si existe; nunca supone que las tres están en la última release.

Un ambiente no consultado o inaccesible se señala como tal; un servicio ausente
aparece sin desplegar. No se interpreta un archivo de values como prueba de
lo que corre. Si los tres namespaces comparten clúster, puede usarse un solo
`KUBECONFIG`; si son tres clústeres, configurar los tres `KUBECONFIG_*`.

Jenkins registra el inventario después de cada despliegue Helm. La etapa
`Versiones por ambiente` consulta los tres kubeconfigs al finalizar una release
o rollback. `post.always` archiva `.local-evidence/hu-27/**`, incluso ante fallos.

## Rollback real de Helm

En Jenkins: `DEPLOY_TARGET=helm`, `ROLLBACK_TAG=vX.Y.Z`, `ROLLBACK_ENV=dev|stage|prod`
y `SKIP_DEPLOY=false`. Prod conserva la aprobación del usuario `aprobador` y
registra `approval-prod-rollback.txt`. No se ejecutan las etapas normales de
publicación/promoción al solicitar rollback, aunque el job corresponda a un tag.

El paso `circleGuardHelm.rollback()`:

1. Guarda el antes: imágenes, revisiones y pods de los nueve servicios.
2. Busca, en **todos** los historiales Helm, una revisión exitosa cuyo
   `image.tag` coincida con el objetivo. Omite revisiones fallidas/pendientes.
   Si algún servicio no tiene ese historial, no inicia ningún rollback.
3. Ejecuta `helm rollback <servicio> <revisión> --wait --timeout 10m --cleanup-on-fail`
   y espera `kubectl rollout status` de cada Deployment.
4. Comprueba que la imagen restaurada coincide, la generación está observada,
   los pods de esa imagen están Ready y Helm está `deployed`.
5. Exige HTTP 2xx en `https://SITE_ADDRESS/` y guarda el después.

La recuperación se mide con reloj monotónico desde el primer `helm rollback`
hasta el humo satisfactorio, incluyendo esperas y comprobaciones. Es la duración
de recuperación de esta operación; no incluye detección del incidente ni la
espera de aprobación. Un fallo conserva `before.json`, `after.json` si se puede
consultar y `rollback.json` con `status=failed` y `recoverySeconds=null`.

No reconstruye imágenes, no ejecuta Terraform, no cambia Secrets ni revierte
middleware/datos. Un rollback de varios releases **no es una transacción**:
si uno falla después de restaurar otros, el resultado es parcial y hay que
revisar la evidencia. Solo revertir versiones de aplicación compatibles con
el esquema de datos actual. Se necesita conservar el historial Helm y las
imágenes inmutables en Nexus; una versión borrada no puede recuperarse.

Uso manual equivalente (los nueve servicios por defecto):

```bash
export KUBECONFIG=/ruta/segura/dev.kubeconfig
node deploy/k8s/releases.mjs rollback dev v1.2.3 https://dev.ejemplo.edu/ \
  .local-evidence/hu-27/rollback-dev
```

No se imprimen ni guardan values/manifests completos o credenciales. HTTPS
verifica el certificado. Para una CA privada, configurar `NODE_EXTRA_CA_CERTS`;
no desactivar globalmente la verificación TLS.

## Demostración local ejecutada

Se ejecutó un rollback **real**, en kind, del release `web` con dos imágenes
sintéticas distintas creadas con `deploy/k8s/demo/Dockerfile`. No son releases
publicadas de CircleGuard ni certifican el funcionamiento de sus nueve servicios.

| Ambiente consultado en la demo | Antes | Después | Alcance |
| --- | --- | --- | --- |
| dev | web v0.2.0 · revisión 2 | web v0.1.0 · revisión 3, restaurada desde 1 | 1/1 Ready, HTTP 200 |
| stage | Sin desplegar | Sin desplegar | Namespace consultado |
| prod | Sin desplegar | Sin desplegar | Namespace consultado |

**Recuperación: 12.717 s.** Inicio 22-nov-2026 17:05:40.719 Colombia
(22:05:40.719 UTC). El contenido HTTP cambió de `v0.2.0` a `v0.1.0`.
La [evidencia de la demo](evidencias/hu-27/README.md) conserva JSON, digests,
tablas y respuestas HTTP. El clúster desechable se elimina al cerrar las pruebas;
la tabla es un registro histórico, no un ambiente que siga encendido.

Para reproducir con Docker, kind, Helm 3, kubectl y Node >=22 en el PATH:

```bash
kind create cluster --name circleguard-hu27 --kubeconfig /ruta/segura/hu27.kubeconfig
export KUBECONFIG=/ruta/segura/hu27.kubeconfig
kubectl apply -f deploy/k8s/namespaces.yaml
for version in v0.1.0 v0.2.0; do
  docker build --build-arg APP_VERSION="$version" -t "circleguard/rollback-demo:$version" -f deploy/k8s/demo/Dockerfile .
  kind load docker-image "circleguard/rollback-demo:$version" --name circleguard-hu27
  helm upgrade --install web deploy/helm/circleguard-service -n circleguard-dev \
    -f deploy/helm/values/web.yaml --set image.repository=circleguard/rollback-demo \
    --set image.tag="$version" --set ingress.enabled=false --wait --timeout 3m
done
# En otra terminal; el proxy de API sigue disponible al reemplazar el pod.
kubectl proxy --address=127.0.0.1 --port=18128
# En la terminal inicial:
node deploy/k8s/releases.mjs rollback dev v0.1.0 \
  http://127.0.0.1:18128/api/v1/namespaces/circleguard-dev/services/web:8080/proxy/ \
  .local-evidence/hu-27/demo web
node deploy/k8s/releases.mjs snapshot all .local-evidence/hu-27/demo-after web
kind delete cluster --name circleguard-hu27
# Cerrar kubectl proxy con Ctrl+C.
```

HTTP solo se acepta para localhost en la demo. El argumento final `web` limita
la operación explícitamente a ese release. No usarlo para afirmar rollback de
toda la aplicación.

## Validación y pendientes

`node --test deploy/k8s/releases.test.mjs scripts/check-release-notes.test.mjs`:
**10 pruebas aprobadas**. Cubren selección del historial, prevalidación conjunta,
fallo parcial, cambios concurrentes, readiness, humo y notas publicadas. También se
ejecutó la demo en Kubernetes y se consultó la release v1.0.0 real.

### Ejecución en AWS (24-nov-2026)

Lo que faltaba se completó en los ambientes reales:

- **Pipeline integrado:** la release `v1.1.2` (#2) desplegó los nueve servicios en
  dev, stage y prod y registró el inventario de los tres.
- **Rollback completo en dev:** v1.1.2 → v1.1.1 en el build #3 (recuperación
  **581,2 s**) y v1.1.1 → v1.1.2 en el #4 (**567,1 s**), ambos con humo y TLS correctos.
- **Stage y prod:** no se pudo hacer rollback, porque solo tienen v1.1.2 en su
  historial y v1.1.0 no se usa por ser vulnerable.

Detalle y archivos: [evidencias/sprint-2.md](evidencias/sprint-2.md#hu-27--versiones-por-ambiente-y-rollback).
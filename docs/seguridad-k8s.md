# HU-21 · RBAC, TLS y política de acceso

Esta HU restringe permisos en Jenkins y Kubernetes, protege el tráfico de
entrada con TLS y cierra por defecto la autenticación de los servicios que
exponían rutas sensibles.

## 1. Roles de Jenkins

El plugin `role-strategy` define tres roles globales en `infra/ci/jenkins/casc.yml`:

| Rol | Usuario | Permisos principales | Uso |
| --- | --- | --- | --- |
| `admin` | `CI_JENKINS_USER` | `Overall/Administer` | Administración completa del CI |
| `developer` | `CI_DEVELOPER_USER` | `Overall/Read`, `Job/Read`, `Job/Build`, `Job/Cancel`, `Job/Workspace`, `Run/Replay`, `SCM/Tag` | Construir y diagnosticar jobs, sin cambiar credenciales ni seguridad |
| `approver` | `CI_APPROVER_USER` | `Overall/Read`, `Job/Read`, `Job/Build`, `Run/Replay` | Aprobar el despliegue a prod |

Variables nuevas en `ci.env`:

```bash
CI_DEVELOPER_USER=desarrollador
CI_DEVELOPER_PASSWORD=<contrasena-larga>
```

Sin `CI_DEVELOPER_PASSWORD`, el bootstrap de Jenkins falla a propósito: no se
crean usuarios con contraseñas vacías. Después de cambiar `ci.env`, reconstruye
la imagen/instancia del CI (`infra/bootstrap/bootstrap-ci.sh`).

## 2. ServiceAccounts de Kubernetes

Cada release del chart crea su propia `ServiceAccount`:

- `serviceAccount.create: true`
- `serviceAccount.automount: false`
- El Deployment usa `serviceAccountName: <release>` y `automountServiceAccountToken: false`.
- No hay `Role` ni `RoleBinding`: los servicios no llaman a la API de Kubernetes,
  así que su permiso efectivo es ninguno.

Se puede verificar con:

```bash
helm template gateway-service deploy/helm/circleguard-service \
  -f deploy/helm/values/gateway-service.yaml \
  -f deploy/helm/envs/dev.yaml --set image.tag=v0.0.1 \
  | grep -E 'ServiceAccount|automountServiceAccountToken|serviceAccountName'
```

## 3. TLS con cert-manager

Instala cert-manager y el emisor autofirmado:

```bash
bash deploy/k8s/cert-manager.sh
```

El script instala cert-manager con Helm y aplica
`deploy/k8s/issuers.yaml` (`ClusterIssuer/circleguard-selfsigned`). Los tres
ambientes piden su certificado con:

```yaml
ingress:
  annotations:
    cert-manager.io/cluster-issuer: circleguard-selfsigned
  tls:
    - secretName: circleguard-dev-tls
      hosts:
        - dev.circleguard.local
```

Verificación con curl:

```bash
bash deploy/k8s/verificar-tls.sh dev.circleguard.local
```

El script comprueba `/` y `/api/v1/health` con `curl -k` e imprime el sujeto,
emisor y fechas del certificado. La prueba de humo del pipeline (`circleGuardSmoke`)
también imprime el certificado del ambiente.

Como la CA es autofirmada, los navegadores no confían en ella y las pruebas usan
`-k`. Cuando exista un dominio público, se puede agregar un `ClusterIssuer` de
Let's Encrypt y cambiar la anotación de los Ingress.

## 4. Política de acceso

### auth-service

Cerrado por defecto (`anyRequest().authenticated()`). Rutas públicas:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/qr/verify`
- `POST /api/v1/auth/visitor/handoff`
- `/actuator/health/**` y `/actuator/prometheus`

`/api/v1/users/permissions/**` es interno: exige `X-Internal-Api-Key` y ya no se
publica en el Ingress.

### identity-service

- `POST /api/v1/identities/visitor`: público (registro previo al login).
- `POST /api/v1/identities/map`: exige JWT o `X-Internal-Api-Key`.
- `/actuator/health/**` y `/actuator/prometheus`: públicos.
- Resto: autenticado.

La app móvil envía el JWT al buscar identidades. auth-service usa la clave
interna durante el login, porque todavía no existe un JWT en ese momento.

### promotion-service

- `/api/v1/admin/**`: exige autenticación y el permiso `dashboard:view`.
- `/api/v1/health/**`: autenticado.
- `/actuator/health/**` y `/actuator/prometheus`: públicos.
- Las rutas de círculos, malla, encuentros y ubicación conservan su política
  actual para no romper los flujos existentes.

## 5. Clave interna entre servicios

`INTERNAL_API_KEY` se genera en `deploy/new-env.sh`, se guarda en el Secret
`circleguard-secrets` y llega a auth, identity y notification por `secretEnv`.
Los clientes envían la cabecera `X-Internal-Api-Key`; si no coincide, la petición
queda sin autenticar y el servicio responde 401.

## 6. Evidencias y pruebas

- `gradle :services:circleguard-auth-service:test :services:circleguard-identity-service:test :services:circleguard-promotion-service:test :services:circleguard-notification-service:test`
- Pruebas de 401/403/200 para las rutas protegidas.
- `npm test` en `mobile`.
- `helm lint` y `helm template` de los 9 values en los 3 ambientes.
- `bash deploy/k8s/verificar-tls.sh <host>` contra un Ingress real.

# HU-21 · Evidencia local de RBAC y TLS

Ejecutado el 23 de noviembre de 2026 sobre el clúster `kind` compartido de
evidencias (Kubernetes 1.32.0). cert-manager v1.16.2 y el `ClusterIssuer`
autofirmado `circleguard-selfsigned` quedaron `Ready`.

| Comprobación | Resultado |
| --- | --- |
| `ClusterIssuer/circleguard-selfsigned` | `Ready=True` |
| `Certificate/circleguard-dev-tls` | `Ready=True` |
| HTTPS en `dev.circleguard.local` | `/` → 200 y `/api/v1/health` → 401 |
| Certificado servido | `notBefore=Nov 23 02:13:41 2026`, `notAfter=Feb 21 02:13:41 2027` |
| ServiceAccounts de aplicación | `automountServiceAccountToken: false` |
| Ingress | clase `traefik` (la misma que declaran los values por ambiente) |

Archivos: [certificados](certificates.txt), [TLS](tls.txt),
[openssl](cert-openssl.txt) y [ServiceAccounts](serviceaccounts.txt).

La prueba usó `curl -k --resolve dev.circleguard.local:443:127.0.0.1` porque el
archivo `hosts` de Windows no se modificó (requiere administrador).

## Pendiente

- Probar `/api/v1/identities/map` sin JWT → 401 y `/admin/**` sin
  `dashboard:view` → 403 con un usuario sembrado.
- Confirmar que auth-service no publica `/api/v1/users` en el Ingress.
- Validar los roles `admin`, `developer` y `approver` en Jenkins real.
- Repetir la emisión de certificados en stage y prod (requiere desplegarlos).

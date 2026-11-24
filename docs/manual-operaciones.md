# Manual de operaciones de CircleGuard

Corresponde al despliegue en AWS verificado el 24 de noviembre de 2026: release
**v1.1.2** en dev, stage y prod. Proyecto `001784022331`, región seleccionada
`us-east-2`, perfil `circleguard`. Ningún comando de este manual imprime secretos;
mantener esa regla al adaptarlos.

## 1. Mapa del despliegue

| Componente | Dirección | Acceso |
| --- | --- | --- |
| Jenkins | https://jenkins.18-220-203-182.sslip.io/ | Usuarios `CI_JENKINS_USER` (admin), `CI_DEVELOPER_USER` (operador), `aprobador` (solo aprueba prod) |
| Nexus (registro Docker) | https://nexus.18-220-203-182.sslip.io/, registro `registry.18-220-203-182.sslip.io` | `CI_NEXUS_USER` |
| SonarQube | https://sonar.18-220-203-182.sslip.io/ | `CI_SONAR_*` |
| CircleGuard dev | https://dev.3-143-61-218.sslip.io | Cuenta de pruebas E2E |
| CircleGuard stage | https://stage.3-134-68-77.sslip.io | — |
| CircleGuard prod | https://prod.13-59-35-181.sslip.io | — |
| Grafana / Prometheus / Alertmanager | Solo por `kubectl port-forward` (sección 6) | `grafana-admin-password` |

Las URL usan [sslip.io](https://sslip.io): el nombre resuelve a la EIP que contiene, así
que no hay DNS que administrar. Las EIP son fijas; la IP pública de los workers cambia
en cada arranque, pero no se usa en ninguna URL.

| Máquina (m7i-flex.large) | Instancia | Función |
| --- | --- | --- |
| circleguard-ci | i-0aaea7211026e03d7 | Jenkins, Nexus, SonarQube y Caddy en Docker Compose |
| circleguard-dev / -worker-1 | i-0713e7f73c304ae7a / i-02ed4119a539dbc42 | k3s dev |
| circleguard-stage / -worker-1 | i-0831343cc07c01f08 / i-0843ecd02295c7763 | k3s stage |
| circleguard-prod / -worker-1 | i-01b37806af3455916 / i-0b07b0fc537c0e392 | k3s prod |

## 2. Secretos y credenciales

Todo vive fuera de Git en `.local-evidence/credentials/` y en Jenkins Credentials:

| Archivo local | Credencial de Jenkins | Uso |
| --- | --- | --- |
| `ci.env` | (usuarios de JCasC, `circleguard-github`, `nexus-registry`, `sonarqube-*`) | Operadores de CI, token de GitHub |
| `<env>.kubeconfig` | `kubeconfig-dev`, `-stage`, `-prod` | API de cada clúster |
| `circleguard-env-<env>` | `circleguard-env-dev`, `-stage`, `-prod` | Secretos de la aplicación; `crear-secretos.sh` los vuelca en el Secret `circleguard-secrets` |
| `grafana-admin-password` | `grafana-admin-password` | Administrador de Grafana |
| — | `circleguard-e2e-health` | Cuenta exclusiva de las pruebas E2E |
| — | `discord-webhook` | **No existe todavía** (sección 7) |

Reglas:

- Subir un archivo cambiado a Jenkins con el importador, que solo confirma IDs:
  ```powershell
  .\infra\ci\jenkins\import-secret-files.ps1 -JenkinsUrl https://jenkins.18-220-203-182.sslip.io/ `
    -CredentialEnvFile .\.local-evidence\credentials\ci.env `
    -SecretDirectory .\.local-evidence\credentials -Environment stage
  ```
- Para comprobar que Jenkins tiene la misma versión, comparar el SHA-256 de la
  credencial (por `scriptText`, devolviendo solo el hash) con `Get-FileHash` local.
- No regenerar `VAULT_*` ni las claves de cifrado de identity cuando ya hay datos.
- Rotación de la clave de Neo4j (hecha el 24-nov en stage y prod, porque el chart la
  imprimió en el log del build `v1.1.2` #2): `ALTER CURRENT USER SET PASSWORD` dentro
  del pod `neo4j-0` pasando las claves por stdin; actualizar `circleguard-secrets` y
  `neo4j-auth`, el `circleguard-env-<env>` local, importarlo en Jenkins y reiniciar
  `promotion-service`. PR #61 evita que el chart vuelva a imprimirla.

## 3. CI: Jenkins, Nexus y SonarQube

- **Arranque/parada:** la VM de CI no se detiene durante la entrega. Si se reinicia,
  `docker compose -f infra/ci/compose.aws.yml up -d` en la VM levanta el stack; Caddy
  renueva los certificados.
- **Jobs:**

  | Job | Qué hace |
  | --- | --- |
  | `circleguard` (multibranch) | PR y `main`: calidad, Sonar, seguridad, Terraform plan. Tags `vX.Y.Z`: imágenes, despliegue dev → stage → prod |
  | `circleguard-observability` | `instalar`, `verificar` o `alertas-*` en un clúster |
  | `circleguard-preflight` | Comprobación del stack de CI |
  | `circleguard-handover` | Snapshots `handover-*` (no son releases) |
  | `circleguard-ensayo-despliegue` | Ensayo no oficial de humo/E2E/ZAP en dev |

- **Filtro de ramas:** el multibranch descubre `main PR-* v1.1.2`
  (`CI_BRANCH_INCLUDES`). Para una release nueva, agregar su tag al filtro en el
  archivo de JCasC y en `ci.env`; guardar `config.xml` dispara un escaneo.
- **Tag recién descubierto:** si el tag tiene menos de 7 días, Jenkins lo construye
  solo con parámetros por defecto. Abortar ese build (`POST /stop` con crumb nuevo; un
  403 se resuelve repitiendo) y lanzar `buildWithParameters` con los oficiales.
- **Un solo executor:** los builds se encolan; no lanzar dos pruebas destructivas a la vez.

## 4. Release y despliegue

1. Los cambios entran a `main` por PR con aprobación de otra persona y checks verdes.
2. Release Please abre el PR de versión; al integrarlo crea el tag `vX.Y.Z` y la
   GitHub Release con notas (requeridas por `check-release-notes.mjs`).
3. En `circleguard » vX.Y.Z` ejecutar con:
   `BUILD_ALL=true`, `DEPLOY_TARGET=helm`, `DEPLOY_MIDDLEWARE=true`, `SKIP_DEPLOY=false`,
   `ROLLBACK_TAG` vacío.
4. El pipeline construye las 9 imágenes, Trivy bloquea CRITICAL (VEX solo para los
   dos CVE de spring-webmvc 6.2.19 ligados a su digest; revisar antes del 27-nov),
   publica en Nexus y despliega dev → humo → E2E → stage → humo → ZAP.
5. Se detiene en **Aprobación prod**: entrar a Jenkins como `aprobador` y pulsar
   **Desplegar prod**. Queda `approval-prod.txt` en los artefactos.
6. Terraform apply prod, Helm, humo y la etapa **Versiones por ambiente**.

Antes del apply, `check-plan.mjs` rechaza cualquier plan con bajas o reemplazos. Si
aparece uno, se revisa la causa; no se debe quitar el control.

## 5. Clústeres k3s y Helm

```powershell
$env:KUBECONFIG = "$PWD\.local-evidence\credentials\dev.kubeconfig"
kubectl get nodes
kubectl -n circleguard-dev get deploy,statefulset,ingress,certificate
```

- 9 releases de aplicación (`identity`, `auth`, `promotion`, `form`, `gateway`,
  `notification`, `file`, `dashboard`, `web`) y middleware (`postgres`, `redis`,
  `neo4j` con Helm; `kafka` y `openldap` con manifiestos).
- Historial: `kubectl -n circleguard-<env> get secrets -l owner=helm` (no hace falta
  tener Helm instalado localmente).
- Inventario de versiones: `node deploy/k8s/releases.mjs snapshot all <salida>` con
  `KUBECONFIG_DEV/STAGE/PROD`.
- **Encender/detener stage o prod:**
  ```powershell
  aws ec2 start-instances --region us-east-2 --profile circleguard --instance-ids i-01b37806af3455916 i-0b07b0fc537c0e392
  aws ec2 wait instance-running --region us-east-2 --profile circleguard --instance-ids i-01b37806af3455916 i-0b07b0fc537c0e392
  # luego: kubectl get nodes (ambos Ready) y el humo de la sección 8
  aws ec2 stop-instances --region us-east-2 --profile circleguard --instance-ids i-01b37806af3455916 i-0b07b0fc537c0e392
  ```
  Stage: `i-0831343cc07c01f08 i-0843ecd02295c7763`. Terraform ignora
  `associate_public_ip_address`, así que detener un worker ya no provoca un reemplazo.

## 6. DNS, TLS y observabilidad

- **TLS:** cert-manager 1.21.3 con el ClusterIssuer `circleguard-letsencrypt` (ACME
  HTTP-01 por Traefik). Comprobar: `kubectl -n circleguard-<env> get certificate` y
  `openssl s_client -connect <host>:443 -servername <host> </dev/null | openssl x509 -noout -issuer -enddate`.
  Vencen el 22-feb-2027; cert-manager los renueva solo.
- **Monitoreo:** `kube-prometheus-stack` en el namespace `monitoring` de cada clúster.
  ```powershell
  kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80
  kubectl -n monitoring port-forward svc/prometheus-operated 9090:9090
  kubectl -n monitoring port-forward svc/alertmanager-operated 9093:9093
  ```
  Dashboards `CircleGuard técnico` y `negocio`. Reglas en `observability/alerts/rules.yaml`
  (`promtool test rules` en cada ejecución del job).
- **Problema conocido:** auth y dashboard no exponían `/actuator/prometheus` (401/404)
  porque `resilience.yml` reemplazaba la lista de endpoints. Corregido en **PR #64**;
  hasta desplegarlo, `CircleguardServiceDown` de esos dos targets y
  `CircleguardAvailabilityLow` aparecen activas en dev, stage y prod.
- **Trazas y logs (HU-25):** Jaeger/Loki no están instalados en los clústeres de AWS;
  los servicios muestran errores del exportador OTLP en sus logs. Ver
  [trazas-y-logs.md](trazas-y-logs.md) para instalarlos.

## 7. Alertas y notificaciones

- **Probar alertas** (nunca en prod): `circleguard-observability` con
  `ENVIRONMENT=dev|stage` y `ACTION=alertas-all`. El script exige que cada alerta
  esté inactiva antes de provocarla, la identifica por etiquetas, guarda el número
  de réplicas original y lo restaura siempre. Evidencia en `observability-report/`.
- **Discord (pendiente):** no hay webhook. Para activarlo sin exponer la URL:
  1. El dueño del canal crea el webhook y lo escribe en `CI_DISCORD_WEBHOOK=` de
     `ci.env` con un editor. No se pega en el chat, en Git ni en comandos.
  2. Crear la credencial `discord-webhook` (usuario/clave; la clave es la URL) en
     Jenkins → Credentials, o con `infra/ci/init/configure.sh`.
  3. Ejecutar `circleguard-observability` `ACTION=instalar` por clúster: guarda la URL
     en un Secret de Alertmanager.
  4. Repetir `alertas-all` en dev y confirmar la recepción en el canal.

  Hasta entonces las alertas quedan solo en Alertmanager. **El correo del pipeline tampoco funciona:** el SMTP configurado respondió `530 Authentication Required` (Gmail exige una contraseña de aplicación). Se corrige en Jenkins → System → E-mail Notification con una credencial de aplicación, sin escribirla en Git.

## 8. Humo y E2E

```powershell
foreach ($h in 'dev.3-143-61-218.sslip.io','stage.3-134-68-77.sslip.io','prod.13-59-35-181.sslip.io') {
  foreach ($p in '/','/api/v1/health') {
    try { $s = (Invoke-WebRequest -UseBasicParsing "https://$h$p").StatusCode } catch { $s = $_.Exception.Response.StatusCode.value__ }
    "$h$p -> $s"   # esperado: / 200 y /api/v1/health 401 (ruta protegida)
  }
}
```

E2E (Playwright, solo dev): lo ejecuta el pipeline del tag con la credencial
`circleguard-e2e-health`.

## 9. Rollback

- En `circleguard » vX.Y.Z` con `ROLLBACK_TAG=<versión anterior>`, `ROLLBACK_ENV=dev|stage|prod`,
  `DEPLOY_TARGET=helm`. Prod pide la aprobación de `aprobador`.
- `deploy/k8s/releases.mjs` busca en los 9 historiales una revisión `deployed`/`superseded`
  con ese `image.tag`. Si falta en alguno, no toca ninguno.
- Mide la recuperación desde el primer `helm rollback` hasta el humo correcto y guarda
  `before.json`, `after.json` y `rollback.json`.
- Una recuperación solo se declara con readiness **y** humo. No usar `v1.1.0` (vulnerable).
- Para volver adelante se usa el mismo mecanismo con la versión nueva (`ROLLBACK_TAG=v1.1.2`).
- Solo dev tiene historial con dos releases (`v1.1.1` rev. 1, `v1.1.2` rev. 2). Stage y
  prod solo tienen `v1.1.2`.

## 10. Seguridad operativa

- Las bajas de Terraform se bloquean en el pipeline (`check-plan.mjs`); CI tiene
  `prevent_destroy`. No quitar ninguno de los dos controles para "destrabar" un build.
- Jenkins usa `CircleGuardCiTerraformRole` (sin access keys). Terraform local usa
  `circleguard-terraform` con `credential_process` y `AWS_EC2_METADATA_DISABLED=true`.
- Gitleaks y Trivy corren en cada build; la excepción VEX está ligada al digest y vence.
- La aprobación de prod es exclusiva de `aprobador`; nadie más debe usar esa cuenta.
- Logs de Jenkins: no imprimen secretos. Si una herramienta lo hace (como el chart de
  Neo4j), rotar la clave y corregir el script.

## 11. Cierre y retiro de recursos

Después de las **23:59 del miércoles 25-nov (Colombia)** y con la sustentación cerrada.
Costos y orden detallado en [costos.md](costos.md#5-plan-de-apagado-y-cierre):

1. Guardar evidencia sin secretos.
2. `terraform plan -destroy` y apply en prod → stage → dev (perfil `circleguard-terraform`).
3. Retirar `prevent_destroy` de CI en un commit explícito, respaldar si hace falta y destruir CI.
4. Destruir el backend al final.
5. Verificar instancias, EBS, EIP, logs (también `us-east-1`) y buckets.
6. **No cerrar el proyecto AWS** como sustituto de la limpieza.

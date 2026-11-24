# CircleGuard - Handover de estabilización y despliegue

**Destinatario:** responsable de infraestructura, CI/CD y webhook de Discord; continuidad con Claude Code.

**Fecha de auditoría:** 23 de noviembre de 2026, zona America/Bogota. Las fechas UTC de servidores pueden mostrar el 24 de noviembre.

**Repositorio:** https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios

**Rama de trabajo:** `fix/release-1.1.0-estabilizacion`. Objetivo posterior: integración por PR a `main` y release oficial verificada.

## 1. Leer primero: estado y reglas de continuidad

La infraestructura y el middleware de dev están operativos. Esto **no significa que la aplicación esté desplegada**: al comenzar esta auditoría no había imágenes en Nexus, releases de aplicación, Ingress ni certificados de aplicación en dev.

El proceso anterior terminó de construir nueve imágenes locales con nombre `v1.1.1`, pero varios escaneos fallaron. Esas imágenes NO son una release oficial y no deben publicarse con ese tag: `version.txt` sigue en `1.1.0` y aún no existe `v1.1.1` en GitHub.

Esta entrega incorpora un flujo separado para snapshots `handover-<12 caracteres del commit>`. Su job prueba el backend, construye las nueve imágenes, exige que todas aprueben Trivy y solo entonces publica en Nexus. El resultado final, commit de código y referencias de imágenes se registran en `docs/evidence/handover-status.json` cuando termine la ejecución.

Reglas para Claude y el operador:

- Leer `CLAUDE.md`, `AGENTS.md`, esta guía y `docs/pendientes-integracion.md`, sección 7.
- No ejecutar `git reset --hard`, `git clean` ni descartar modificaciones locales. Auditar `git status` y el diff antes de actuar.
- Mantener los secretos fuera del repositorio, PDF, chat, capturas y logs. No imprimir archivos de credenciales ni kubeconfigs.
- Usar solamente el proyecto AWS `001784022331`, perfil `circleguard` y recursos regionales en `us-east-2`.
- No cambiar al plan de pago ni sustituir recursos por tipos no admitidos para forzar un despliegue.
- No mover ni recrear `v1.1.0`: ya existe en `145f0a7` y no contiene la estabilización de `69cba57`.
- No presentar un snapshot, una imagen construida o un `helm template` como evidencia de despliegue completo.

## 2. Qué se hizo y qué sigue pendiente

### Hecho y comprobado

La base de estabilización `69cba57` contiene las correcciones de handoff HMAC, JWT y permisos, Promotion default-deny, transacciones y eventos Kafka, MERGE de UserNode, archivos y formularios, Resilience4j HU-23, móvil y wrapper Gradle. No se revirtieron esas correcciones.

Se creó el backend S3/DynamoDB; CI con Jenkins, SonarQube y Nexus; y dev con un servidor k3s y un worker. Ambos nodos están Ready y PostgreSQL, Redis, Kafka, Neo4j y OpenLDAP están disponibles. Se verificó por SQL el permiso de migración sobre las cinco bases.

Se configuraron HTTPS público de CI, credenciales GitHub, token/callback de Sonar, rol de instancia de Jenkins sin access keys estáticas, permisos limitados de nodos, secretos dev y kubeconfig dev. El usuario aceptó la licencia de Nexus. Los últimos planes Terraform de CI y dev terminaron con exit 0 y sin cambios.

Se actualizó Boot a 3.5.16, Tomcat a 10.1.62, Netty a 4.1.137.Final y FreeMarker a 2.3.35. Notification usa RestClient en lugar del starter WebFlux; conserva el envío bloqueante dentro de su ejecución asíncrona y propaga fallos para retry. Se añadieron pruebas de envío, error HTTP y modo mock. Las pruebas unitarias y JaCoCo pasan con estos cambios.

El nuevo control de planes impide bajas/reemplazos desde el pipeline. La evaluación XSLT/SSE está acotada a Spring MVC 6.2.19, a un digest OCI y a funciones no utilizadas; no es un parche del paquete. Consultar `docs/seguridad-spring-demo.md`.

### Pendiente antes de declarar main desplegado

1. Confirmar el resultado final del job de snapshots y su inventario en Nexus.
2. Cargar el webhook de Discord existente. **La auditoría encontró el campo vacío**, aunque el contexto previo lo daba por configurado.
3. Completar la instalación de observabilidad por clúster y corregir el script de evidencia antes de pruebas destructivas.
4. Preparar stage/prod, sus kubeconfigs y secretos propios; actualmente **no existen instancias de esos ambientes**, no están simplemente detenidas.
5. Validar el pipeline del PR, Sonar y los controles de seguridad completos; revisar y fusionar a main.
6. Crear la siguiente release mediante Release Please, verificar tag/notas/versiones y ejecutar su job oficial.
7. Desplegar los nueve releases en dev, validar DNS/TLS, Smoke y E2E; promover a stage, ejecutar ZAP y obtener aprobación para prod.
8. Guardar evidencia real de alertas y rollback en los tres ambientes. No se ha completado esa evidencia.

## 3. Arquitectura AWS y servicios

Proyecto de la nueva experiencia AWS: `001784022331`. Región seleccionada: Ohio, `us-east-2`. El control del Agent Toolkit usa `us-east-1`, lo cual no autoriza crear allí recursos de aplicación.

Flujo de entrega:

```text
GitHub -> Jenkins -> pruebas / Sonar / Trivy -> Nexus HTTPS
                                               |
                              dev -> stage -> aprobación -> prod
                              k3s     k3s                  k3s
```

Cada ambiente usa una VPC propia. El diseño no es un único clúster con tres namespaces. Cada clúster tendrá middleware y observabilidad propios; los scripts que hablan de un clúster compartido requieren revisión.

Servicios AWS utilizados: IAM para roles de servicios, EC2, EBS gp3 cifrado, VPC/subred pública/rutas/Internet Gateway/security groups, Elastic IP, S3 y DynamoDB para Terraform, CloudWatch Logs, Systems Manager y AWS Budgets. K3s, Nexus y las bases de datos corren en EC2. No se necesita crear EKS, ECS, ECR, RDS, MSK, ElastiCache, NAT Gateway, ALB ni una zona Route 53 para esta demostración.

| Entorno | Recurso | Identificador / acceso |
| --- | --- | --- |
| CI | EC2 m7i-flex.large, 2 CPU/8 GiB | `i-0aaea7211026e03d7`, `18.220.203.182` |
| Dev servidor | EC2 m7i-flex.large | `i-0713e7f73c304ae7a`, EIP `3.143.61.218` |
| Dev worker | EC2 m7i-flex.large | `i-02ed4119a539dbc42`; IP pública automática, no estable |
| Stage | Previsto, sin crear | VPC `10.60.0.0/16`, dos nodos por defecto |
| Prod | Previsto, sin crear | VPC `10.70.0.0/16`, dos nodos por defecto |
| Estado | S3 | `circleguard-tfstate-001784022331-us-east-2` |
| Locks | DynamoDB | `circleguard-tf-locks`, clave `LockID` String |

CI usa VPC `10.40.0.0/16`; dev, `10.50.0.0/16`. El worker comparte red y rol del ambiente; usa salida pública sin NAT Gateway. SSH y API externos están restringidos. TCP 6443/10250 y UDP 8472 entre nodos están limitados al mismo security group.

En dev, el servidor conserva el nombre Kubernetes `ip-10-50-1-25` aunque su IP privada cambie. Es intencional: mantiene la afinidad de los PVC del volumen EBS. El worker se llama `circleguard-dev-worker-1`.

### Consolas y direcciones

- Jenkins: https://jenkins.18-220-203-182.sslip.io/
- Nexus: https://nexus.18-220-203-182.sslip.io/
- SonarQube: https://sonar.18-220-203-182.sslip.io/
- Registry Docker: `registry.18-220-203-182.sslip.io`, sin esquema ni `:5000` al usar TLS.
- API Kubernetes dev: `https://3.143.61.218:6443`.
- Host previsto para la aplicación dev: `dev.3-143-61-218.sslip.io`. No darlo por operativo hasta validar sus Ingress.

Caddy publica las consolas de CI y el registry con certificados públicos. Los puertos internos siguen en loopback/Compose. Dev usa Traefik de k3s y cert-manager 1.21.3 con HTTP-01/Let's Encrypt; no usa la CA de Caddy para los Ingress.

### Microservicios y middleware

| Release Helm | Puerto | Dependencias principales |
| --- | --- | --- |
| auth-service | 8180 | PostgreSQL auth, LDAP, identity |
| identity-service | 8083 | PostgreSQL identity, cifrado de identidad, Kafka |
| promotion-service | 8088 | PostgreSQL promotion, Neo4j, Redis, Kafka |
| form-service | 8086 | PostgreSQL form, Kafka |
| gateway-service | 8087 | Redis, validación de acceso/QR |
| notification-service | 8082 | Kafka, auth/identity, correo/SMS/push según configuración |
| file-service | 8085 | Almacenamiento de archivos y JWT |
| dashboard-service | 8084 | PostgreSQL dashboard, promotion |
| web | 8080 | SPA Expo, llamadas `/api/v1/...` al mismo origen |

Bases: `circleguard_auth`, `circleguard_identity`, `circleguard_promotion`, `circleguard_dashboard`, `circleguard_form`. Servicios internos: `postgres`, `redis-master`, `kafka`, `neo4j`, `openldap`. El Secret es `circleguard-secrets`; el imagePullSecret será `nexus-registry`.

## 4. Archivos privados y conexión desde Windows

Carpeta de trabajo actual: `C:\Users\damar\Desktop\CircleGuardSoft5Comentarios`.

| Archivo local, excluido de Git | Función |
| --- | --- |
| `.local-evidence/credentials/ci.env` | Usuarios y claves CI, token GitHub, webhook Discord |
| `.local-evidence/credentials/dev.kubeconfig` | Acceso administrativo al clúster dev |
| `.local-evidence/credentials/circleguard-env-dev` | Secretos de aplicación propios de dev |
| `.local-evidence/credentials/ssh/circleguard-demo-20261123` | Llave SSH privada |
| `.local-evidence/credentials/ssh/known_hosts` | Huellas SSH aceptadas |
| `.local-evidence/credentials/jira.credential.xml` | Credencial Atlassian cifrada con DPAPI |

No se adjuntan estos archivos al PDF ni al repositorio. En otra máquina hay que transferirlos por un mecanismo seguro o generar accesos nuevos. El XML de Jira depende del usuario y equipo Windows que lo creó.

Preparar variables PowerShell, sin mostrar secretos:

```powershell
$Repo = 'C:\Users\damar\Desktop\CircleGuardSoft5Comentarios'
Set-Location -LiteralPath $Repo
$Aws = Join-Path $env:LOCALAPPDATA 'Programs\Amazon\AWSCLIV2\aws.exe'
$Tf = Join-Path $Repo '.tools\terraform-1.9.8\terraform.exe'
$Ssh = 'C:\Windows\System32\OpenSSH\ssh.exe'
$Key = Join-Path $Repo '.local-evidence\credentials\ssh\circleguard-demo-20261123'
$Known = Join-Path $Repo '.local-evidence\credentials\ssh\known_hosts'
$SshOptions = @('-i', $Key, '-o', 'BatchMode=yes',
  '-o', 'ConnectTimeout=8', '-o', "UserKnownHostsFile=$Known")
$Ci = 'ubuntu@18.220.203.182'
$Dev = 'ubuntu@3.143.61.218'
```

Comprobaciones seguras:

```powershell
git status --short
git branch --show-current
& $Aws sts get-caller-identity --profile circleguard --region us-east-2
& $Ssh @SshOptions $Ci 'sudo docker ps'
& $Ssh @SshOptions $Dev 'sudo k3s kubectl get nodes -o wide'
& $Ssh @SshOptions $Dev 'sudo k3s kubectl get pods -A'
```

Si SSH falla tras un cambio de máquina, verificar primero la instancia/EIP y su huella; no desactivar globalmente la verificación SSH. Si cambió la IP del operador, ajustar su `/32` en Terraform mediante un plan revisado. Nunca abrir 22/6443 a `0.0.0.0/0`.

## 5. Conectar Claude Code o Codex con AWS

La conexión ya se preparó en esta estación. No implica pegar credenciales en el chat ni que el login de la consola AWS se transfiera automáticamente al agente.

Fuente oficial: https://raw.githubusercontent.com/aws/agent-toolkit-for-aws/refs/heads/main/setup-instructions/setup.md

### 5.1 Verificar el acceso local existente

```powershell
& $Aws --version
& $Aws configure get region --profile circleguard
& $Aws sts get-caller-identity --profile circleguard --region us-east-2
```

La región debe ser `us-east-2` y `Account` debe ser `001784022331`. Si devuelve otro proyecto, detenerse. Confirmar la región también en AWS Settings, proyecto, Overview, Additional Info.

Si la sesión expiró, renovar por navegador con la identidad social del propietario o del miembro del equipo autorizado:

```powershell
& $Aws login --region us-east-2 --profile circleguard
```

Seleccionar el proyecto correcto y repetir STS. La guía oficial indica credenciales de 12 horas, renovables durante 90 días sin repetir la autenticación del navegador. No crear access keys de un IAM user ni usar contraseñas de Google como tokens.

### 5.2 Instalar o comprobar el Agent Toolkit

Comprobar `uv --version` y `uvx --version`; en esta estación están en `C:\Users\damar\.local\bin`. Si CLI/uv faltan, seguir el instalador oficial citado y verificar su procedencia.

Si ya existe `aws-mcp`, inspeccionar su perfil antes de reinstalar. Para un agente aún sin configurar, el asistente oficial es:

```powershell
& $Aws configure agent-toolkit --yes --region us-east-1 --profile circleguard
& $Aws agent-toolkit list-available-skills `
  --region us-east-1 --profile circleguard
```

Elegir Claude Code y/o Codex cuando el asistente lo ofrezca. El `us-east-1` de estos dos comandos pertenece al control del kit, no al despliegue de CircleGuard.

El kit escribe la entrada MCP `aws-mcp`. Conservar sus `command`, `args`, timeout y transporte. Agregar o combinar únicamente la selección del perfil:

```json
"env": {
  "AWS_MCP_PROXY_PROFILES": "circleguard"
}
```

En Claude, la entrada está bajo `mcpServers` del archivo de configuración que indique el instalador. En Codex, si la configuración es TOML, la forma equivalente es:

```toml
[mcp_servers.aws-mcp.env]
AWS_MCP_PROXY_PROFILES = "circleguard"
```

No duplicar tablas/entradas existentes ni borrar otros servidores. Para MCP se usa `AWS_MCP_PROXY_PROFILES`, no solamente `AWS_PROFILE`. El perfil de aplicación sigue seleccionando Ohio.

Cerrar y abrir una sesión del agente para recargar MCP. Claude debe cargar `CLAUDE.md`; Codex, `AGENTS.md`. Ambos archivos ya conservan las reglas de nueva experiencia y el nivel de ayuda HIGH.

### 5.3 Verificar que el agente realmente accede

Pedir al agente que enumere sus herramientas AWS, busque la habilidad pertinente con `aws___search_documentation` en `agent_skills`, la cargue con `aws___retrieve_skill` y consulte STS. Confirmar otra vez `001784022331`, sin imprimir tokens. Hacer después una lectura de las instancias de Ohio, no un despliegue de prueba.

Si AWS MCP no aparece, revisar la configuración generada, que `uvx` sea resoluble y que el perfil tenga sesión. La ausencia del conector en una sesión no borra los recursos AWS. Se puede continuar con la CLI autenticada hasta corregir o reiniciar el agente; esta auditoría usó esa alternativa.

### 5.4 Diferencia entre autenticación local y Jenkins

Terraform 1.9.8/proveedor AWS 5.100.0 no consume directamente el nuevo `login_session`. El perfil local `circleguard-terraform` usa `credential_process` para exportar temporalmente las credenciales de `circleguard`. No contiene access keys permanentes.

```powershell
$env:AWS_PROFILE = 'circleguard-terraform'
$env:AWS_REGION = 'us-east-2'
$env:AWS_DEFAULT_REGION = 'us-east-2'
$env:AWS_EC2_METADATA_DISABLED = 'true'
```

Ese adaptador local no debe copiarse a Jenkins. Jenkins usa IMDSv2 y `CircleGuardCiTerraformRole`, con `CIRCLE_AWS_AUTH_MODE=instance-profile`. El contenedor necesita hop limit 2; los nodos de aplicación usan 1.

## 6. Retomar el punto de corte: imágenes y Nexus

Revisar primero el inventario final de esta entrega y el job:

- `docs/evidence/handover-status.json`.
- https://jenkins.18-220-203-182.sslip.io/job/circleguard-handover/
- Nexus, Browse, repositorio Docker `circleguard`.

No confundir las nueve imágenes locales antiguas `v1.1.1` con los snapshots publicados por el nuevo job. Los snapshots tienen un commit de origen completo en `org.opencontainers.image.revision` y tag `handover-<sha12>`.

Código del job: `infra/ci/candidate.Jenkinsfile`. Publicador: `infra/ci/publish-candidate.sh`. Escaneo: `scripts/scan-image.sh`. Esta ruta no crea infraestructura ni despliega ambientes y no reemplaza el pipeline oficial de release.

Para iniciar un nuevo snapshot desde un commit ya subido:

```powershell
$Commit = (git rev-parse HEAD).Trim()
& .\infra\ci\jenkins\start-candidate.ps1 `
  -JenkinsUrl 'https://jenkins.18-220-203-182.sslip.io' `
  -CredentialEnvFile '.local-evidence/credentials/ci.env' `
  -GitCommit $Commit
```

El job exige checkout limpio, ejecuta unitarias/JaCoCo e integraciones Docker, construye ocho imágenes Java y web, escanea todas y comprueba el arranque web y la ruta SPA antes de cualquier push. Si falla una imagen, no publica el lote. Preserva los informes originales, SBOM, VEX y hallazgos suprimidos visibles en sus artifacts.

No aumentar el umbral de Trivy, no usar exit-code 0 para aprobar el resultado final y no ampliar VEX a otros paquetes sin análisis. La evaluación actual solo cubre dos avisos de Spring MVC 6.2.19 y exige revisión antes del 27 de noviembre. No extender esa fecha sin reevaluar.

## 7. Webhook: tarea del responsable que recibe este PDF

Se debe usar el webhook existente que indicó el equipo. El estado confirmado al redactar la guía es **pendiente**: no está guardado en el archivo de credenciales indicado. No se inventó ni se envió ninguna notificación de prueba a un canal desconocido.

1. En Discord, verificar el servidor y el canal correctos de Spidey. En Ajustes del servidor o canal, Integraciones, Webhooks, seleccionar el existente.
2. Confirmar que su propietario permite utilizarlo para CircleGuard. Copiar su URL de forma privada.
3. Editar `.local-evidence/credentials/ci.env` y agregar una sola línea `CI_DISCORD_WEBHOOK=...` con el valor real. No agregar `;` ni publicar el archivo.
4. Conservar las demás variables del archivo. El token GitHub ya existe; no sustituirlo por el webhook.
5. Transferir el archivo por SCP al CI como `/opt/circleguard/ci.env`, permisos 600. No enviarlo por Git, PDF o un comentario de PR.
6. Esperar a que no haya builds activos. Ejecutar el bootstrap CI idempotente desde una copia actualizada del código, con `CI_ENABLE_HTTPS=1 CI_DOCKER_USE_SUDO=1`.
7. `infra/ci/init/configure.sh` debe registrar `discord-webhook` en Jenkins como usuario/contraseña; la contraseña es la URL. Verificar el ID, sin revelar su valor.
8. Cargar también esa URL en Alertmanager mediante un Secret por clúster. El webhook de Jenkins por sí solo no configura Kubernetes.
9. Probar un fallo controlado del pipeline y una alerta FIRING y RESOLVED de Prometheus. Registrar ambiente, hora, build y mensaje recibido; ocultar la URL en capturas.

Ejemplo de ejecución en el host CI, solo después de transferir credenciales y actualizar sus archivos de bootstrap:

```bash
cd /opt/circleguard
chmod 600 ci.env
CI_ENABLE_HTTPS=1 CI_DOCKER_USE_SUDO=1 \
  bash infra/bootstrap/bootstrap-ci.sh --local ci.env
```

No ejecutar `bootstrap-ci.sh --aws`: esa ruta antigua usa CloudFormation y no corresponde al CI aprovisionado por Terraform de este proyecto.

## 8. Jenkins: credenciales y prerrequisitos del pipeline oficial

| Credencial o ajuste | Estado / verificación |
| --- | --- |
| `circleguard-github` | Token validado con permiso push al repositorio público |
| `nexus-registry` | Usuario/contraseña configurados; licencia aceptada |
| `sonarqube-token` | Token de análisis y servidor `sonarqube` configurados |
| `sonarqube-webhook-secret` | Callback interno del quality gate configurado |
| `kubeconfig-dev` | Cargado; repetir preflight tras actualizar CI |
| `circleguard-env-dev` | Cargado; no regenerar cuando ya haya datos cifrados |
| `circleguard-e2e-health` | Cuenta sintética, comprobar contra el seed de auth |
| `kubeconfig-stage/prod` | Pendientes hasta crear los ambientes |
| `circleguard-env-stage/prod` | Pendientes; generar secretos distintos |
| `discord-webhook` | Pendiente del responsable |
| `grafana-admin-password` | Pendiente de generar/cargar de forma privada |
| AWS | Rol de instancia; no crear `aws-terraform` con claves estáticas para este modo |

Usuarios de Jenkins: operador administrativo, desarrollador y aprobador; consultar sus nombres y contraseñas en el archivo privado. Prod exige el usuario aprobador configurado, normalmente `aprobador`.

El CI activo puede seguir con `CI_BRANCH_INCLUDES=__bootstrap_disabled__`. Antes de la release, actualizar/recrear el servicio Jenkins con el código actual, verificar sus credenciales y habilitar filtros para main, PR y el **nuevo** tag. No disparar el viejo `v1.1.0` sobre este Nexus vacío.

El JCasC versionado agrega sondeo de dos minutos, útil porque el token verificado no tiene administración del repositorio para crear webhooks. Alternativamente, un administrador puede configurar el webhook de GitHub hacia `https://jenkins.18-220-203-182.sslip.io/github-webhook/`. No se ha certificado todavía ese disparo completo.

Para cargar los dos Secret files de un ambiente sin imprimir su contenido:

```powershell
& .\infra\ci\jenkins\import-secret-files.ps1 `
  -JenkinsUrl 'https://jenkins.18-220-203-182.sslip.io' `
  -CredentialEnvFile '.local-evidence/credentials/ci.env' `
  -SecretDirectory (Join-Path $Repo '.local-evidence/credentials') `
  -Environment dev
```

El importador termina con `return null`: no quitarlo. El endpoint scriptText de Jenkins imprime el valor de retorno de Groovy; devolver una lista de credenciales puede revelar su contenido codificado.

## 9. Terraform: verificar y preparar stage/prod

Raíces: `infra/terraform/bootstrap`, `infra/terraform/envs/ci`, `dev`, `stage`, `prod`. Cada ambiente tiene su propia clave de estado; CI usa `circleguard/ci/terraform.tfstate`.

Comprobaciones de CI y dev, después de preparar las variables del apartado 5.4:

```powershell
& $Tf '-chdir=infra/terraform/envs/dev' init `
  -input=false '-backend-config=backend.hcl'
& $Tf '-chdir=infra/terraform/envs/dev' validate
& $Tf '-chdir=infra/terraform/envs/dev' plan -input=false -detailed-exitcode
```

Exit 0 significa sin cambios; 2 significa cambios, no éxito de despliegue; 1 es error. No aplicar un plan que reemplace máquinas por sorpresa. Para revisar automáticamente un plan guardado:

```powershell
& $Tf '-chdir=infra/terraform/envs/stage' init `
  -input=false '-backend-config=backend.hcl'
& $Tf '-chdir=infra/terraform/envs/stage' plan -input=false '-out=tfplan'
if ($LASTEXITCODE -ne 0) { throw 'Plan stage fallido' }
& $Tf '-chdir=infra/terraform/envs/stage' show -json tfplan |
  node infra/terraform/scripts/check-plan.mjs
if ($LASTEXITCODE -ne 0) { throw 'Revisar cambios destructivos' }
```

Antes del apply, revisar región, proyecto, AMI, llave, CIDR, registro y capacidad. Se han usado 3 VPC incluyendo la predeterminada; CI/dev/stage/prod más la predeterminada consumirán la cuota anterior de 5. Revisar cuotas antes de crear más redes. Cada ambiente agregará dos EC2; no usa t3.xlarge, rechazado por el plan FREE.

Aplicar el plan revisado de stage; esperar cloud-init, k3s y ambos nodos Ready. Obtener su EIP con `terraform output -raw public_ip`; no reutilizar la IP de dev. Repetir el proceso para prod solo cuando se vayan a realizar pruebas.

Exportar el kubeconfig con `infra/terraform/scripts/export-kubeconfig.sh`, revisando primero su uso, o por SSH redirigido a un archivo privado y sustituyendo únicamente `127.0.0.1` por su EIP. No mostrar las claves en consola. Generar el `.env` con `deploy/new-env.sh` en Linux/Git Bash y redirigirlo a un archivo privado; no invocarlo sin redirección. Agregar el registro HTTPS real, SITE_ADDRESS, TLS_ISSUER y ACME_EMAIL. Subir los Secret files a Jenkins con el importador.

Valores por ambiente:

```dotenv
REGISTRY=registry.18-220-203-182.sslip.io
SITE_ADDRESS=HOST_PUBLICO_DEL_AMBIENTE
TLS_ISSUER=circleguard-letsencrypt
ACME_EMAIL=CORREO_DEL_OPERADOR
```

Cada `.env` requiere DB_USERNAME, DB_PASSWORD, JWT_SECRET, QR_SECRET, LDAP_PASSWORD, VAULT_SECRET, VAULT_SALT, VAULT_HASH_SALT, NEO4J_PASSWORD, REDIS_PASSWORD e INTERNAL_API_KEY. No compartir claves entre ambientes ni cambiar las de la bóveda después de almacenar identidades.

## 10. Control de costos y encendido selectivo

La última consulta de esta auditoría devolvió plan FREE activo y USD 160 en créditos. El presupuesto es USD 25 con alertas de consumo; no es un corte automático. El proyecto debe permanecer en la modalidad acordada.

Con CI más dev de dos nodos, el orden de magnitud es USD 7,8/día, antes de logs/transferencia/solicitudes. La tarifa observada fue USD 0,09576/h por m7i-flex.large. No mantener los tres ambientes encendidos continuamente.

Para consultar únicamente stage/prod existentes:

```powershell
& $Aws ec2 describe-instances --profile circleguard --region us-east-2 `
  --filters Name=tag:Project,Values=CircleGuard `
    Name=tag:Environment,Values=stage,prod `
    Name=instance-state-name,Values=running,stopped,stopping,pending `
  --query 'Reservations[].Instances[].{Id:InstanceId,State:State.Name,Tags:Tags}'
```

Antes de detenerlos, verificar el resultado y seleccionar exclusivamente sus IDs. Detener ambos nodos de cada ambiente y esperar estado stopped. Encenderlos antes del pipeline y esperar Ready: un Terraform apply sin cambios no enciende automáticamente una instancia detenida. No seleccionar CI ni dev por accidente.

```powershell
# Sustituir por los IDs verificados del ambiente; no ejecutar con placeholders.
& $Aws ec2 stop-instances --profile circleguard --region us-east-2 `
  --instance-ids ID_SERVIDOR_STAGE ID_WORKER_STAGE
& $Aws ec2 wait instance-stopped --profile circleguard --region us-east-2 `
  --instance-ids ID_SERVIDOR_STAGE ID_WORKER_STAGE
```

Detener EC2 no elimina cargos de EBS/EIP. No sustituir stop por terminate para ahorrar: podría perderse el disco raíz o la identidad de un worker. Tras la sustentación se realizará el destroy revisado del apartado 16.

## 11. Integración a main y nueva release

La entrega se hace en la rama de estabilización, conservando el trabajo local. El PR de integración debe permanecer pendiente si faltan controles. No se certifica main desplegado por haber hecho push.

Orden:

1. Revisar el diff completo del PR hacia main, incluido el commit base 69cba57.
2. Verificar unitarias, integraciones Docker, JaCoCo, Jest, TypeScript, Helm, controles de release y seguridad. El job de snapshots no sustituye Sonar, ZAP ni E2E del pipeline oficial.
3. Resolver los pendientes de observabilidad y credenciales de los tres ambientes antes del tag.
4. Pasar los checks requeridos de GitHub/Jenkins y obtener las revisiones exigidas por la rama. No saltarlas con force push o cambios de protección.
5. Fusionar el PR de estabilización a main.
6. Release Please abrirá el PR de release. Revisar changelog, `version.txt`, versión móvil y manifiesto; se espera una patch posterior a 1.1.0, previsiblemente 1.1.1.
7. Fusionar el PR de release y comprobar tag `vX.Y.Z`, release pública con notas y que el commit contiene la estabilización.
8. Ejecutar el job del nuevo tag con BUILD_ALL=true, DEPLOY_TARGET=helm, DEPLOY_MIDDLEWARE=true para el arranque y SKIP_DEPLOY=false.

Comprobaciones Git tras obtener el tag real:

```powershell
git fetch origin --prune --tags
git show --no-patch v1.1.0
git show --no-patch v1.1.1
git merge-base --is-ancestor 69cba57 v1.1.1
git show v1.1.1:version.txt
```

Si Release Please elige otra versión, usarla en lugar de v1.1.1. No etiquetar la rama fix manualmente para saltarse el flujo. `release-1.1.0` no coincide con el patrón oficial del Jenkinsfile.

## 12. Helm, DNS y TLS de dev

La ruta recomendada es el job oficial del tag, después de main. Si un operador necesita ejecutar los scripts manualmente durante diagnóstico, debe utilizar las imágenes oficiales ya publicadas y documentar que no constituye una ejecución completa del pipeline.

Con el kubeconfig del ambiente y sus variables privadas cargadas en un shell de administración:

```bash
export KUBECONFIG=/ruta/privada/dev.kubeconfig
export REGISTRY=registry.18-220-203-182.sslip.io
export SITE_ADDRESS=dev.3-143-61-218.sslip.io
export TLS_ISSUER=circleguard-letsencrypt
export TLS_ISSUER_MODE=acme
export ACME_EMAIL=correo-del-operador
bash deploy/k8s/crear-secretos.sh dev
bash deploy/k8s/cert-manager.sh
bash deploy/k8s/middleware.sh dev
bash deploy/k8s/verificar-postgres.sh dev
# NX_USER/NX_PASS deben llegar de una credencial, no de un literal del comando.
bash deploy/k8s/desplegar.sh dev v1.1.1
```

`desplegar.sh` toma SITE_ADDRESS para host, CORS y TLS; conserva rutas por servicio. El `.env` de Jenkins debe aportar esas variables, el resto de secretos y la misma clave JWT entre los servicios del ambiente.

Verificar los nueve releases, rollouts, certificado y HTTP:

```bash
helm list -n circleguard-dev
kubectl -n circleguard-dev get deployments,pods,ingress,certificate
kubectl -n circleguard-dev wait --for=condition=Ready \
  certificate/circleguard-dev-tls --timeout=300s
bash deploy/k8s/verificar-tls.sh dev.3-143-61-218.sslip.io
curl -I https://dev.3-143-61-218.sslip.io/
```

El nombre del Certificate suele coincidir con el Secret solicitado por Ingress; comprobarlo con get si difiere. No usar `-k` para aprobar TLS público. HTTP 404 o un certificado autofirmado no son éxito. Los 401/403 de rutas protegidas pueden ser válidos para humo, pero no demuestran el flujo funcional.

## 13. Smoke, E2E, stage y prod

El pipeline oficial debe completar humo dev y Playwright antes de promover. `circleGuardE2E.groovy` abre port-forward a `redis-master`, utiliza la credencial sintética de salud y los endpoints del SITE_ADDRESS dev.

Comprobar que no se ejecuta contra localhost ni una VM anterior. La web usa rutas relativas al mismo origen para permitir la misma imagen en los tres ambientes. Capturar el reporte Playwright y las versiones de imagen efectivas, no solo un log de helm upgrade.

Promover exactamente los mismos tags/digests a stage; ejecutar humo y ZAP. Prod requiere la aprobación del usuario restringido en Jenkins y conserva un artifact con el aprobador. El operador debe revisar y aprobar esa acción en el job; no simular su identidad desde otro agente.

Inventario de release, con los tres kubeconfigs disponibles de forma privada:

```bash
node deploy/k8s/releases.mjs snapshot dev .local-evidence/hu-27/dev/deploy
node deploy/k8s/releases.mjs snapshot all .local-evidence/hu-27/overview
```

Consultar el uso de `releases.mjs` para las variables KUBECONFIG_DEV/STAGE/PROD antes de snapshot all. Guardar versiones, namespace, readiness y URLs de los builds junto a los reportes.

## 14. Observabilidad: trabajo pendiente concreto

Instalar por cada clúster el stack Prometheus/Grafana/Alertmanager y después Jaeger/Loki. Los archivos relevantes son `observability/instalar.sh`, `observability/kube-prometheus-stack/values.yaml`, `observability/servicemonitors.yaml`, `observability/alerts/` y `observability/trazas-logs/`.

Pendientes detectados antes de ejecutar:

- El Jenkinsfile de esta rama todavía no carga una instalación de observabilidad por ambiente. `origin/dev` contiene wrappers y un cierre del PR 52, pero asumen un clúster compartido: integrar selectivamente, sin pisar las correcciones de la rama fix.
- El chart fijado trae Alertmanager 0.27.0; la configuración actual usa `webhook_url_file`. El cierre de dev utiliza configuración completa en Secret porque esa versión no admite esa forma. Corregir y validar el Secret antes de afirmar que Discord funciona.
- Preparar `grafana-admin-password` y el webhook; no escribirlos en values versionados.
- Instalar en cada contexto, no únicamente en kubeconfig-dev. Cada ambiente debe mostrar sus ocho servicios; el número de targets puede aumentar por réplicas/HPA.
- Para operar las evidencias se necesitan kubectl, curl y jq en el agente utilizado.

Después de corregir esos puntos y cargar credenciales privadas:

```bash
# Variables CI_DISCORD_WEBHOOK y GRAFANA_ADMIN_PASSWORD ya deben estar cargadas.
bash observability/instalar.sh
bash observability/trazas-logs/instalar.sh
kubectl -n monitoring get pods,servicemonitors,prometheusrules
```

Confirmar targets UP, dashboards técnicos/negocio, trazas y logs del flujo encuestas. Revisar el nombre real de los Services antes de port-forward; no asumir un nombre de un chart anterior.

## 15. Evidencia de alertas y rollback

**No ejecutar a ciegas el script actual de alertas.** En la auditoría se detectó que su cleanup restaura web y PostgreSQL, pero no auth-service si ServiceDown falla o termina antes de la restauración normal; además restaura cantidades fijas. Debe corregirse para capturar y restaurar las réplicas originales aun cuando falle el test. Su cabecera desaconseja usarlo en prod.

El responsable debe resolver ese punto primero, probar la restauración y planificar pruebas controladas por ambiente. No tumbar bases de datos o autenticación de prod como una prueba improvisada.

En dev/stage, una vez reparado y revisado:

```bash
ENV_NAME=dev bash observability/evidencias/disparar-alertas.sh service-down
ENV_NAME=dev bash observability/evidencias/disparar-alertas.sh high-errors
ENV_NAME=dev bash observability/evidencias/disparar-alertas.sh gate-denials
```

Registrar estado anterior, FIRING, recepción real en Discord, restauración, rollouts Ready y RESOLVED. Las rutas protegidas cambiaron durante la estabilización: verificar que el generador de carga produce la métrica que se pretende probar, no solo respuestas 401.

Para rollback se necesita una versión previa segura **ya publicada** y revisiones Helm exitosas. El Nexus nuevo no contiene un histórico válido por defecto. No usar el viejo v1.1.0 vulnerable como base ni publicar imágenes nuevas bajo ese nombre. Preparar dos releases posteriores seguras si hace falta demostrar cambio de versión.

Desde Jenkins, usar ROLLBACK_TAG y ROLLBACK_ENV; prod exige aprobación. El helper utiliza historiales de los nueve releases y falla ante uno incompleto. No eliminar esa comprobación ni sustituir rollback por reconstrucción de imágenes.

La evidencia de HU-27 debe incluir antes/después, tags y digests, readiness, humo, duración, resultado y build. No declarar recuperación si solo terminó helm rollback pero el servicio sigue fallando. Detener stage/prod al terminar las pruebas.

## 16. Cierre después de la sustentación

El usuario quiere conservar el entorno hasta la sustentación del miércoles 25 y retirarlo después. No hay una automatización de apagado/destroy; ni el presupuesto ni DemoEnd lo ejecutan. Confirmar que terminó la sustentación antes de retirar recursos.

1. Guardar evidencia sin secretos y respaldos privados necesarios.
2. Revisar el plan destroy de prod, stage y dev y aplicarlo de forma deliberada con el perfil del operador. El control del pipeline bloquea estos planes; la limpieza es una operación administrativa separada.
3. Para CI, `prevent_destroy=true` evita otra eliminación accidental. Preparar su retiro explícito en código, revisar el plan y solo entonces destruir CI. Sus datos de Jenkins/Nexus/Sonar están en el disco raíz: hacer respaldo privado si se desean conservar.
4. Destruir el backend al final. El bucket versionado contiene estados sensibles y no quedará vacío eliminando solo los objetos actuales. No borrar el backend mientras haya ambientes activos o locks necesarios.
5. Consultar instancias, EBS, EIP, logs y recursos restantes. EBS y EIP pueden seguir costando aun con EC2 detenido.
6. No cerrar el proyecto AWS para sustituir una limpieza de recursos. La experiencia anterior de cierre de proyecto no debe repetirse por accidente.

## 17. Jira y contexto de requisitos

Tablero KAN: https://seraph.atlassian.net/jira/software/projects/KAN/boards/1

La sesión anterior conservó 56 issues y 28 historias accesibles con sus criterios en `.local-evidence/jira/kan-issues.json`. HU-22/KAN-34 devolvió 404; no se debe afirmar que esa historia fue verificada. Las restricciones de acceso del tablero no se resuelven reutilizando el conector de otro tenant.

La conexión API local está en `.local-evidence/jira/connection.json`; el token está cifrado en el XML de credenciales. Una cuenta Atlassian que entra con Google necesita un token API de Atlassian para la API, no su contraseña Google. Revalidar Jira solo si cambian los criterios o quedan dudas de cierre; no editar/cerrar HUs sin evidencia.

## 18. Validaciones, evidencia y archivos de referencia

Separar tres niveles de evidencia:

| Nivel | Lo que demuestra |
| --- | --- |
| Base de estabilización anterior | Los checks enumerados en sección 7 pasaron antes de los últimos parches |
| Validación de esta entrega | Consultar inventario final y job snapshot; unitarias/JaCoCo y 16 pruebas Node verificadas localmente |
| Cierre pendiente | Pipeline oficial, Sonar, DNS/TLS de aplicación, E2E/ZAP, alertas/rollback y main desplegado |

Los planes sin cambios de CI/dev se guardaron localmente en `.local-evidence/ci-no-changes.txt` y `dev-no-changes.txt`. Los informes completos de seguridad, kubeconfigs, planes JSON y credenciales permanecen privados. El inventario público solo debe contener nombres, digests, resultados y enlaces de ejecución.

Archivos para revisar:

- `docs/pendientes-integracion.md`, sección 7: base funcional de estabilización.
- `docs/despliegue-aws-estabilizacion.md`: bitácora resumida de infraestructura.
- `docs/seguridad-spring-demo.md` y `security/spring-rendering-assessment.json`.
- `infra/terraform/envs/ci/`, `modules/cluster/`, `modules/environment/`, `modules/network/`.
- `infra/terraform/scripts/check-plan.mjs` y pruebas; `policies/ci-terraform.json`.
- `infra/ci/compose.aws.yml`, `Caddyfile`, JCasC y scripts de importación/snapshot.
- `Jenkinsfile`, `jenkins/vars/`, `deploy/k8s/`, `deploy/helm/` y `observability/`.

## 19. Prompt inicial listo para Claude

```text
Lee CLAUDE.md, AGENTS.md y docs/handover-claude-release.md completos.
Trabaja en fix/release-1.1.0-estabilizacion sin descartar cambios locales.
Confirma el commit y docs/evidence/handover-status.json antes de asumir resultados.
Usa el perfil AWS circleguard, proyecto 001784022331, region us-east-2.
Verifica STS y el estado real de CI, Nexus y los dos nodos dev.
Distingue snapshots handover-* de la release oficial pendiente.
Coordina conmigo el webhook existente y cargalo localmente, nunca en Git/chat.
Corrige los pendientes concretos de observabilidad/alertas de la guia.
Continua el PR, Release Please y el pipeline oficial hasta cerrar las evidencias.
Mantiene stage/prod apagados salvo durante pruebas y controla el presupuesto.
Explica cada paso y verifica su resultado antes de avanzar. No declares terminado
main hasta comprobar despliegue, TLS, E2E, alertas y rollback en los tres ambientes.
```

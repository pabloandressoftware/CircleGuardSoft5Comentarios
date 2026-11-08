# Sprint 1 · HU-03, HU-05 y HU-15

## HU-03 · Versiones e imágenes

Release Please lee los Conventional Commits de `main`. Usen títulos de PR como
`feat: agregar reporte de salud`, `fix: corregir la validación QR` y
`feat!: cambiar el contrato de acceso` si fusionan con squash. Tras la primera
versión, estos cambios incrementan MINOR, PATCH y MAJOR, respectivamente.

El workflow abre o actualiza un PR de release con el changelog y la versión.
Al aprobar y fusionar ese PR, el siguiente run publica el tag `vX.Y.Z` y el
GitHub Release. Configuren GitHub para que el workflow pueda crear PR. Si
requieren que otros workflows de GitHub se disparen desde el PR o tag, usen el
secreto de repositorio `RELEASE_PLEASE_TOKEN` con una credencial autorizada para
el repositorio. El token por defecto sirve para el propio workflow.

Jenkins debe descubrir los tags de release y ejecutar el empaquetado y despliegue
oficial desde el **commit del tag**, después de sus controles de calidad. Tras el
checkout y fetch de tags, obtengan la etiqueta con:

```bash
git fetch origin --tags
IMAGE_TAG=$(node scripts/release-image-tag.mjs)
```

El comando falla si `HEAD` y `version.txt` no coinciden con un tag SemVer.
Usen `IMAGE_TAG` al publicar y promuevan las mismas referencias o digests en
dev, stage y prod. Los builds de PR pueden compilar y verificar sin publicar.
Con esta estrategia, el merge que actualiza la versión oficial es el merge
del PR de release. El equipo de pipeline debe conectar la detección del tag y
el uso de la versión a HU-08, HU-09 y HU-10.

## HU-05 · EC2 por ambiente

Usen AWS CLI v2, Bash y una sesión AWS ya autorizada. Definan `AWS_REGION`, el
nombre de la llave existente en `EC2_KEY_NAME`, el CIDR de operadores
en `SSH_CIDR` y el CIDR autorizado para HTTPS en `WEB_CIDR`. Si el agente
Jenkins sale por otra IP pública, definan `JENKINS_CIDR` para darle acceso a
SSH y HTTPS. El script usa `m7i-flex.large` y 40 GiB de forma predeterminada.
Comprueben en su región y cuenta la elegibilidad del tipo de instancia y
la AMI. Pueden ajustar `EC2_INSTANCE_TYPE` y `EC2_DISK_GIB` antes de la
primera ejecución.

```bash
export EC2_AMI_ID="$(aws ssm get-parameter --region "$AWS_REGION" \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query 'Parameter.Value' --output text)"

bash infra/bootstrap/bootstrap-env.sh dev
bash infra/bootstrap/bootstrap-env.sh stage
bash infra/bootstrap/bootstrap-env.sh prod
```

Conserven la AMI elegida y los mismos parámetros al repetir la ejecución.
Cada stack tiene su propia VPC, VM y firewall. El firewall admite SSH y HTTPS
desde los CIDR indicados. Los servicios y las bases de datos se comunican
mediante la red de Docker en cada host.

| Ambiente | Stack | Host y URL |
| --- | --- | --- |
| Dev | `circleguard-dev` | Salida `PublicDns` de dev. Registrar la URL HTTPS una vez configurados DNS y TLS. |
| Stage | `circleguard-stage` | Salida `PublicDns` de stage. Registrar la URL HTTPS una vez configurados DNS y TLS. |
| Prod | `circleguard-prod` | Salida `PublicDns` de prod. Registrar la URL HTTPS una vez configurados DNS y TLS. |

Los tres stacks se aprovisionaron el 6 de noviembre de 2026 en la cuenta
CircleGuard terminada en 9264, en Ohio. El README recoge los DNS de SSH.
Para conectarse desde Windows como operador, usen la clave de ambientes
`$env:USERPROFILE\.ssh\circleguard-env\id_ed25519` con el usuario `ubuntu`.
La entrada al host se restringe a la IPv4 del operador y la de CI.
Docker Compose se verificó por SSH en los tres hosts. Con PostgreSQL temporal
aceptando conexiones en la IP privada de prod, una prueba desde dev agotó el
tiempo de conexión tanto a la IP privada como al DNS público de prod por el
puerto 5432. Se retiró la base de prueba al terminar.
Al ejecutar otra vez el bootstrap, CloudFormation indicó que los tres stacks
estaban al día y mantuvo las mismas instancias.

Comprueben en cada host `sudo cloud-init status --wait` y
`sudo docker compose version`. Guarden las salidas de dos ejecuciones con los
mismos parámetros. Tras desplegar el Compose de HU-07, obtengan evidencia de
que PostgreSQL está listo desde prod con `docker compose exec -T postgres pg_isready`.
Desde dev, prueben `timeout 5 bash -c 'echo >/dev/tcp/HOST_PROD/5432'` contra
el DNS público de prod y registren el fallo junto a los `VpcId` distintos y
las reglas de entrada de prod. Sustituyan `HOST_PROD` por el valor real.

En Jenkins creen un **Secret file** por ambiente con los IDs
`circleguard-env-dev`, `circleguard-env-stage` y `circleguard-env-prod`.
Configuren la llave SSH y verifiquen previamente la clave de cada host en el
`known_hosts` del agente. El paso
`circleGuardEnv('dev', 'ubuntu@HOST_DEV', 'ID_LLAVE_SSH')` entrega el archivo a
`/opt/circleguard/.env` antes del despliegue. Repítanlo con el host y el ambiente
correspondientes. El reemplazo es atómico y el archivo queda con permisos 0600.
El instalador se probó en los tres hosts con archivos temporales y conservó su
contenido y los permisos 0600. La VM de CI alcanza el puerto SSH de los tres
ambientes. Para el despliegue queda registrar en Jenkins la llave de despliegue
y los archivos definitivos que use el Compose de HU-07.

## HU-15 · Playwright en dev

Usen un ambiente local o dev, una cuenta sintética exclusiva de la suite y
los servicios ya desplegados. El cliente web debe apuntar a las API del
ambiente. Para el cliente Expo local usen el puerto 8081, permitido por auth.
Configuren el mismo `QR_SECRET` en auth y gateway dentro de ese ambiente para
que la prueba compruebe el estado de salud con un token válido. En auth,
`QR_EXPIRATION` se mide en milisegundos. Usen `QR_EXPIRATION=60000` en el
ambiente de pruebas para una vigencia de 60 segundos.
Para el cliente desplegado, coordinen con HU-07 el proxy del mismo origen
o el origen HTTPS permitido por los servicios.

| Variable | Uso |
| --- | --- |
| `E2E_ENV` | `local` o `dev`. |
| `E2E_WEB_URL` | URL base del cliente. |
| `E2E_AUTH_URL`, `E2E_FORM_URL`, `E2E_GATEWAY_URL` | URLs base de las API desde el agente. Pueden ser el mismo proxy si enruta las tres. |
| `E2E_HEALTH_USERNAME`, `E2E_HEALTH_PASSWORD` | Cuenta de salud de pruebas. En local se puede usar la cuenta sembrada indicada en el PDF. |
| `E2E_REDIS_URL` | Redis del mismo ambiente en loopback. Si es remoto, usen un puerto Redis ligado a loopback en dev y un túnel SSH desde el agente. |
| `E2E_SEED_QUESTIONNAIRE` | Opcional. Con `1`, crea un cuestionario de prueba si dev/local aún no tiene uno activo. Si ya existe, debe tener una pregunta YES_NO. |

```bash
cd e2e
npm ci
npx playwright install --with-deps --only-shell chromium
npm test
```

La suite verifica login válido e inválido, envío de una respuesta real del
cuestionario con confirmación y dos casos de QR auténtico, uno para `ACTIVE`
y otro para `SUSPECT`. Para la puerta se prepara temporalmente el estado de la cuenta
sintética en Redis. El QR se pide a auth y se valida con gateway. La
comprobación de `SUSPECT` requiere la corrección de HU-12.
En la ejecución local del 6 de noviembre de 2026 pasaron cuatro pruebas
(ambos casos de login, el cuestionario y el QR de `ACTIVE`). El gateway
devolvió `GREEN` para `SUSPECT`, cuyo resultado esperado es `RED`.

En el pipeline, carguen `jenkins/` como Shared Library y llamen a
`circleGuardE2E('circleguard-e2e-health')` después del despliegue y prueba de
humo de dev y antes de promover a stage. La imagen de Jenkins instala las
dependencias de sistema de Chromium. Al cambiar Playwright en `e2e/`,
actualicen también `PLAYWRIGHT_VERSION` en el Dockerfile de Jenkins.
Se verificó el arranque de Chromium como usuario Jenkins en EC2.
Configuren las URL y el túnel en el agente y el usuario de prueba como
credencial **Username with password**.
El paso archiva `playwright-report/` también si falla una prueba. Dejen que
el fallo detenga el pipeline y ejecuten una sola suite a la vez por cuenta
sintética.

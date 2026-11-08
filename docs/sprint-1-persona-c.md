# Sprint 1 · HU-08 Multibranch y webhook

## Qué se implementó

- Job multibranch `circleguard` definido por JCasC en
  `infra/ci/jenkins/casc.yml`. Descubre las ramas del repositorio y lee el
  `Jenkinsfile` de la raíz.
- Shared Library global `circleguard` que apunta al directorio `jenkins/` de
  este mismo repositorio. El `Jenkinsfile` la carga con
  `@Library('circleguard') _`.
- Detección de cambios con `git diff`, sin construir los ocho servicios en
  cada push:
  - Pull request: diff contra `origin/${CHANGE_TARGET}`.
  - Rama: diff contra `GIT_PREVIOUS_SUCCESSFUL_COMMIT` o, si no existe,
    contra el commit anterior. Una rama nueva sin historial compara contra
    `origin/dev`.
  - Cambio en la raíz (`Jenkinsfile`, `build.gradle.kts`, `settings.gradle.kts`,
    `gradle/`, `gradlew`, `gradlew.bat`) o en `jenkins/` construye todo.
  - Parámetro `BUILD_ALL=true` fuerza la construcción de los ocho servicios.
- `quietPeriod` en 0 y disparador `githubPush()`. Con el webhook configurado,
  el build inicia en segundos tras el push.
- Plugins nuevos en `infra/ci/jenkins/plugins.txt`: `job-dsl` (crea el job
  multibranch desde JCasC) y `timestamper` (marca de tiempo en la consola).

## Estructura

| Archivo | Responsabilidad |
| --- | --- |
| `Jenkinsfile` | Punto de entrada del job. Carga la library, define el trigger y el parámetro `BUILD_ALL`. |
| `jenkins/vars/circleGuardServices.groovy` | Lista canónica de servicios y puertos. |
| `jenkins/vars/circleGuardChanges.groovy` | Calcula los servicios afectados con `git diff`. |
| `jenkins/vars/circleGuardPipeline.groovy` | Orquesta checkout, detección y builds en paralelo con Gradle. |
| `infra/ci/jenkins/casc.yml` | Library global, job multibranch y `quietPeriod`. |

El pipeline ejecuta `gradle --no-daemon --console=plain --max-workers=2
-Dorg.gradle.jvmargs=-Xmx512m :services:<servicio>:build` por cada módulo
afectado, publica los resultados JUnit y archiva `changed-services.txt` como
evidencia de la selección. Los módulos se construyen en lotes de dos para no
saturar el agente: ocho daemons de Gradle simultáneos consumían cerca de 5 GB
y dejaban a Jenkins sin respuesta.

## Puesta en marcha

### 1. Levantar el stack de CI (HU-04)

```bash
cp infra/ci/ci.env.example /ruta/segura/ci.env
# completar contraseñas en /ruta/segura/ci.env
bash infra/bootstrap/bootstrap-ci.sh --local /ruta/segura/ci.env
```

Jenkins queda en `http://127.0.0.1:8181`. La segunda ejecución reutiliza los
volúmenes.

Si el stack de HU-04 ya estaba corriendo, reconstruyan la imagen para instalar
`job-dsl` y `timestamper`, y vuelvan a crear el contenedor para que JCasC
registre la library y el job:

```bash
CI_JENKINS_PORT=8181 CI_JENKINS_USER=... CI_JENKINS_PASSWORD=... \
  docker compose -p circleguard-ci --env-file /ruta/segura/ci.env \
  -f infra/ci/compose.yml up -d --build --force-recreate jenkins
```

JCasC crea el job `circleguard`. Si no aparece, revisen
`docker compose -p circleguard-ci logs jenkins` y corrijan el YAML antes de
reiniciar.

### 1.1 Acceso a GitHub

La vía recomendada para el Sprint 1 es dejar el repositorio **público**.
Jenkins descubre las ramas y clona la Shared Library de forma anónima, sin
credenciales: dejen `CI_GITHUB_CREDENTIALS_ID` vacío en `ci.env`.

Si el repositorio debe permanecer privado, creen un PAT de GitHub:

- Fine-grained: acceso al repositorio `CircleGuardSoft5Comentarios` con
  permisos `Contents: Read` y `Metadata: Read` (y `Pull requests: Read` si
  quieren construir PRs). Los PR desde forks también requieren
  `Pull requests: Read`.
- Classic: scope `repo`.

Agreguen el token a `ci.env` y repitan el bootstrap para que el inicializador
cree la credencial `circleguard-github`:

```dotenv
CI_GITHUB_CREDENTIALS_ID=circleguard-github
CI_GITHUB_USER=x-access-token
CI_GITHUB_TOKEN=github_pat_...
```

El inicializador la registra mediante la API de Jenkins. Para hacerlo a mano:
**Manage Jenkins → Credentials → System → Global → Add Credentials**,
tipo *Username with password*, ID `circleguard-github`, usuario de GitHub (o
`x-access-token`) y el PAT como contraseña. Después fuercen un escaneo del job
`circleguard`.

### 2. Exponer el webhook con smee.io

GitHub necesita una URL HTTPS pública. Para la entrega usamos un canal de
[smee.io](https://smee.io):

```bash
# Crear un canal nuevo abriendo https://smee.io y copiar su URL.
npx smee-client \
  --url https://smee.io/<canal> \
  --target http://127.0.0.1:8181/github-webhook/
```

Alternativa con Docker:

```bash
docker run --rm --network host \
  ghcr.io/probot/smee-client \
  --url https://smee.io/<canal> \
  --target http://127.0.0.1:8181/github-webhook/
```

### 3. Configurar el webhook en GitHub

En el repositorio: **Settings → Webhooks → Add webhook**.

- **Payload URL**: `https://smee.io/<canal>`
- **Content type**: `application/json`
- **Which events**: *Just the push event* y *Pull requests*.
- **Active**: marcado.

El repositorio es público, por lo que el escaneo anónimo de ramas funciona. Si
se vuelve privado, creen una credencial de GitHub en Jenkins y añádanla al
bloque `branchSources.github` de `casc.yml`.

### 4. Compilar y verificar

```bash
# Módulo modificado: solo ese servicio
git checkout -b prueba-hu-08
touch services/circleguard-auth-service/src/main/resources/hu08.txt
git add . && git commit -m "test(hu-08): servicio auth"
git push origin prueba-hu-08
```

Evidencia esperada en la consola de Jenkins:

- `Servicios modificados: circleguard-auth-service`
- `changed-services.txt` con una sola línea.
- Tiempo entre el push y el inicio del build menor a 60 segundos.

```bash
# Cambio en la raíz: se construyen todos
echo "" >> build.gradle.kts
git add build.gradle.kts && git commit -m "test(hu-08): raíz"
git push origin prueba-hu-08
```

También pueden usar **Build with Parameters → BUILD_ALL** para forzar los
ocho servicios sin tocar archivos.

## Evidencia de aceptación (8 de noviembre de 2026)

- **Webhook**: push a `hu-08-demo` → build `#3` inició **5.3 segundos**
  después (criterio: menos de 60 s). El forwarder registró
  `RESP 200 event=push` en `/github-webhook/`.
- **Shared Library**: la consola muestra
  `Loading library circleguard@dev:jenkins/`.
- **Build selectivo**: `Servicios modificados: circleguard-auth-service` y una
  sola invocación `:services:circleguard-auth-service:build`; terminó en
  `BUILD SUCCESSFUL` en 22 s. El artefacto `changed-services.txt` contiene solo
  ese servicio.
- **BUILD_ALL**: con `BUILD_ALL=true` la consola muestra `BUILD_ALL activo: se
  construirán los ocho servicios` y programa los servicios en lotes de dos
  (notification, dashboard, form, promotion, etc.).
- **Cambio en la raíz**: el merge a `dev` que modificó `jenkins/` disparó un
  build que programó los ocho servicios.

Nota: el servicio `form-service` tiene un test preexistente que falla
(`AttachmentControllerTest`, carga del `ApplicationContext`). No es causado
por HU-08; el pipeline lo reporta como fallo del módulo.

## Notas

- `defaultVersion` de la library es `dev` porque HU-04 aún no está en `main`.
  Por eso HU-08 debe fusionarse en `dev` antes de la primera ejecución del
  pipeline; si quieren probarlo en la rama de la HU antes del merge, cambien
  temporalmente ese valor por `hu-08-multibranch` y reinicien Jenkins. Cuando
  HU-08 se fusione en `main`, cambien ese valor a `main`.
- El agente usa Gradle 8.14.5 y JDK 21 incluidos en la imagen de Jenkins, por
  eso el pipeline invoca `gradle` y no el wrapper. No commiteen cambios
  locales que suban el wrapper a otra versión mayor.
- El webhook de smee.io es para demostración. Para operación real, publiquen
  Jenkins tras un reverse proxy con TLS y usen esa URL en GitHub.
- `smee-client` 5.0.0 puede devolver `405 Method POST required` porque su
  `fetch` sigue un redirect y convierte el POST en GET. Para la demostración se
  usó un forwarder que conserva el método POST y la cabecera
  `X-GitHub-Event`. Si reproducen el webhook, verifiquen que Jenkins recibe un
  POST y responda 200.

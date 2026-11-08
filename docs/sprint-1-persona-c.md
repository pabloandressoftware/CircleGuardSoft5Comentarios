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
  - Rama: diff contra `GIT_PREVIOUS_SUCCESSFUL_COMMIT`; el primer build
    construye todo.
  - Cambio en la raíz (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`,
    `gradlew`, `gradlew.bat`) o en `jenkins/` construye todo.
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
:services:<servicio>:build` por cada módulo afectado, publica los resultados
JUnit y archiva `changed-services.txt` como evidencia de la selección.

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

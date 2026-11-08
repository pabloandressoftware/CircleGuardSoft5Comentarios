# Sprint 1 · Rol D · HU-07, HU-09 y HU-12

Esta rama parte de la base del proyecto (`main`, commit inicial) y solo agrega
el trabajo del rol D. No incluye archivos de los roles A, B ni C. Donde el rol D
necesita algo de otra rama, la sección "Integración con las otras ramas" dice
qué y dónde.

## HU-12 · QR y pruebas base

`gateway-service` negaba la entrada solo a los estados `CONTAGIED` y
`POTENTIAL`, que ningún servicio escribe. Ahora la regla de
`QrValidationService` usa los estados reales que promotion-service guarda en
Redis (`user:status:{anonymousId}`):

| Estado en Redis | Resultado |
| --- | --- |
| Sin estado, `ACTIVE`, `RECOVERED` | `GREEN`, entra |
| `SUSPECT`, `PROBABLE`, `CONFIRMED` | `RED`, "Access Denied: Health Risk Detected" |
| Cualquier otro valor | `RED`, "Access Denied: Unknown Health Status" (falla cerrada) |
| Token vencido o con otra firma | `RED`, "Invalid or Expired Token" |

`PROBABLE` también se niega porque está dentro del cerco obligatorio de 14 días.

La vigencia del QR se mide en milisegundos (`QR_EXPIRATION`, 60000 = 60 s). El
valor anterior, `300`, daba un QR de 0,3 s. `QrTokenService` rechaza al arrancar
cualquier valor menor de 1000 ms, porque casi siempre es un error de unidades.
`/api/v1/auth/qr/generate` responde `expiresIn` en segundos calculado de la
misma vigencia, en lugar del `"60"` fijo.

Pruebas: `QrValidationServiceTest` (gateway) cubre cada estado, el token vencido
y la firma falsa. `QrTokenServiceTest` (auth) cubre las unidades. En la app,
`useQrToken.test.ts` se actualizó a la firma actual del hook
(`anonymousId`, `authToken`) con la API simulada.

## HU-07 · Variables y Compose

### Configuración externalizada

Cada `application.yml` lee secretos y direcciones de variables de entorno. El
valor por defecto es el de desarrollo local (`docker-compose.dev.yml`), así que
el entorno local y las pruebas siguen igual. En los ambientes desplegados el
Compose exige cada secreto con `${VAR:?}`: si falta, no arranca.

| Variable | Servicios | Nota |
| --- | --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `JPA_DDL_AUTO` | auth, identity, promotion, form, dashboard | Una base por servicio en el mismo PostgreSQL |
| `JWT_SECRET`, `JWT_EXPIRATION` | auth, identity, promotion, gateway, notification | Mínimo 32 caracteres |
| `QR_SECRET`, `QR_EXPIRATION` | auth, gateway, notification | Mismo secreto en auth y gateway; milisegundos |
| `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT` | identity | `VAULT_SALT` en hexadecimal. No cambiar con datos existentes |
| `LDAP_URL`, `LDAP_BASE`, `LDAP_USERNAME`, `LDAP_PASSWORD` | auth | Antes estaban fijos en `SecurityConfig` |
| `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` | promotion | |
| `KAFKA_BOOTSTRAP_SERVERS` | promotion, form, notification | |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | promotion, gateway | Redis exige contraseña en los ambientes |
| `IDENTITY_SERVICE_URL` | auth, notification | Antes `localhost:8083` fijo: el login fallaba en contenedores |
| `PROMOTION_SERVICE_URL`, `AUTH_API_URL` | dashboard, notification | |
| `CORS_ALLOWED_ORIGINS` | auth, identity, promotion | Lista separada por comas |
| `SMTP_*`, `TWILIO_*`, `GOTIFY_*`, `LMS_API_URL`, `ROOM_BOOKING_API_URL` | notification | Opcionales |

Todos los servicios usan `server.forward-headers-strategy: native` para
reconocer el esquema HTTPS detrás del proxy.

En la app (`mobile/constants/Config.ts`) cada URL sale de
`EXPO_PUBLIC_<SERVICIO>_BASE_URL`, luego de `EXPO_PUBLIC_API_BASE_URL` y por
último del puerto local. Con `EXPO_PUBLIC_API_BASE_URL=/` la web llama a
`/api/v1/...` en su mismo origen y el proxy reparte, así la **misma imagen**
sirve en dev, stage y prod. Se corrigieron también `useGateValidation`
(importaba `API_BASE_URL`, que no existía), las dos pantallas de analítica y
`utils/proximityTask.ts`, que tenían `localhost` fijo.

### Compose por ambiente

| Archivo | Uso |
| --- | --- |
| `deploy/compose.yml` | Los 8 servicios, la web, el proxy y el middleware, con healthchecks y `depends_on: service_healthy` |
| `deploy/compose.dev.yml` | Solo dev: Redis en `127.0.0.1:6379` para Playwright (HU-15) por túnel SSH |
| `deploy/compose.prod.yml` | Stage y prod: `restart: always` en todo. Stage usa el de prod para probar exactamente lo que irá a producción |
| `deploy/Caddyfile` | Proxy HTTPS en el puerto 443: reparte `/api/v1/...` a cada servicio y lo demás a la web |
| `deploy/postgres/init-db.sql` | Crea las 5 bases al primer arranque |
| `deploy/new-env.sh` | Genera el archivo de ambiente con secretos aleatorios |
| `deploy/check-env.sh` | Revisa un archivo de ambiente antes de subirlo a Jenkins |

Solo el proxy publica un puerto (443, el único abierto en el firewall de las
VM). PostgreSQL, Neo4j, Kafka y los servicios quedan en la red interna de
Docker de cada VM. El certificado lo firma la CA interna de Caddy, porque los
ambientes no tienen dominio propio ni el puerto 80 abierto. En la Parte 2 se
reemplaza por un certificado público.

La memoria está contenida para una VM de 8 GB: cada servicio Java usa
`-Xmx320m` (`CIRCLEGUARD_JAVA_OPTS`), Kafka 512 MB y Neo4j 512 MB de heap.

### Variables para A (Jenkins)

```bash
bash deploy/new-env.sh dev   <PublicDns-dev>   [IP] > /ruta/segura/circleguard-env-dev
bash deploy/new-env.sh stage <PublicDns-stage> [IP] > /ruta/segura/circleguard-env-stage
bash deploy/new-env.sh prod  <PublicDns-prod>  [IP] > /ruta/segura/circleguard-env-prod
bash deploy/check-env.sh /ruta/segura/circleguard-env-dev   # repetir con stage y prod
```

`new-env.sh` genera secretos aleatorios distintos por ambiente. `check-env.sh`
revisa que no falte nada, los largos y formatos, y que Docker Compose acepte la
configuración completa. Suban cada archivo a Jenkins como **Secret file** con
los IDs `circleguard-env-dev`, `circleguard-env-stage` y `circleguard-env-prod`;
el pipeline lo copia a `/opt/circleguard/.env` de la VM. No los suban a Git.

### Despliegue de un ambiente (C, HU-10)

El `.env` define `COMPOSE_FILE`, así que en la VM basta con `docker compose`.
Además del `.env`, la VM necesita los archivos de `deploy/`:

```bash
scp -r deploy/compose.yml deploy/compose.dev.yml deploy/compose.prod.yml \
  deploy/Caddyfile deploy/postgres "$CIRCLE_SSH_TARGET":/opt/circleguard/
```

Luego, con el túnel inverso hacia el registro de Nexus (rol B):

```bash
sudo IMAGE_TAG=vX.Y.Z docker compose pull
sudo IMAGE_TAG=vX.Y.Z docker compose up -d --wait
```

`--wait` termina cuando todos los healthchecks están sanos, o falla si alguno
no lo logra. Sirve como prueba de humo del despliegue.

### E2E de A en dev (HU-15)

| Variable | Valor |
| --- | --- |
| `E2E_WEB_URL`, `E2E_AUTH_URL`, `E2E_FORM_URL`, `E2E_GATEWAY_URL` | `https://<PublicDns-dev>` (el proxy enruta todo) |
| `E2E_IGNORE_HTTPS_ERRORS` | `1` (acepta la CA interna de Caddy; ver "Integración") |
| `E2E_REDIS_URL` | `redis://:<REDIS_PASSWORD>@127.0.0.1:6379` con `ssh -N -L 6379:127.0.0.1:6379 ubuntu@<dev>` |

### Evidencias para la entrega

```bash
sudo docker compose ps                            # todos "healthy"
sudo docker compose exec -T postgres pg_isready   # PostgreSQL listo
curl -k https://<PublicDns>/api/v1/questionnaires/active   # el proxy enruta a form
```

## HU-09 · Pruebas y quality gate

- **Gradle:** el build raíz aplica JaCoCo a los 8 servicios. `test` siempre
  termina con `jacocoTestReport`, en XML para SonarQube y HTML para Jenkins.
  El plugin `org.sonarqube` analiza el monorepo como el proyecto `circleguard`.
  La versión del análisis sale de `version.txt` si existe (Release Please) y,
  si no, queda en `0.0.0`.
- **Jenkins:** `jenkins/vars/circleGuardQuality.groovy` corre
  `gradle --continue test jacocoTestReport`. Publica JUnit y la cobertura de
  cada servicio, también si algo falla. Luego analiza con
  `withSonarQubeEnv('sonarqube')` y espera `waitForQualityGate abortPipeline: true`.
  Un gate en ERROR detiene el pipeline.
- **Conexión SonarQube–Jenkins:** `scripts/conectar-sonar-jenkins.sh` crea el
  webhook de SonarQube hacia Jenkins, la credencial `sonar-token` y el servidor
  `sonarqube` en Jenkins. Se corre una vez en la VM de CI y se puede repetir:

  ```bash
  bash scripts/conectar-sonar-jenkins.sh /ruta/segura/ci.env
  ```

  Sin el webhook, `waitForQualityGate` espera hasta su timeout en vez de cortar
  el pipeline.
- **Publicación oficial:** la etapa "Publicar imágenes" solo corre en builds
  de un tag `vX.Y.Z`. Release Please crea esos tags al fusionar en `main`, así
  que ramas y PR nunca publican. `circleGuardPublish.groovy` usa la credencial
  `nexus-registry` y los scripts de empaquetado del rol B.

### Demostración de un PR rechazado

SonarQube Community no analiza ramas ni PR por separado. Cada análisis
actualiza el proyecto `circleguard` y el gate evalúa el código nuevo desde el
análisis anterior.

1. Asegúrense de que `main` tenga al menos un build verde con análisis: es la
   línea base.
2. Creen la rama `demo/gate-rechazado` y agreguen una clase con lógica y sin
   pruebas. Por ejemplo,
   `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/AccessWindowPolicy.java`,
   con un par de métodos con condicionales.
3. Abran el PR. Las pruebas pasan, pero el código nuevo tiene una cobertura
   menor a la que exige el gate, y el log muestra
   `Pipeline aborted due to quality gate failure: ERROR`. El check de GitHub
   queda en rojo y la protección de `main` impide fusionar.
4. Guarden capturas del log, del gate en SonarQube y del PR bloqueado.
   Cierren el PR sin fusionar y vuelvan a ejecutar el build de `main` para
   restaurar su análisis.

## Integración con las otras ramas

Esta rama no modifica archivos de otros roles. Al juntar las ramas, estas
piezas se conectan así:

| Archivo (rol) | Qué necesita el rol D | Cambio |
| --- | --- | --- |
| `mobile/Dockerfile` (B) | Que la web llame a su mismo origen | Antes de `expo export`, agregar `ARG EXPO_PUBLIC_API_BASE_URL=/` y `ENV EXPO_PUBLIC_API_BASE_URL=${EXPO_PUBLIC_API_BASE_URL}` |
| `e2e/playwright.config.js` (A) | Aceptar el certificado de Caddy | En `use`, agregar `ignoreHTTPSErrors: process.env.E2E_IGNORE_HTTPS_ERRORS === '1'` |
| Servidor de CI (B) | Jenkins con los plugins `sonar`, `junit` y `htmlpublisher`, y la etiqueta `docker` | Correr `scripts/conectar-sonar-jenkins.sh` una vez levantado |
| `scripts/release-image-tag.mjs` y `scripts/package-images.sh` (B) | Versión e imágenes para la publicación oficial | Los usa `circleGuardPublish.groovy` sin cambios |
| Quality gate `CircleGuard` (B) | Umbral de cobertura del código nuevo | Ya lo configura el servidor de CI de B |
| Despliegue en el `Jenkinsfile` (C, HU-10) | Usar el Compose de HU-07 | Agregar las etapas donde está el comentario de HU-10 |

## Verificación

```bash
bash scripts/verificar-rol-d.sh /ruta/segura/circleguard-env-dev /ruta/segura/circleguard-env-prod
```

Corre en cualquier máquina con Docker y python3 (la VM de CI sirve). Ejecuta
las pruebas JUnit y la cobertura de los 8 servicios, las pruebas HU-12 y Jest,
y revisa los archivos de ambiente. Deja la evidencia en `evidencias/rol-d/`.

## Pendientes conocidos

- `JPA_DDL_AUTO` queda en `update`. Pasar a `validate` en stage y prod cuando
  dev confirme que las migraciones Flyway coinciden con las entidades.
- file-service guarda los archivos dentro del contenedor y se pierden al
  recrearlo. Pasa a S3 o a un volumen en la Parte 2.
- La CA interna de Caddy produce un aviso en el navegador hasta que haya
  dominio y certificado público.
- La base tiene en Git las carpetas `services/*/bin/` que genera el IDE, con
  copias viejas de los `application.yml`. Esta rama no las toca; conviene
  quitarlas y agregar `**/bin/` al `.gitignore` cuando el equipo lo decida.

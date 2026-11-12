# Sprint 1 · HU-04 y HU-06

## HU-04 · Jenkins, SonarQube y Nexus

El stack usa Docker Compose y guarda los datos de Jenkins, SonarQube,
PostgreSQL y Nexus en volúmenes del host. Jenkins carga JCasC y los plugins de
`infra/ci/jenkins/plugins.txt`. El agente integrado se identifica con la
etiqueta `docker` y trae Java 21, Gradle, Node, npm y Docker. El socket de
Docker da al agente control del host de CI y permite ejecutar Testcontainers.
Usen esta VM dedicada para builds de confianza.

Copien `infra/ci/ci.env.example` fuera del repositorio y completen las
credenciales. Usen contraseñas distintas. Para un SonarQube recién creado,
`CI_SONAR_INITIAL_PASSWORD` es el valor inicial documentado por SonarQube.
El script lo cambia a `CI_SONAR_PASSWORD`, que debe incluir mayúsculas,
minúsculas, números y símbolos. En Nexus lee la contraseña inicial
generada en el volumen y la reemplaza por `CI_NEXUS_PASSWORD`.
Usen comillas simples en el archivo dotenv para valores con `$` literal.

```bash
bash infra/bootstrap/bootstrap-ci.sh --local /ruta/segura/ci.env
bash infra/bootstrap/bootstrap-ci.sh --local /ruta/segura/ci.env
```

La segunda ejecución reutiliza los contenedores y volúmenes si no cambiaron
la configuración ni las imágenes. El inicializador
configura el gate por defecto `CircleGuard`, que falla por debajo del 60 % de
cobertura en código nuevo, incluidas las modificaciones pequeñas. También
configura el repositorio Docker privado `circleguard` de Nexus con política
`ALLOW_ONCE` para impedir la sobrescritura de una imagen publicada.

En una instancia nueva de Nexus, el script deja el stack operativo y sale con
código 3 hasta que el administrador revise y acepte la
[licencia de Community Edition](https://help.sonatype.com/en/ce-onboarding.html).
Entren como `admin` con `CI_NEXUS_PASSWORD` a la consola de Nexus (por defecto
`http://127.0.0.1:8081`). Tras completar el asistente, repitan el comando para
comprobar el acceso al registro Docker.

Para aprovisionar AWS, preparen el acceso de CLI, la llave SSH y las variables
`AWS_REGION`, `EC2_KEY_NAME`, `EC2_AMI_ID` y `SSH_CIDR` descritas en el
[procedimiento de ambientes](sprint-1-persona-a.md). Carguen la llave SSH en
el agente de su terminal. Ejecuten:

```bash
bash infra/bootstrap/bootstrap-ci.sh --aws /ruta/segura/ci.env
```

El comando crea el stack `circleguard-ci`, espera la instalación de Docker,
transfiere la configuración y levanta los mismos servicios. Las consolas y el
registro se enlazan a loopback en la VM. Para administración por SSH:

```bash
ssh -N -L 8181:127.0.0.1:8181 -L 9000:127.0.0.1:9000 \
  -L 8081:127.0.0.1:8081 -L 5000:127.0.0.1:5000 ubuntu@HOST_CI
```

| Servicio | Puerto local predeterminado |
| --- | --- |
| Jenkins | 8181 |
| SonarQube | 9000 |
| Nexus | 8081 |
| Registro Docker de Nexus | 5000 |

La integración de HU-08 necesita un punto de entrada HTTPS para el webhook
de GitHub. Para acceder al registro desde las VM de HU-10, publiquen el
conector tras un proxy HTTPS de confianza o provisionen un túnel desde los
hosts de despliegue. En la red interna del stack, Jenkins puede usar
`http://sonarqube:9000` para SonarQube.

## HU-06 · Construcción y publicación de imágenes

El `Dockerfile` de la raíz compila el servicio indicado con Gradle y JDK 21,
ejecuta el JAR con JRE 21 como usuario sin privilegios y comprueba la
respuesta HTTP del servicio. `mobile/Dockerfile` instala las dependencias del
lockfile, exporta Expo y sirve las rutas estáticas con nginx sin privilegios.
La raíz y el cliente web tienen contextos de compilación separados.

```bash
# Construcción de los ocho servicios y el cliente web
bash scripts/package-images.sh v0.0.1

# Publicación de una versión de release desde Jenkins
IMAGE_TAG=$(node scripts/release-image-tag.mjs)
REGISTRY=127.0.0.1:5000 bash scripts/package-images.sh "$IMAGE_TAG" --push
```

Autentiquen el registro antes de publicar. En Jenkins usen su credencial de
usuario y contraseña de Nexus para `docker login --password-stdin`.
Configuren `REGISTRY` con el host y puerto publicados del conector.
El empaquetado añade el tag SemVer y las etiquetas OCI de versión y commit.
Si el árbol de trabajo tiene cambios, la etiqueta de revisión añade `-dirty`.
Las URLs que usa el navegador y el Compose de los servicios en cada
ambiente se integran con HU-07.

# Informe · Parte 1 (Taller 3) · CircleGuard

Ingeniería de Software V · Universidad Icesi · Entrega: domingo 8 de noviembre de 2026, 11:59 p. m.

Repositorio de trabajo: https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios
Tablero: Jira, proyecto KAN (`seraph.atlassian.net`)

Equipo: Pablo Guzmán (`pabloandressoftware`), Daniel Martínez (`damaral2005`), Rodolfo Moreno (`Matatrolls`), NIGORI YT (`itsnigori98`).

> Este informe distingue entre lo **verificado con ejecución**, lo que tiene **código integrado sin evidencia de ejecución** y lo **no implementado**. Lo que solo el equipo conoce está marcado `[COMPLETAR]`.

## 1. Resumen

CircleGuard es un sistema universitario de rastreo de contactos y cercos sanitarios que preserva el anonimato. Se compone de 8 microservicios (Java 21, Spring Boot, Gradle), un cliente Expo/React Native con salida web y servicios de soporte (PostgreSQL, Neo4j, Kafka, Redis, OpenLDAP). El código base no traía contenedores, pipelines, infraestructura declarativa ni pruebas de extremo a extremo.

La Parte 1 lleva el proyecto desde el commit hasta el despliegue: gobierno del repositorio, aprovisionamiento de Jenkins, SonarQube y Nexus, empaquetado en imágenes Docker, pipeline multibranch con análisis de calidad, versionado automático, ambientes en AWS y pruebas E2E con Playwright.

## 2. Marco ágil y tablero

- **Marco:** Scrum (sprints 0, 1 y 2) con un tablero Kanban en Jira. La justificación completa está en `docs/agil/marco.md`.
- **Backlog:** 12 épicas, 29 historias de usuario y 144 puntos. Cada HU tiene criterios de aceptación, puntos, responsable e iteración en su descripción, y las HU grandes tienen subtareas.
- **Alcance de la Parte 1:** HU-01 a HU-16 (16 historias, 74 puntos). La Parte 2 (Sprint 2) cubre HU-17 a HU-29.
- **Documentación de sprints:** `docs/agil/sprint-0.md` y `docs/agil/sprint-1.md` (planeación, review y retrospectiva). Las fechas y las reflexiones del equipo están marcadas `[COMPLETAR]` en esos archivos.

| Rol | Responsabilidad | Persona |
| --- | --- | --- |
| A | Gestión ágil, versiones y E2E | Daniel Martínez |
| B | Infraestructura | Pablo Guzmán |
| C | Pipeline y despliegue | Rodolfo Moreno |
| D | Configuración y calidad | NIGORI YT |

## 3. Estrategia de ramas y gobierno (HU-02)

```
hu-XX  ->  dev  ->  main
```

- **Una rama por HU** (`hu-01` … `hu-15`). Las que dependen de otra la heredan: `hu-04` parte de `hu-05` y `hu-08` parte de `hu-04`.
- **Entrada a `dev` solo por Pull Request.** Cada PR indica el responsable según Jira y lo aprueba otra persona; el autor no aprueba su propia HU.
- **`main` y `dev` están protegidas** y las reglas valen también para el propietario del repositorio:

| Regla | `main` | `dev` |
| --- | --- | --- |
| PR obligatorio con 1 aprobación | Sí | Sí |
| Checks requeridos | `commitlint` y `continuous-integration/jenkins/pr-merge` | `commitlint` |
| Sin push forzado ni borrado | Sí | Sí |
| Rama al día antes de mergear | Sí | No |
| Conversaciones resueltas | Sí | Sí |

- **commitlint** valida Conventional Commits en cada PR mediante GitHub Actions (`.github/workflows/commitlint.yml`).
- **Plantillas** de PR y de solicitud de cambio con tipo, riesgo y rollback, y una tabla de aprobadores por tipo de cambio en `docs/gobernanza/cambios.md`.

**Limitación:** el check de Jenkins está exigido en `main`, pero Jenkins todavía no lo publica en este repositorio (faltan un Jenkins accesible desde GitHub, el token y el webhook). Hasta entonces `main` no admite merges.

## 4. Arquitectura de CI/CD

```
push / PR ──► GitHub ──webhook──► Jenkins (multibranch + Shared Library)
                                   │
                  detectar módulos modificados (HU-08)
                                   │
        compilar, pruebas, JaCoCo ──► SonarQube (quality gate ≥ 60 % en código nuevo)
                                   │
        pruebas del cliente web (Jest)
                                   │
        tag vX.Y.Z ──► empaquetar 9 imágenes ──► Nexus (registro Docker)
```

| Pieza | Qué hace | HU |
| --- | --- | --- |
| `infra/bootstrap/bootstrap-ci.sh` | Levanta Jenkins, SonarQube (con PostgreSQL) y Nexus con Docker Compose, en local (`--local`) o en una VM de AWS (`--aws`). Es repetible. | HU-04 |
| JCasC + `plugins.txt` | Configura Jenkins por código: Shared Library `circleguard`, job multibranch, agente con etiqueta `docker`. | HU-04, HU-08 |
| Quality gate `CircleGuard` | Falla si la cobertura del código nuevo es menor a 60 %. | HU-04 |
| `Dockerfile` (ARG `SERVICE`) | Imagen multi-stage: compila con JDK 21 y ejecuta con JRE 21 y usuario sin privilegios. | HU-06 |
| `mobile/Dockerfile` | `expo export` y nginx sin privilegios. | HU-06 |
| `scripts/package-images.sh` | Construye y publica las 9 imágenes (8 servicios y la web) en Nexus. | HU-06 |
| `Jenkinsfile` unificado | Pipeline declarativo: disparo `githubPush`, parámetro `BUILD_ALL`, detección de módulos (HU-08), calidad y gate bloqueante (HU-09), pruebas del web y publicación solo en tags `vX.Y.Z`. | HU-08, HU-09 |
| release-please | Calcula la versión SemVer a partir de Conventional Commits y publica tags y notas de versión. | HU-03 |
| `infra/bootstrap/bootstrap-env.sh` | Crea una VM con Docker en AWS EC2 por ambiente (dev, stage, prod) con CloudFormation. | HU-05 |
| `deploy/` (Compose) | Compose por ambiente con proxy HTTPS (Caddy), healthchecks y variables externalizadas. | HU-07 |
| `e2e/` (Playwright) | Pruebas de login, cuestionario y QR, con etapa `circleGuardE2E` para Jenkins. | HU-15 |

## 5. Estado de las historias de usuario

Estado en Jira al redactar este informe y evidencia disponible. «Verificado» significa que se ejecutó y se observó el resultado; «documentado» significa que el responsable lo registra en `docs/` pero no se reprodujo para este informe.

| HU | Historia | Responsable (Jira) | Jira | Evidencia real |
| --- | --- | --- | --- | --- |
| HU-01 | Tablero ágil y sprints documentados | Rodolfo Moreno | Por hacer | Documentos en `docs/agil/` integrados. Pendientes: marcas `[COMPLETAR]` y acceso del docente al tablero. |
| HU-02 | Proteger `main` y formalizar PR | Pablo Guzmán | Finalizada | **Verificado:** protección activa en `main` y `dev`, commitlint pasando en GitHub Actions, plantillas y tabla de aprobadores. **Parcial:** el check de Jenkins no reporta. |
| HU-03 | SemVer y notas de entrega | Daniel Martínez | En curso | Código integrado (release-please, `version.txt`). Sin ejecución verificada. |
| HU-04 | Jenkins, SonarQube y Nexus | Pablo Guzmán | Finalizada | **Verificado en local:** el bootstrap levanta el stack, dos ejecuciones no duplican recursos, el gate exige 60 %, credenciales fuera del repo. **No probado:** modo `--aws` y un análisis que falle el gate. |
| HU-05 | Ambientes dev, stage y prod | Daniel Martínez | En curso | Código integrado. **Documentado** por el responsable: instancias EC2 por ambiente y una prueba de que dev no alcanza la base de prod (tiempo agotado al puerto 5432). Falta la URL HTTPS de la aplicación (criterio 4). |
| HU-06 | Ocho servicios Java y cliente web | Pablo Guzmán | Finalizada | **Verificado en local:** 9 imágenes construidas y publicadas en un Nexus local con etiqueta `v0.0.1`, usuario sin privilegios y HEALTHCHECK en las 9. No se publicó en el Nexus de AWS. |
| HU-07 | Configuración externalizada y Compose por ambiente | NIGORI YT | Por hacer | Código integrado (`deploy/`, variables). Sin ejecución verificada. |
| HU-08 | Jenkins por push y módulos afectados | Rodolfo Moreno | Finalizada | **Documentado** por el responsable en `docs/sprint-1-persona-c.md`: build iniciado 5,3 s después del push, build selectivo de un servicio en 22 s, `BUILD_ALL` y cambio en la raíz. No reproducido para este informe. |
| HU-09 | Pruebas y bloqueo con SonarQube | NIGORI YT | Por hacer | Código integrado (JaCoCo, gate bloqueante). El `Jenkinsfile` unificado con HU-08 **no se ha ejecutado en Jenkins**. |
| HU-10 | Desplegar en dev y promover | Rodolfo Moreno | En curso | **No implementada.** Sin rama ni código; hay un marcador en el `Jenkinsfile`. |
| HU-11 | Notificar fallos y rollback | Sin asignar | Por hacer | **No implementada.** |
| HU-12 | Pruebas base y validación del QR | NIGORI YT | Por hacer | Código integrado (QR en milisegundos, acceso denegado a `SUSPECT`, `PROBABLE` y `CONFIRMED`). Sin ejecución verificada aquí. |
| HU-13 | 60 % de cobertura en cinco servicios | Sin asignar | Por hacer | **No implementada.** Hay JaCoCo (HU-09), pero no se midió la cobertura por servicio. |
| HU-14 | Tres integraciones con Testcontainers | Sin asignar | Por hacer | **No implementada.** |
| HU-15 | Tres flujos E2E con Playwright | Daniel Martínez | En curso | Código integrado. **Documentado** por el responsable: 4 pruebas pasaron en local el 6 de nov; la de `SUSPECT` dependía de HU-12. No ejecutado desde Jenkins. |
| HU-16 | Documentar operación y evidencias | Sin asignar | Por hacer | Este informe y los documentos `docs/sprint-1-persona-*.md`. |

**Resumen:** de 74 puntos de la Parte 1, Jira marca como finalizados 21 (HU-02, 04, 06 y 08). Las demás historias tienen código o documentación, pero la mayoría **sin evidencia de ejecución integrada**. HU-10, 11, 13 y 14 no se implementaron.

## 6. Problemas encontrados y cómo se resolvieron

| Problema | Impacto | Resolución |
| --- | --- | --- |
| Las branch rules no se pueden configurar en la organización `ingesoft5` (plan GitHub Team) | HU-02 no era viable en el repositorio original | Se creó el repositorio bajo la cuenta de `pabloandressoftware` (público, donde las reglas son gratuitas), se migraron todas las ramas con su historial y se reconstruyó `dev` con un PR por HU. Se conserva `dev-respaldo` con el `dev` anterior. |
| El mirror de plugins de Jenkins (`ftp.halifax.rwth-aachen.de`) estuvo caído | `bootstrap-ci.sh` se colgó 17 minutos | Se fijó `JENKINS_UC_DOWNLOAD` a otro mirror en el Dockerfile de Jenkins. Queda como dependencia externa. |
| HU-08 y HU-09 modifican el mismo `Jenkinsfile` con diseños distintos | Conflicto al integrar la segunda | Se unieron en un solo pipeline declarativo que conserva los elementos de ambas. La sintaxis de Groovy se validó, pero el pipeline unificado **no se ejecutó en Jenkins**. |
| Un PR de HU se envió a `main` en lugar de `dev` | PR cerrado y trabajo repetido | Se cerró y se abrió otro PR hacia `dev`; los PR de HU-09 y HU-12 se redirigieron a `dev` cambiando su base. El flujo `hu-XX -> dev -> main` quedó documentado. |
| La regla `subject-case` de commitlint rechazaba asuntos con siglas (`URLs`, `JaCoCo`) | Fallaban los PR de HU-07 y HU-09 | Se desactivó solo esa regla; el resto de Conventional Commits sigue validándose. |
| Las aprobaciones se descartaban solas cuando `dev` cambiaba | Un PR aprobado volvía a «revisión requerida» | Se desactivó «descartar aprobaciones antiguas» en `dev`. |
| Una rama de HU nueva no tenía el check commitlint | El check es obligatorio y no corría | Se actualizó la rama con `dev` para que el workflow se ejecutara. |

## 7. Limitaciones y trabajo pendiente

- **Check de Jenkins en `main`:** exigido pero sin publicar. Falta un Jenkins accesible desde internet, un token de GitHub y el webhook. La demostración del webhook de HU-08 usó un túnel smee.io.
- **Pipeline de punta a punta:** el `Jenkinsfile` unificado no se ha corrido; tampoco se probó el gate de SonarQube bloqueando una cobertura menor a 60 %.
- **AWS:** el modo `--aws` del bootstrap y la publicación en el Nexus de AWS no se probaron. La URL HTTPS de la aplicación por ambiente está pendiente (DNS y TLS).
- **No implementadas:** HU-10 (despliegue y promoción), HU-11 (notificaciones y rollback), HU-13 (cobertura de 60 % en cinco servicios) y HU-14 (Testcontainers).
- **Deuda conocida:** un test preexistente de `form-service` falla (`AttachmentControllerTest`); `file-service` guarda archivos dentro del contenedor; hay carpetas `services/*/bin/` del IDE versionadas.
- **Jira:** conviene alinear los estados con la evidencia real; varias HU con código integrado siguen «Por hacer» o «En curso».

## 8. Contribución individual

[COMPLETAR por el equipo: qué hizo cada integrante, con enlaces a PR y commits. La trazabilidad del repositorio muestra: Pablo (HU-02, 04, 06), Daniel (HU-03, 05, 15), Rodolfo (HU-01, 08), NIGORI (HU-07, 09, 12).]

## 9. Declaración de uso de IA generativa

Siguiendo la regla del curso (nivel 3: colaboración permitida con declaración de uso y registro de prompts), el equipo declara que se usó **Claude Code** (Anthropic) para:

- verificar la ejecución local de HU-04 y HU-06 (levantar el stack, construir y publicar las imágenes);
- migrar el repositorio, repartir el trabajo en una rama por HU y configurar la protección de ramas;
- redactar la configuración de commitlint, las plantillas de PR y de cambio, y la documentación de HU-01, HU-02 y este informe;
- resolver el conflicto del `Jenkinsfile` entre HU-08 y HU-09.

Todo resultado fue revisado por el equipo antes de integrarse mediante PR con aprobación de otra persona. Lo que no se pudo ejecutar se declara como tal en este informe.

**Anexo · registro de prompts:** [COMPLETAR por el equipo: pegar el registro de prompts].

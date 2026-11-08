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
- **Alcance acordado del Sprint 1 (esta entrega):** 10 historias y 49 puntos: HU-03, 04, 05, 06, 07, 08, 09, 10, 12 y 15. HU-01 y HU-02 pertenecen al Sprint 0. HU-11, 13, 14 y 16 quedaron **fuera del alcance acordado de esta entrega** y pasan al siguiente sprint. La Parte 2 (Sprint 2) cubre HU-17 a HU-29.
- **Fuentes del estado:** Jira (corte de hoy), el repositorio y el documento del equipo «Sprint 1 · Pendientes por persona» (corte del 6 de noviembre). Lo que proviene de ese documento se cita como «según los registros del equipo (corte 6-nov)» y no se presenta como verificado para este informe.
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

Criterios: **verificado** = se ejecutó y se observó el resultado al preparar este informe; **registros del equipo** = consta en el documento «Sprint 1 · Pendientes por persona» (corte 6-nov) o en `docs/sprint-1-persona-*.md`, sin reproducirse aquí; **código integrado** = está en `dev` sin evidencia de ejecución.

### Alcance acordado del Sprint 1 (10 HU, 49 puntos)

| HU | Historia | Pts | Responsable (Jira) | Jira | Evidencia |
| --- | --- | --- | --- | --- | --- |
| HU-03 | Versionado y releases | 3 | Daniel Martínez | En curso | Código integrado (release-please, `version.txt`). Según los registros del equipo: en progreso, verificando el tag SemVer contra el commit; falta integrar release-please en `main` y confirmar el permiso para abrir el PR de release. |
| HU-04 | Jenkins, SonarQube y Nexus | 8 | Pablo Guzmán | Finalizada | **Verificado en local:** el bootstrap levanta el stack, dos ejecuciones no duplican recursos, el gate exige 60 % de cobertura nueva, credenciales fuera del repo. Registros del equipo: servidor de CI aprovisionado en EC2 con bootstrap repetido. No probado por mí: modo `--aws` y un análisis que falle el gate. |
| HU-05 | Ambientes en AWS | 5 | Daniel Martínez | En curso | Código integrado. Registros del equipo: tres VM en Ohio (`us-east-2`) para dev, stage y prod, bootstrap repetido y aislamiento de PostgreSQL entre dev y prod comprobado. Falta el Compose y los valores de HU-07, los archivos de entorno en Jenkins y las URL HTTPS. |
| HU-06 | Imágenes Docker | 5 | Pablo Guzmán | Finalizada | **Verificado en local:** 9 imágenes (8 servicios y la web) construidas y publicadas con etiqueta `v0.0.1` en un Nexus local, usuario sin privilegios y HEALTHCHECK en las 9. Registros del equipo: nueve imágenes `v0.0.1` comprobadas en Nexus. |
| HU-07 | Variables y Compose | 5 | NIGORI YT | Por hacer | Código integrado en `dev` (`deploy/`, variables de entorno). Registros del equipo: faltaba evidencia del Compose definitivo por ambiente. |
| HU-08 | Multibranch y webhook | 5 | Rodolfo Moreno | Finalizada | Evidencia **documentada por su responsable** en `docs/sprint-1-persona-c.md`: build iniciado 5,3 s después del push, build selectivo de un servicio en 22 s, `BUILD_ALL` y cambio en la raíz. No reproducida aquí. |
| HU-09 | Pruebas y quality gate | 5 | NIGORI YT | Por hacer | Código integrado (JaCoCo, gate bloqueante, publicación limitada a tags). Unido al `Jenkinsfile` de HU-08, y ese pipeline unificado **no se ha ejecutado en Jenkins**. Registros del equipo: SonarQube ya tiene el gate de 60 %. |
| HU-10 | Despliegue y promoción | 5 | Rodolfo Moreno | En curso | **Sin código en `dev`**; solo un marcador en el `Jenkinsfile`. Registros del equipo: las tres VM están disponibles; falta evidencia del flujo (despliegue y humo en dev, E2E, promoción del mismo digest a stage y aprobación manual de prod). |
| HU-12 | QR y pruebas base | 3 | NIGORI YT | Por hacer | Código integrado (vigencia del QR en milisegundos, acceso denegado a `SUSPECT`, `PROBABLE` y `CONFIRMED`; PR #13 mergeado). Registros del equipo (6-nov): el gateway dejaba pasar `SUSPECT`; el fallo se corrigió con este cambio, sin ejecución verificada aquí. |
| HU-15 | Pruebas Playwright | 5 | Daniel Martínez | En curso | Código integrado. Registros del equipo: Chromium validado en Jenkins y 4 de 5 casos pasaron en local; el de `SUSPECT` fallaba hasta la corrección de HU-12. No se ha ejecutado la suite completa desde Jenkins. |

### Sprint 0 (previo)

| HU | Historia | Pts | Responsable (Jira) | Jira | Evidencia |
| --- | --- | --- | --- | --- | --- |
| HU-01 | Tablero ágil y sprints documentados | 3 | Rodolfo Moreno | Por hacer | Documentos en `docs/agil/` integrados. Pendientes: las marcas `[COMPLETAR]` y confirmar el acceso del docente al tablero. |
| HU-02 | Proteger `main` y formalizar PR | 3 | Pablo Guzmán | Finalizada | **Verificado:** protección activa en `main` y `dev`, commitlint pasando en GitHub Actions, plantillas y tabla de aprobadores. **Parcial:** el check de Jenkins no se publica aún. |

### Fuera del alcance acordado de esta entrega (pasan al siguiente sprint)

HU-11 (notificar fallos y rollback), HU-13 (60 % de cobertura en cinco servicios), HU-14 (tres integraciones con Testcontainers) y HU-16 (documentar operación y evidencias). No se implementaron porque no estaban en el alcance acordado. Este informe y los documentos `docs/sprint-1-persona-*.md` son la documentación de operación disponible hoy.

**Resumen:** del alcance acordado (49 puntos), Jira marca como finalizadas HU-04, 06 y 08 (18 puntos). HU-03, 05, 10 y 15 siguen en curso. HU-07, 09 y 12 tienen su código integrado en `dev` pero siguen «Por hacer» en Jira. La mayor brecha es HU-10 (sin código) y que el pipeline unificado no se ha ejecutado de punta a punta.

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
- **Dentro del alcance y sin implementar:** HU-10 (despliegue y promoción).
- **Fuera del alcance acordado de esta entrega:** HU-11 (notificaciones y rollback), HU-13 (cobertura de 60 % en cinco servicios), HU-14 (Testcontainers) y HU-16 (documentación de operación).
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

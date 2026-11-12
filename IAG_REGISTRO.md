# Registro de uso de IA generativa (IAG)

Proyecto CircleGuard · Ingeniería de Software V · Parte 1. Nivel 3 del curso: colaboración permitida con declaración de uso y registro de prompts. Este archivo es el anexo oficial; el informe lo resume.

## Declaración de uso de IA generativa

Siguiendo la regla del curso (nivel 3: colaboración permitida con declaración de uso y registro de prompts), el equipo declara que se usó **Claude Code** (Anthropic) para:

- verificar la ejecución local de HU-04 y HU-06 (levantar el stack, construir y publicar las imágenes);
- migrar el repositorio, repartir el trabajo en una rama por HU y configurar la protección de ramas;
- redactar la configuración de commitlint, las plantillas de PR y de cambio, y la documentación de HU-01, HU-02 y este informe;
- resolver el conflicto del `Jenkinsfile` entre HU-08 y HU-09.

Todo resultado fue revisado por el equipo antes de integrarse mediante PR con aprobación de otra persona. Lo que no se pudo ejecutar se declara como tal en este informe.

## Bitácora de uso de IA

**Método.** El equipo trabajó con Claude Code (Anthropic) como asistente de ingeniería sobre el repositorio, la cuenta de Jira y un Jenkins local de pruebas. Las preguntas de esta bitácora están **reformuladas** a partir de la conversación real para que se lean con claridad; el registro textual completo de la sesión puede entregarse a solicitud del docente. Cada fila muestra qué se preguntó, qué hizo el asistente y qué **decidió o verificó una persona**. No se incluyen contraseñas ni tokens.

**Reglas de control que se mantuvieron durante todo el proceso**

- El asistente no integró ningún PR sin aprobación de otra persona, y las acciones sensibles (crear el repositorio público, reescribir historia, tocar reglas de protección, mergear PR propios) pasaron por un control de permisos que las bloqueó hasta tener autorización explícita.
- Lo que el asistente no pudo ejecutar se declaró como tal: modo `--aws`, gate fallando por cobertura, check de Jenkins en `main`.
- El asistente leyó el servidor de AWS solo con permiso, en modo lectura, y no usó una llave privada compartida por chat.

| # | Pregunta al asistente (reformulada) | Qué hizo la IA | Decisión y verificación humana |
| --- | --- | --- | --- |
| 1 | «Hice tres commits con la cuenta de un compañero. ¿Puedes pasarlos a mi usuario sin tocar el código?» | Analizó la rama, explicó que reescribir historia exige un `force-push` y pidió elegir entre reescribir o abrir una rama nueva. Reescribió autor y committer y comprobó que el diff contra la versión anterior estaba vacío. | Pablo eligió reescribir. Se verificó en GitHub que los tres commits aparecen a su nombre y que el contenido no cambió. |
| 2 | «¿Qué piden realmente los criterios de HU-04 y HU-06 y cómo lo demostramos con evidencia y no con fe?» | Leyó los criterios en Jira por API, los comparó con el código y levantó Jenkins, SonarQube y Nexus en local. Ejecutó el bootstrap dos veces comparando contenedores, volúmenes y redes, y publicó las 9 imágenes. | Se aceptó como verificado solo lo ejecutado; el modo `--aws` y un gate que falle se dejaron declarados como no probados. |
| 3 | «El profesor dice que las branch rules no funcionan en la organización. ¿Cómo migramos sin perder historial y dejamos una rama por HU?» | Propuso un repositorio nuevo, subió las 19 ramas con sus SHA, repartió los 40 archivos de un bloque en ramas por HU y comprobó que su suma era idéntica a `dev` (diff vacío). Configuró protección de `main` y `dev`, commitlint y plantillas. | El equipo validó el plan, creó el repositorio público y aceptó las invitaciones. La equivalencia se comprobó con `git diff`. |
| 4 | «¿Qué riesgo corremos si borramos `dev` y metemos cada HU por su propio PR?» | Expuso el costo (unas 12 aprobaciones encadenadas y un `dev` incompleto mientras tanto) y una alternativa más ligera. Creó `dev-respaldo` antes del reset. | Pablo eligió el reset completo. Al final se verificó que `dev` quedó igual al respaldo más lo nuevo, sin archivos perdidos. |
| 5 | «Cada PR debe salir a nombre de su responsable en Jira y lo aprueba otra persona. ¿Cómo lo automatizamos?» | Explicó que no puede suplantar cuentas, propuso enlaces de PR prellenados, un script y un workflow, y dejó la regla en la plantilla de PR. | Cada integrante abrió su propio PR; las aprobaciones fueron personas distintas del autor y del responsable. |
| 6 | «HU-08 y HU-09 chocan en el `Jenkinsfile`. ¿Puedes unirlos sin romper ninguno?» | Leyó los dos diseños, escribió un pipeline declarativo que conserva ambos y validó la sintaxis de Groovy en un contenedor. | Se pidió probarlo en Jenkins: se ejecutó con `BUILD_ALL` y recorrió todas las etapas de calidad. |
| 7 | «El pipeline con `BUILD_ALL` falla. ¿Es el código o es el entorno?» | Leyó los reportes JUnit y separó cuatro causas: `jwt.secret` ausente en el yml de prueba, una prueba que exigía PostgreSQL, un rol simulado sin `ROLE_` y Testcontainers 1.19 incompatible con Docker 29. Corrigió y probó dentro del contenedor de Jenkins. | Rodolfo llegó al mismo diagnóstico en HU-10, así que el PR se redujo al arreglo de Docker 29. Dos pruebas de Neo4j se pausaron con `@Disabled` y quedan para HU-14. |
| 8 | «¿Por qué dices que mi disco es lento?» | Midió en vez de suponer: 300 escrituras con `fsync` tardaron 59 s en un disco mecánico de 5400 rpm, y relacionó eso con los timeouts de PostgreSQL y Neo4j. | Pablo decidió no mover Docker al NVMe antes de la entrega. |
| 9 | «¿Cómo vamos frente al enunciado?» | Cometió un error: tomó la fecha de un PDF preliminar (15 de noviembre). Al ver el correo del 28 de octubre se retractó y recalibró el alcance a 10 HU y 49 puntos. | El equipo aportó el correo y el documento «Pendientes por persona», que corrigieron la lectura del asistente. |
| 10 | «¿Necesitas algo de AWS?» | Con permiso, hizo solo lecturas: no halló recursos de CircleGuard en esa cuenta y alertó de que las credenciales de la máquina eran de la cuenta root, recomendando rotarlas. | El servidor de CI real no se usó: el firewall no estaba abierto y se prefirió no operar un recurso compartido sin coordinación. |
| 11 | «Redacta el informe mientras tú pruebas el pipeline.» | Lanzó un agente en paralelo que escribió el informe con evidencia graduada (verificado, registros del equipo, código integrado) y un guion de video. | El equipo revisó el informe, completó nombres y contribución individual, y lo integró por PR con aprobación. |
| 12 | «Lee el enunciado oficial y dime qué falta de la Parte 1.» | Contrastó el PDF del curso con el backlog del equipo y encontró que HU-11, 13, 14 y 16 sí son entregables de la Parte 1 (E1.5, E1.6, E1.8), aunque el equipo las había dejado fuera del alcance acordado. | El equipo decidió cerrarlas con un enfoque de producto mínimo viable. |
| 13 | «Cierra HU-13 y HU-14.» | Midió la cobertura real (auth 35 %, identity 50 %, form 44 %, promotion 25 %), escribió pruebas unitarias hasta superar 60 % de líneas, añadió la regla que hace fallar el build y tres pruebas de integración con Testcontainers (identity, form y Kafka). | Daniel aprobó los PR. Todo se ejecutó en local; las ejecuciones en Jenkins quedan declaradas como pendientes. |
| 14 | «El disco es lento, migra Docker al NVMe.» | Inspeccionó los discos, vio que el NVMe tenía una instalación de Windows con el sistema de archivos inconsistente (`ntfsresize` se negó a tocarlo) y se detuvo sin modificarlo. | Pablo eligió la alternativa sin riesgo: datos de los contenedores de prueba en memoria RAM. |
| 15 | «Notificaciones de fallo y rollback (HU-11).» | Implementó aviso por Discord y correo y un paso de rollback con parámetros; guardó el webhook solo como credencial de Jenkins y en un archivo local fuera del repositorio. | Pablo creó el webhook, que se probó con un mensaje de prueba. La ejecución dentro de Jenkins se declara en el informe según su resultado. |
| 16 | «Levanta el CI local y prueba las notificaciones.» | Descubrió que el trabajo multibranch solo veía tags y no ramas ni PR, y lo corrigió en `casc.yml`. | Se aplicó y se verificó contra el Jenkins local. |

**Errores del asistente y cómo se corrigieron** (parte del proceso, no se ocultan)

- Dio por buena una fecha de entrega equivocada (ver fila 9) y la corrigió con el correo del profesor.
- Un borrado de volúmenes de Docker con un `.env` vacío falló sin avisar y dejó contraseñas viejas; se repitió con el `.env` correcto.
- Canceló por error un build de `dev` al filtrar mal la cola de Jenkins; se relanzó.
- Anunció HU-11, 13, 14 y 16 como pendientes cuando no estaban en el alcance acordado; se corrigió al leer el documento del equipo.
- Abrió el PR de HU-14 apuntando a la rama de HU-13; al unirse HU-13 primero, HU-14 no llegó a `dev` y hubo que abrir un PR correctivo (#31).
- Al corregir el descubrimiento de ramas, Jenkins consultó todas las ramas viejas y agotó el límite anónimo de la API de GitHub; se canceló lo que no hacía falta.

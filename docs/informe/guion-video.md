# Guion de la demo y del video · Parte 1 (3 a 5 minutos)

**Objetivo:** mostrar lo que **funciona y se puede comprobar** y ser claros con lo que falta. Se ensaya una vez y se graba en una sola toma. Fecha límite de entrega: domingo 8 de noviembre de 2026, 11:59 p. m.

## Antes de grabar (preparación, unos 20 minutos)

Los builds de Jenkins tardan, así que se lanzan **antes** de empezar a grabar.

1. Levantar el stack local: `bash infra/bootstrap/bootstrap-ci.sh --local <ci.env>`. En Nexus nuevo termina con código 3 hasta aceptar la licencia de Community Edition en `http://127.0.0.1:8081`; para esta demo no hace falta el registro Docker.
2. Conectar SonarQube con Jenkins: `bash scripts/conectar-sonar-jenkins.sh <ci.env>`.
3. Escanear el repositorio (job `circleguard`) y lanzar un build con `BUILD_ALL=true` sobre una rama de `dev`. Con el disco mecánico tarda de 5 a 25 minutos; dejarlo terminar.
4. Tener abiertas estas pestañas: repositorio en GitHub, lista de PR cerrados, ajustes de ramas, tablero de Jira, Jenkins (job y consola del build), Nexus y la terminal.

## Guion

| Tiempo | Qué se ve | Qué se dice | Dato real que se muestra |
| --- | --- | --- | --- |
| 0:00–0:30 | Repositorio y tablero de Jira | Qué es CircleGuard, equipo y roles A a D. | Tablero con las 12 HU del Sprint 0 y 1 en *Done*. |
| 0:30–1:15 | PR cerrados y ajustes de ramas de `main` | Flujo `hu-XX -> dev -> main`, un PR por HU con responsable de Jira y aprobador distinto, commitlint y ramas protegidas. | Un PR con commitlint en verde y su aprobación (por ejemplo #11 de HU-04 o #13 de HU-12). |
| 1:15–2:00 | Terminal: bootstrap del CI | Un solo script levanta Jenkins, SonarQube y Nexus y se puede repetir sin duplicar. | Dos ejecuciones: mismos 4 contenedores, volúmenes y redes. Gate `CircleGuard` con cobertura nueva ≥ 60 %. |
| 2:00–2:40 | Terminal y Nexus | Ocho servicios y la web empaquetados, sin privilegios y con healthcheck. | `package-images.sh vX.Y.Z --push` y las 9 imágenes en el repositorio `circleguard`. |
| 2:40–3:40 | Jenkins: job multibranch y consola del build | Escaneo de ramas, detección de módulos modificados y `BUILD_ALL`; etapas de calidad con JUnit y reportes JaCoCo. | Build con `BUILD_ALL`: los 8 servicios se prueban y se publican los reportes. Declarar que el análisis de SonarQube no se alcanzó por 2 pruebas pausadas de `promotion`. |
| 3:40–4:30 | Tabla de estado del informe | Alcance acordado de 10 HU: qué está verificado, qué tiene código sin ejecución (HU-03, 05, 07, 09, 10, 12, 15) y por qué. | Columna *Evidencia* del informe. HU-11, 13, 14 y 16 quedaron fuera del alcance. |
| 4:30–5:00 | Pantalla de cierre | Siguientes pasos: check de Jenkins en `main`, despliegue y promoción en AWS, gate de SonarQube, pruebas de Neo4j. Declaración de uso de IA y bitácora. | Anexo de IA del informe. |

## Qué NO se muestra (no se ejecutó)

- Despliegue y promoción a stage y prod (HU-10): el código está integrado pero requiere un tag de release y las VM de AWS.
- El análisis de SonarQube bloqueando por cobertura menor a 60 %.
- El modo `--aws` del bootstrap y el check de Jenkins en `main`.

## Plan B si algo falla en vivo

- Decirlo en voz alta y mostrar la evidencia ya generada (consola del build, capturas del informe). No esconder el fallo.
- Si Jenkins no alcanza a levantar a tiempo, mostrar la consola de un build anterior y los reportes JaCoCo ya publicados.

## Reglas

- No mostrar contraseñas, tokens, llaves ni archivos `.env`.
- No presentar como probado nada que no se haya ejecutado.

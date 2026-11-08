# Guion del video · Parte 1 (3 a 5 minutos)

Objetivo: mostrar lo que **funciona y se puede comprobar**, y ser claros con lo que falta. Ensayar una vez y grabar en una sola toma.

| Tiempo | Qué se ve | Qué se dice |
| --- | --- | --- |
| 0:00–0:30 | Repositorio en GitHub y el tablero de Jira | Qué es CircleGuard y qué cubre la Parte 1. Equipo y roles. |
| 0:30–1:15 | Pestaña Pull requests (cerrados) y ajustes de ramas de `main` | Flujo `hu-XX -> dev -> main`, un PR por HU con responsable y aprobador distinto, commitlint, `main` y `dev` protegidas. Mostrar un PR con su check verde y su aprobación. |
| 1:15–2:15 | Terminal: `bootstrap-ci.sh --local` y Jenkins, SonarQube y Nexus arriba; el quality gate `CircleGuard` | Un solo script levanta el stack, se puede repetir sin duplicar, el gate exige 60 % de cobertura de código nuevo. |
| 2:15–3:00 | Terminal: `package-images.sh`; Nexus con las 9 imágenes | Imágenes de los 8 servicios y la web, sin privilegios y con healthcheck. |
| 3:00–3:45 | Jenkins: job multibranch y consola de un build | Disparo por push, detección de módulos modificados y quality gate. **Solo mostrar lo que se haya ejecutado de verdad.** `[COMPLETAR: confirmar qué build se muestra]` |
| 3:45–4:30 | Tabla de estado de HU del informe | Qué está verificado, qué tiene código sin ejecución y qué no se implementó (HU-10, 11, 13, 14). Por qué. |
| 4:30–5:00 | Pantalla de cierre | Siguientes pasos: check de Jenkins en `main`, despliegue y promoción, cobertura y pruebas de integración. Declaración de uso de IA. |

Recomendaciones:
- No mostrar contraseñas, tokens ni archivos `.env`.
- No mostrar nada que no se haya probado; si algo falla en vivo, decirlo.
- Si el pipeline unificado no se ejecutó en Jenkins, no presentarlo como probado.

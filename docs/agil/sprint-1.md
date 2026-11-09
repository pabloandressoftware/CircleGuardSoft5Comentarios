# Sprint 1

## Planeación

**Objetivo:** entregar la Parte 1: CI/CD funcional, imágenes publicadas, pruebas E2E y
ambientes en AWS. Entrega el **8 de noviembre de 2026 a las 23:59 (Colombia)**.

**Alcance comprometido:** 14 historias, 68 puntos.

| HU | Historia | Responsable | Pts | Estado en Jira |
| --- | --- | --- | --- | --- |
| HU-03 (KAN-15) | Automatizar SemVer y notas de entrega | Daniel Martinez | 3 | En curso |
| HU-04 (KAN-16) | Aprovisionar Jenkins, SonarQube y Nexus | pablo andres guzman | 8 | Finalizada |
| HU-05 (KAN-17) | Aislar los ambientes dev, stage y prod | Daniel Martinez | 5 | En curso |
| HU-06 (KAN-18) | Empaquetar ocho servicios Java y el cliente web | pablo andres guzman | 5 | Finalizada |
| HU-07 (KAN-19) | Externalizar configuración y separar los Compose | NIGORI YT | 5 | Por hacer |
| HU-08 (KAN-20) | Disparar Jenkins por push y detectar módulos afectados | Rodolfo Moreno | 5 | Finalizada |
| HU-09 (KAN-21) | Ejecutar pruebas y bloquear builds con SonarQube | NIGORI YT | 5 | Por hacer |
| HU-10 (KAN-22) | Desplegar en dev y promover a stage y prod | Rodolfo Moreno | 5 | En curso |
| HU-11 (KAN-23) | Notificar fallos y demostrar un rollback | Sin asignar | 3 | Por hacer |
| HU-12 (KAN-24) | Reparar pruebas base y validación del QR | NIGORI YT | 3 | Por hacer |
| HU-13 (KAN-25) | Exigir 60 % de cobertura en cinco servicios | Sin asignar | 8 | Por hacer |
| HU-14 (KAN-26) | Verificar tres integraciones con Testcontainers | Sin asignar | 5 | Por hacer |
| HU-15 (KAN-27) | Automatizar tres flujos E2E con Playwright | Daniel Martinez | 5 | En curso |
| HU-16 (KAN-28) | Documentar operación y evidencias de la Parte 1 | Sin asignar | 3 | Por hacer |

**Fechas del sprint:** 2 al 8 de noviembre de 2026.

## Review (estado según Jira)

- **Finalizadas:** HU-04, HU-06, HU-08.
- **En curso:** HU-03, HU-05, HU-10, HU-15.
- **Por hacer:** HU-07, HU-09, HU-11, HU-12, HU-13, HU-14, HU-16.
- Puntos finalizados: 18 de 68.

**Evidencia de lo entregado**

- **HU-04:** el bootstrap levanta Jenkins, SonarQube y Nexus, es idempotente y el quality
  gate exige 60 % de cobertura de código nuevo (ver `docs/sprint-1-persona-b.md`).
- **HU-06:** las nueve imágenes (ocho servicios y la web) se construyen, corren sin
  privilegios, tienen healthcheck y se publican en Nexus.
- **HU-08:** multibranch con shared library y build selectivo (ver `docs/sprint-1-persona-c.md`).

**Demo:** [COMPLETAR por el equipo: fecha y qué se mostró]

## Retrospectiva

> Borrador redactado con hechos verificables del repositorio y de Jira. El equipo lo confirma o ajusta en la retrospectiva.

Hechos del sprint que conviene discutir:

| Qué pasó | Impacto | Acción acordada |
| --- | --- | --- |
| El mirror de plugins de Jenkins estuvo caído y el bootstrap se colgó 17 minutos | Retraso en la verificación de HU-04 | Se fijó `JENKINS_UC_DOWNLOAD` en el Dockerfile |
| HU-08 y HU-09 modifican el mismo `Jenkinsfile` | Conflicto al mergear la segunda | Coordinar el orden de merge entre C y D |
| El check de Jenkins exigido en `main` aún no reporta en el repositorio nuevo | `main` no admite merges | Publicar Jenkins con URL pública, crear el token y el webhook |
| HU-04 y HU-06 bloqueaban a HU-08 y HU-09 | Trabajo en cadena | Terminar primero las HU de las que otras dependen |

**Qué hicimos bien:**

- Cada HU tiene su rama y entra a `dev` por un PR aprobado por otra persona, con commits Conventional Commits validados por commitlint.
- El stack de CI (Jenkins, SonarQube y Nexus) se levanta con un solo script y es idempotente.
- Las nueve imágenes se construyen reproducibles, sin privilegios y con healthcheck.
- El pipeline unificado de HU-08 y HU-09 se ejecutó en un Jenkins local de pruebas: detecta módulos, corre pruebas y publica los reportes JUnit y JaCoCo.

**Qué mejorar:**

- Las HU se integraron al final y en cadena (HU-05 → HU-04 → HU-08 → HU-09); conviene abrir PR pequeños y temprano.
- Varios PR se enviaron a la rama equivocada (`main` en vez de `dev`).
- Las pruebas de backend fallan en Jenkins: `form-service` (1), `identity-service` (4) y `promotion-service` (5, incluidos errores de inicialización de Testcontainers). Por eso el quality gate no llega a SonarQube.
- HU-10 (despliegue y promoción) quedó sin implementar.

**Acciones para el Sprint 2:**

- Arreglar las pruebas de backend y habilitar Testcontainers en el agente de Jenkins (HU-12, HU-13 y HU-14).
- Publicar Jenkins con URL accesible, token de GitHub y webhook para que el check exigido en `main` se reporte.
- Implementar HU-10 y HU-11 sobre el pipeline unificado.
- Abrir los PR de cada HU al día, no al cierre del sprint.

# HU-22 (KAN-34) · Historia eliminada del tablero

**Estado:** eliminada del tablero Jira KAN por razones externas al equipo.
**Fecha de constatación:** 23 y 24 de noviembre de 2026.
**Efecto en el Sprint 2:** no se cuenta en el alcance comprometido ni en los puntos
completados del sprint.

## Qué se comprobó

| Comprobación | Resultado |
| --- | --- |
| `GET /rest/api/3/issue/KAN-34` en `seraph.atlassian.net` (23-nov-2026) | HTTP 404 |
| Misma consulta en el cierre (24-nov-2026) | HTTP 404 |
| Búsqueda `project = KAN` (24-nov-2026) | 56 issues; la secuencia salta de KAN-33 (HU-21) a KAN-35 (HU-23) |
| Respaldo local del tablero (`.local-evidence/jira/kan-issues.json`, 23-nov-2026) | No contiene KAN-34 |

Un 404 de Jira significa que la issue no existe o que la cuenta no tiene permiso
para verla; con la información disponible no se pueden distinguir esos dos casos. El
equipo confirma que la historia se eliminó del tablero por razones ajenas al equipo.

## Qué no se afirma

- **No se reconstruyen sus criterios de aceptación.** El texto original de KAN-34 no
  está disponible, así que este documento no inventa criterios ni los da por cumplidos.
- **No se declara verificada.** Ninguna evidencia de este repositorio se presenta como
  cierre de KAN-34.
- **No se suma a la velocidad.** Sus puntos no aparecen en el burndown ni en el review.

## Trabajo del repositorio que lleva la etiqueta HU-22

Antes de la eliminación, el equipo implementó y etiquetó como HU-22 un trabajo de
patrones de resiliencia. Sigue en el código, tiene pruebas y forma parte del despliegue
`v1.1.2`. Se documenta como **trabajo técnico**, no como el cierre de una historia de Jira:

| Pieza | Dónde | Prueba |
| --- | --- | --- |
| Retry + Circuit Breaker (Resilience4j) de auth hacia identity | `services/circleguard-auth-service/.../client/IdentityClient.java`, `resilience.yml` | `IdentityClientResilienceTest`, `LoginControllerTest` |
| Retry + Circuit Breaker (Resilience4j) de dashboard hacia promotion | `services/circleguard-dashboard-service/.../client/PromotionClient.java`, `resilience.yml` | `PromotionClientResilienceTest` |
| Catálogo de patrones | [docs/patrones/README.md](../patrones/README.md), [retry-y-circuit-breaker.md](../patrones/retry-y-circuit-breaker.md) | — |

El patrón adicional exigido por HU-23 (KAN-35, Done) se presenta con su propia evidencia en
[docs/patrones/](../patrones/). Si el docente pregunta por HU-22, la respuesta es:
*"la historia ya no existe en el tablero; el código de resiliencia que llevaba su
etiqueta está desplegado y probado, pero no la damos por cerrada porque no podemos
contrastarlo con sus criterios originales"*.

## Si la historia reaparece

1. Leer sus criterios en Jira y compararlos con las pruebas listadas arriba.
2. Registrar en la issue la evidencia que corresponda o las brechas.
3. Actualizar este documento, [sprint-2.md](sprint-2.md) y el burndown.

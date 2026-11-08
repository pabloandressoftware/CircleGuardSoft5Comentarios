# Marco de trabajo ágil (HU-01)

## Marco elegido: Scrum con tablero Kanban en Jira

El proyecto se organiza en **sprints** (Sprint 0, 1 y 2) con un backlog priorizado de
**12 épicas y 29 historias de usuario (144 puntos)**, y se sigue en un **tablero Kanban**
de Jira (proyecto KAN) con las columnas *Tareas por hacer → En curso → Finalizada*.

### Por qué Scrum y no solo Kanban

- Hay **fechas de entrega fijas** (Parte 1 el 8 de noviembre de 2026 a las 23:59, hora de
  Colombia), y los sprints obligan a recortar el alcance a lo que cabe en cada iteración.
- El backlog ya viene estimado en puntos y asignado por iteración, que es lo que Scrum pide
  para planear y medir lo comprometido contra lo entregado.
- Cada sprint cierra con **review** (qué se entregó y se demuestra) y **retrospectiva**
  (qué mejorar), que es lo que el curso pide documentar.

### Por qué un tablero Kanban dentro de Scrum

- Se ve el flujo de cada HU a simple vista y se detectan cuellos de botella (por ejemplo,
  HU-04 y HU-06 bloqueaban a HU-08 y HU-09).
- Las subtareas permiten repartir una HU entre varias personas sin perder el seguimiento.

## Roles

| Rol | Responsabilidad | Quién |
| --- | --- | --- |
| A · Gestión ágil, versiones y E2E | Tablero, SemVer, pruebas E2E | Daniel Martinez (`damaral2005`) |
| B · Infraestructura | Jenkins, SonarQube, Nexus, imágenes Docker | Pablo Guzmán (`pabloandressoftware`) |
| C · Pipeline y despliegue | Multibranch, despliegue y rollback | Rodolfo Moreno (`Matatrolls`) |
| D · Configuración y calidad | Variables, quality gate, pruebas base | NIGORI YT (`itsnigori98`) |

> Los roles y su asignación se confirmaron en Jira. Corregir aquí si el equipo los cambia.

## Tablero y trazabilidad

- Épica → Historia (HU-xx) → Subtarea (HU-xx.y). Cada HU lleva criterios de aceptación,
  puntos, responsable e iteración en su descripción.
- **Una rama por HU** (`hu-02` … `hu-15`), con commits Conventional Commits que citan la HU.
- Cada PR enlaza su HU de Jira (KAN-xx) mediante la plantilla de PR.

## Estrategia de ramas

```
hu-XX  ->  dev  ->  main
```

- Cada HU tiene su rama, que sale de `main` o de la HU de la que depende
  (`hu-04` parte de `hu-05`, `hu-08` parte de `hu-04`).
- Los cambios entran a `dev` por Pull Request y `dev` pasa a `main` por Pull Request.
- `main` y `dev` están protegidas: sin push directo, 1 aprobación y checks obligatorios.
- Detalle de reglas, plantillas y aprobadores: [`docs/gobernanza/cambios.md`](../gobernanza/cambios.md).

## Definición de terminado (DoD)

Una HU está **Finalizada** cuando:

1. Cumple todos sus criterios de aceptación y hay evidencia de ello (salida de comandos,
   capturas o comentario en Jira).
2. Su código está en `dev` mediante un PR aprobado por otra persona.
3. Los commits pasan commitlint y los checks de CI exigidos.
4. La documentación afectada está actualizada.

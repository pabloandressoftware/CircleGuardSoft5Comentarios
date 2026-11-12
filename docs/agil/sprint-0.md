# Sprint 0

## Planeación

**Objetivo:** dejar listas las bases para trabajar en equipo: tablero ágil y reglas del
repositorio.

**Alcance comprometido** (6 puntos):

| HU | Historia | Responsable | Pts | Estado en Jira |
| --- | --- | --- | --- | --- |
| HU-01 (KAN-13) | Configurar el tablero ágil y documentar los sprints | Rodolfo Moreno | 3 | Por hacer |
| HU-02 (KAN-14) | Proteger main y formalizar PR y cambios | pablo andres guzman | 3 | Finalizada |

**Preparación (sin HU propia):** creación del repositorio desde la plantilla del curso,
definición del backlog de 12 épicas y 29 HU en Jira, y reparto de roles A a D.

**Fechas del sprint:** 28 de octubre al 1 de noviembre de 2026.

## Review

- **HU-02:** ramas `main` y `dev` protegidas, commitlint y plantillas de PR y de cambio
  (ver `docs/gobernanza/cambios.md`). Entregada y finalizada en Jira.
- **HU-01:** el tablero KAN existe con las 12 épicas y las 29 HU. Con estos documentos
  queda documentado el marco y los sprints.
- **Pendiente:** confirmar que el docente tiene acceso al tablero de Jira.

## Retrospectiva

> Borrador redactado con hechos verificables del repositorio y de Jira. El equipo lo confirma o ajusta en la retrospectiva.

Hechos del sprint que conviene discutir:

| Qué pasó | Impacto | Acción acordada |
| --- | --- | --- |
| Las reglas de ramas no se pueden configurar en la organización `ingesoft5` (plan GitHub Team) | HU-02 no era viable en ese repositorio | Mover el repositorio a la cuenta del líder de desarrollo, donde son gratuitas |
| Algunos PR se enviaron a `main` en lugar de `dev` | PR cerrado y trabajo repetido | La protección de `main` y la plantilla de PR lo evitan |

**Qué hicimos bien:**

- Se construyó el backlog completo antes de programar: 12 épicas, 29 HU y 144 puntos, con criterios de aceptación, puntos, responsable e iteración.
- Se repartieron los roles A a D y quedó claro quién responde por cada HU.

**Qué mejorar:**

- Se descubrió tarde que las reglas de ramas no están disponibles en la organización del curso (plan gratuito).
- Falta confirmar que el docente tiene acceso al tablero de Jira.

**Acciones para el siguiente sprint:**

- Mover el repositorio a la cuenta del líder de desarrollo y activar ahí la protección de ramas (hecho).
- Confirmar el acceso del docente al tablero KAN.

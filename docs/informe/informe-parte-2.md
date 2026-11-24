# Informe · Parte 2 (Taller 3) · CircleGuard

Corte: 24 de noviembre de 2026. Sustentación: 25 de noviembre de 2026. Repositorio:
`pabloandressoftware/CircleGuardSoft5Comentarios` (`main`). Tablero: Jira KAN
(`seraph.atlassian.net`).

## 1. Resumen

La Parte 2 lleva CircleGuard de Docker Compose a **Kubernetes en AWS** con
infraestructura como código, seguridad en el pipeline, observabilidad y rollback.
La release **v1.1.2** está desplegada en dev, stage y prod por el pipeline oficial,
con HTTPS público, 9/9 servicios sanos por ambiente, E2E 5/5, Trivy sin CRITICAL,
ZAP sin fallos y aprobación manual de prod. Se integraron y desplegaron 54 de 62
puntos comprometidos; el resto es esta documentación (HU-28) y la sustentación
(HU-29). Las brechas se listan en la sección 7.

Resumen en inglés de una página: [executive-summary.md](../executive-summary.md).

## 2. Marco ágil

Scrum con sprints cortos sobre Jira KAN, una rama y un PR por HU, aprobación de otra
persona, Conventional Commits validados por commitlint y ramas protegidas
([marco.md](../agil/marco.md), [cambios.md](../gobernanza/cambios.md)).
Planeación, review, burndown y retrospectiva del Sprint 2:
[sprint-2.md](../agil/sprint-2.md). HU-22 (KAN-34) fue eliminada del tablero por
razones externas: [hu-22-eliminada.md](../agil/hu-22-eliminada.md).

## 3. Arquitectura e infraestructura

- **Aplicación, infraestructura y pipeline:** [arquitectura.md](../arquitectura.md).
- **Terraform:** [hu-17-terraform.md](../hu-17-terraform.md) y [contrato](../hu-17-terraform-contrato.md).
- **Kubernetes y Helm:** [kubernetes.md](../kubernetes.md).
- **Patrones:** [patrones/](../patrones/README.md).

| Capa | Decisión | Por qué |
| --- | --- | --- |
| Cómputo | k3s en 2 VMs m7i-flex.large por ambiente | EKS cobra el plano de control y exigiría NAT/LB; el plan FREE no admite t3.xlarge |
| Red | VPC por ambiente, EIP fija, SSH/API por CIDR | Aislamiento entre dev, stage y prod |
| DNS/TLS | sslip.io + cert-manager + Let's Encrypt | Sin dominio propio; certificados públicos reales |
| Estado | S3 versionado + DynamoDB, un estado por ambiente | Un error en un ambiente no toca a los demás |
| Credenciales de CI | Rol IAM asumido, sin access keys | Nada que filtrar ni rotar |
| Protección | `check-plan.mjs` + `prevent_destroy` | El pipeline nunca destruye ni reemplaza |

## 4. Pipeline CI/CD

Descrito en [hu-19-pipeline.md](../hu-19-pipeline.md) y en el
[manual de operaciones](../manual-operaciones.md#4-release-y-despliegue). Un tag
`vX.Y.Z` construye 9 imágenes una sola vez, las escanea, las publica y las promueve
de dev a stage y a prod con humo en cada paso, E2E en dev, ZAP en stage y aprobación
de `aprobador` para prod. El rollback reutiliza revisiones Helm.

## 5. Estado de las historias

| HU | Pts | Jira | Evidencia | Valoración |
| --- | --- | --- | --- | --- |
| HU-17 Terraform | 8 | Done | Plan sin cambios en los 3 ambientes | Cumple |
| HU-18 Kubernetes/Helm | 8 | Done | 9/9 releases sanos en los 3 ambientes | Cumple |
| HU-19 Pipeline Terraform + Helm | 5 | Done | Build `v1.1.2` #2 | Cumple |
| HU-20 Vulnerabilidades y secretos | 5 | Done | Trivy 0 CRITICAL, VEX acotado, gitleaks | Cumple |
| HU-21 RBAC, TLS, autenticación | 5 | Done | TLS público, E2E 5/5, clave interna | Cumple |
| HU-23 Feature toggle + patrón | 5 | Done | Parámetros en BD; sin ConfigMap ni Bulkhead | **Parcial** |
| HU-24 Métricas y alertas | 5 | Done | FIRING/RESOLVED en dev y stage | Cumple; Discord y métricas de 2 servicios pendientes |
| HU-25 Trazas y logs | 5 | Done | Agente OTel en el chart | **Parcial** en AWS (sin Jaeger/Loki) |
| HU-26 Locust y ZAP | 5 | Done | ZAP stage 0 FAIL; Locust local | Cumple; Locust sin repetir en AWS |
| HU-27 Versiones y rollback | 3 | Done | Rollback real en dev | Cumple |
| HU-28 Cierre y documentación | 5 | In Progress | Este informe y sus anexos | En curso |
| HU-29 Sustentación | 3 | In Progress | 25-nov | En curso |

Detalle de cada prueba: [evidencias/sprint-2.md](../evidencias/sprint-2.md).

## 6. Problemas encontrados y cómo se resolvieron

| Problema | Detección | Solución |
| --- | --- | --- |
| v1.1.0 salió con dependencias vulnerables | Trivy bloqueó la publicación | Estabilización (#55) y releases 1.1.1/1.1.2; v1.1.0 no se movió ni se reutilizó |
| Imagen web: conflicto NGINX/Alpine | Build del lote de imágenes | NGINX 1.30.7 (#57) |
| Terraform del pipeline y observabilidad por clúster | Builds de `main` | #58 |
| Terraform quería reemplazar el worker detenido | `check-plan.mjs` bloqueó el apply | `ignore_changes` (#59) |
| Paso Helm con argumentos con nombre: `NoSuchMethodError` | v1.1.1 #2 | Sobrecarga `call(Map, String)` (#59) |
| Login válido → 503 (cabecera interna perdida) | E2E del ensayo en dev | Se restauró con prueba que falla sin ella (#59) |
| Primer login tras desplegar cortado a 250 ms | E2E 3/5 | Timeouts de identity en Helm (#59) |
| El cuestionario E2E no tenía token | E2E 3/5 | Semilla con el token de la cuenta de pruebas (#59) |
| Clave de Neo4j en el log de Jenkins | Revisión del log | Rotación en stage/prod y #61 |
| Script de alertas abortaba y luego daba un FIRING falso | Builds de observabilidad #5 y #6 | #63 (variables y filtro por etiquetas) |
| auth y dashboard sin `/actuator/prometheus` | Targets caídos en Prometheus | #64 con prueba de regresión |

## 7. Limitaciones y trabajo pendiente

- **Notificaciones:** no hay webhook de Discord y el correo del pipeline falla (SMTP `530 Authentication Required`). Las alertas quedan en Alertmanager. El
  procedimiento para activar ambos sin exponer secretos está en el manual.
- **PR #64** (integrado) necesita la release 1.1.4 (PR #65) para que Prometheus vea auth y dashboard. El tag `v1.1.3` existe (incluye #61) pero no se construyó ni se desplegó. Hasta
  entonces `CircleguardServiceDown` de esos targets y `AvailabilityLow` aparecen activas.
- **HU-23:** falta el toggle en ConfigMap con prueba en caliente, que el flag
  `unconfirmed-fencing` tenga efecto y un Bulkhead o rate limit.
- **HU-25 en AWS:** Jaeger y Loki no están instalados.
- **ZAP:** 12 avisos de cabeceras (HSTS, CSP, anti-clickjacking…).
- **Locust:** solo medido en local; `/surveys` no cumple la meta allí.
- **Trivy fs:** falso positivo con `node_modules` anidados en workspaces reutilizados.
- **Rollback:** solo dev tiene dos releases en su historial; v1.1.1 conserva el
  defecto de login que corrigió v1.1.2.
- **Jira:** subtareas del Sprint 1 en *To Do* y estados del Sprint 2 actualizados en lote.

## 8. Operación, costos y cierre

- **Operación:** [manual-operaciones.md](../manual-operaciones.md) (CI, k3s, Helm,
  DNS/TLS, observabilidad, alertas, rollback, seguridad y retiro).
- **Costos:** [costos.md](../costos.md). Plan FREE con USD 160 de créditos y
  presupuesto de USD 25. Consumo estimado ≈ USD 8,8 al 24-nov y ≈ USD 23 al cierre
  si stage/prod se encienden solo para pruebas.
- **Cierre:** 25-nov 23:59 Colombia, con destroy revisado prod → stage → dev → CI → backend.

## 9. Guion de la sustentación

Presentación: [presentacion/sustentacion-parte-2.pdf](../presentacion/sustentacion-parte-2.pdf).

1. **Arquitectura** (2 min): diapositivas 3–4.
2. **Terraform** (3 min): estructura de `infra/terraform`, estado remoto y `check-plan.mjs`.
   Mostrar en Jenkins un *Terraform apply* con `0 added, 0 changed, 0 destroyed`.
3. **Pipeline** (4 min): build `v1.1.2` #2, la vista de etapas, `approval-prod.txt`,
   el informe de ZAP y la tabla *Versiones por ambiente*.
4. **Aplicación** (2 min): abrir https://prod.13-59-35-181.sslip.io y mostrar el
   certificado. Si prod está detenido, usar dev.
5. **Observabilidad** (3 min): `port-forward` a Grafana y a Alertmanager; mostrar el
   registro de `circleguard-observability` #7 con FIRING/RESOLVED.
6. **Rollback** (2 min): `v1.1.2` #3 y #4, `rollback.json` con la duración.
7. **Costos y cierre** (1 min).
8. **Retrospectiva y brechas** (2 min): decirlas antes de que pregunten.

Antes de empezar:

- Encender prod (y stage si se va a mostrar) 20 minutos antes.
- Comprobar `kubectl get nodes` y el humo.
- Iniciar sesión en Jenkins con el usuario operador, no con `aprobador`.

## 10. Contribución individual

Responsables por HU según Jira (Sprint 2):

| Integrante | HU del Sprint 2 |
| --- | --- |
| Daniel Martínez Alvarado (`damaral2005`) | HU-17, HU-27, HU-28 |
| Pablo Andrés Guzmán Alarcón (`pabloandressoftware`) | HU-18, HU-20, HU-26 |
| Rodolfo Moreno Gutiérrez | HU-19, HU-21, HU-24 |
| Nicolás Góngora Rincón (`itsnigori98`) | HU-23, HU-25 |

Commits en `main` desde el 9-nov (`git shortlog -sn --since=2026-11-09 origin/main`):
damaral2005 37, Seraph 31, pabloandressoftware 30, Pablo Andrés Guzmán Alarcón 5,
itsnigori98 5, github-actions[bot] 4. El equipo completa la correspondencia entre las
cuentas de Git y los integrantes donde no es evidente.

## 11. Declaración de uso de IA generativa

Siguiendo la regla del curso (nivel 3: colaboración permitida con declaración), el
equipo usó **Claude Code** (Anthropic) durante la estabilización y el cierre para:

- diagnosticar fallos del pipeline;
- escribir correcciones con sus pruebas (#59, #61, #63, #64);
- operar Jenkins y los clústeres con credenciales que nunca se mostraron en el chat;
- rotar la clave de Neo4j expuesta;
- ejecutar las pruebas de alertas y rollback;
- redactar esta documentación y la presentación.

Toda corrección entró por PR con aprobación de otra persona. La aprobación de prod
la dio el integrante con el usuario `aprobador`; la IA no la ejecutó. Lo que no se
completó se declara como tal.

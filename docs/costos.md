# Costos de AWS: estimados, reales y ventana de cierre

Consulta del 24 de noviembre de 2026, ~19:40 UTC (14:40 Colombia). Proyecto
`001784022331`, región seleccionada `us-east-2` (Ohio), perfil `circleguard`.
Montos en USD.

## 1. Situación de la cuenta

| Dato | Valor | Fuente |
| --- | --- | --- |
| Plan | **FREE**, activo, vence el 23-may-2027 | `aws freetier get-account-plan-state` |
| Créditos disponibles | **160,00** (100 iniciales + 60 por tres actividades completadas) | mismo comando y `list-account-activities` |
| Presupuesto | `CircleGuard-demo-2026-11`, límite **25,00**, avisos al 50/80/100 % | `aws budgets describe-budgets` |
| Gasto real registrado | **0,00** (Budgets `ActualSpend` y Cost Explorer diario) | `aws ce get-cost-and-usage` |

**Cómo leer el "real 0,00":** con el plan FREE el uso se descuenta de los créditos
y AWS publica los datos de facturación con hasta 24 h de retraso. El 0,00 significa
que todavía no se ha reflejado ningún cargo, **no** que la infraestructura sea gratuita.
Para planear se usa la estimación de la sección 3. No se cambió al plan de pago: con
el plan FREE no hay cargo a tarjeta mientras queden créditos.

## 2. Inventario facturable (24-nov-2026)

| Ambiente | Instancias | Disco gp3 | IPv4 públicas | Creado (UTC) |
| --- | --- | --- | --- | --- |
| CI (Jenkins, Nexus, SonarQube, Caddy) | 1 × m7i-flex.large | 100 GiB | 1 (EIP) | 24-nov 00:34 |
| dev (k3s, 2 nodos) | 2 × m7i-flex.large | 120 GiB | 2 (EIP + worker) | 24-nov 00:21–00:52 |
| stage (k3s, 2 nodos) | 2 × m7i-flex.large | 140 GiB | 2 (EIP + worker) | 24-nov 13:52 |
| prod (k3s, 2 nodos) | 2 × m7i-flex.large | 160 GiB | 2 (EIP + worker) | 24-nov 13:53 |
| Compartido | S3 `circleguard-tfstate-…` (estado), DynamoDB de locks, 3 grupos de CloudWatch Logs (0 bytes) | — | — | — |

No hay NAT Gateway ni balanceadores (el Ingress es Traefik dentro de k3s), que son
los recursos que más encarecen una cuenta pequeña.

Precios on-demand de Ohio (AWS Price List API, 24-nov-2026):

| Recurso | Precio |
| --- | --- |
| m7i-flex.large (2 vCPU, 8 GiB) Linux | 0,09576 / h |
| gp3 | 0,08 / GiB-mes (≈ 0,0001096 / GiB-h) |
| IPv4 pública, en uso o en reposo | 0,005 / h |

## 3. Costo por ambiente

| Ambiente | Encendido | Detenido | Encendido por día |
| --- | --- | --- | --- |
| CI | 0,1117 / h | — (no se detiene) | 2,68 |
| dev | 0,2147 / h | — (no se detiene) | 5,15 |
| stage | 0,2169 / h | 0,0203 / h (disco + EIP) | 5,20 |
| prod | 0,2191 / h | 0,0225 / h (disco + EIP) | 5,26 |
| **CI + dev** | **0,3264 / h** | | **7,83** |
| **Los cuatro** | **0,7623 / h** | | **18,30** |

Al detener stage/prod solo se paga el disco y la EIP; la IP pública del worker se
libera (por eso cambia en cada arranque). S3, DynamoDB y CloudWatch Logs suman
centavos al día con este volumen.

### Consumo estimado hasta la consulta

Cota superior: supone stage/prod encendidos desde su creación.

| Ambiente | Horas | Estimado |
| --- | --- | --- |
| CI | 19,1 | 2,13 |
| dev | 19,3 | 4,14 |
| stage | 5,8 | 1,26 |
| prod | 5,8 | 1,27 |
| **Total al 24-nov 19:40 UTC** | | **≈ 8,80** |

No incluye la instancia de CI anterior, reemplazada el 23-nov
([despliegue-aws-estabilizacion.md](despliegue-aws-estabilizacion.md)); fueron unas horas a la misma tarifa.

## 4. Ventana hasta el cierre: miércoles 25-nov 23:59 Colombia

El cierre es el **25-nov-2026 a las 23:59 America/Bogota = 26-nov-2026 04:59 UTC**.
Desde la consulta quedan unas **33 h**.

| Escenario | CI + dev (33 h) | stage + prod | Ventana | Acumulado estimado | vs. presupuesto 25 |
| --- | --- | --- | --- | --- | --- |
| **A (recomendado):** stage/prod encendidos 2 h más hoy y 3 h para la sustentación | 10,77 | 2,18 encendidos + 1,20 detenidos | **14,15** | **≈ 22,9** | 92 % |
| B: stage/prod detenidos desde ahora, la sustentación se hace con dev | 10,77 | 1,41 detenidos | 12,18 | ≈ 21,0 | 84 % |
| C: todo encendido hasta el cierre | 10,77 | 14,39 | 25,16 | ≈ 34,0 | **136 %** |

- El escenario C supera el presupuesto, aunque siguen sobrando créditos (160). El
  presupuesto es un aviso: **no apaga nada por sí solo**.
- En A, prod se enciende para la sustentación con `start-instances` y se valida con
  el humo antes de presentarlo. Tras cada arranque cambia la IP pública del worker;
  el Ingress usa la EIP del servidor, así que la URL de la aplicación no cambia.
- Si no se ejecuta el cierre, CI + dev + stage/prod detenidos cuestan **≈ 8,86 por
  día** y los créditos se agotarían en unos 15 días. Con el plan FREE, al agotarse
  los créditos o vencer el plan, AWS cierra la cuenta si no se actualiza a pago.

**Ejecutado:** stage y prod se detuvieron el 24-nov a las 20:40 UTC (15:40 Colombia), al terminar la release, el rollback y las alertas.

## 5. Plan de apagado y cierre

Detalle operativo en el [manual de operaciones](manual-operaciones.md#11-cierre-y-retiro-de-recursos).
Resumen del orden:

1. **Hoy, al terminar las pruebas:** detener stage y prod (4 instancias).
   ```bash
   aws ec2 stop-instances --region us-east-2 --profile circleguard \
     --instance-ids i-0831343cc07c01f08 i-0843ecd02295c7763 i-01b37806af3455916 i-0b07b0fc537c0e392
   ```
2. **Miércoles 25, antes de la sustentación:** encender prod (y stage si se va a
   mostrar la promoción), esperar los nodos `Ready` y correr el humo.
3. **Miércoles 25, después de las 23:59 (Colombia) y con la sustentación cerrada:**
   1. Guardar evidencia sin secretos (artefactos de Jenkins, informes, tablas).
   2. `terraform plan -destroy` en **prod → stage → dev**, revisarlo y aplicarlo.
      El control `check-plan.mjs` del pipeline bloquea bajas: el retiro es una
      operación administrativa con el perfil `circleguard-terraform`, no un build.
   3. CI tiene `prevent_destroy`: retirarlo en un commit explícito, revisar el plan,
      respaldar Jenkins/Nexus/Sonar si se quieren conservar y destruir.
   4. Destruir el backend (S3 versionado + DynamoDB) **al final**: el bucket tiene
      versiones antiguas del estado y no se vacía borrando solo los objetos actuales.
   5. Verificar que no quedan instancias, volúmenes EBS, EIP, grupos de logs ni
      buckets: `aws ec2 describe-instances|describe-volumes|describe-addresses`,
      `aws logs describe-log-groups` (también en `us-east-1`).
4. **No cerrar el proyecto AWS** en AWS Settings como sustituto de la limpieza.
   Cerrar el proyecto no es lo mismo que retirar recursos y no debe repetirse por
   accidente.

## 6. Comandos usados en esta estimación

```bash
aws freetier get-account-plan-state --region us-east-1
aws freetier list-account-activities --region us-east-1
aws budgets describe-budgets --account-id 001784022331 --region us-east-1
aws ce get-cost-and-usage --region us-east-1 --time-period Start=2026-11-01,End=2026-11-25 \
  --granularity DAILY --metrics UnblendedCost
aws pricing get-products --region us-east-1 --service-code AmazonEC2 \
  --filters Type=TERM_MATCH,Field=instanceType,Value=m7i-flex.large \
            Type=TERM_MATCH,Field=location,Value='US East (Ohio)' ...
aws ec2 describe-instances|describe-volumes|describe-addresses --region us-east-2
```

Todos con `--profile circleguard`; son consultas de solo lectura.

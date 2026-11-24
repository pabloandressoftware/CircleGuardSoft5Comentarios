# Despliegue de CircleGuard para la sustentacion

Estado comprobado el 23 de noviembre de 2026. Rama de trabajo:
`fix/release-1.1.0-estabilizacion`, base `69cba57`.
La aplicacion todavia no esta publicada: hay infraestructura y middleware,
pero falta aprobar las imagenes y ejecutar la release integrada en main.

## Recursos y accesos actuales

- Proyecto AWS `001784022331`, region seleccionada `us-east-2`, perfil `circleguard`.
- Plan FREE activo. La ultima consulta devolvio USD 160 de creditos.
- Presupuesto `CircleGuard-demo-2026-11`: USD 25, avisos al 50/80/100 % a
  `damaral2005@gmail.com`. Son avisos; no constituye un apagado automatico.
- Backend aplicado: bucket `circleguard-tfstate-001784022331-us-east-2`, cifrado
  SSE-S3, versionado, bloqueo publico y politica TLS. DynamoDB
  `circleguard-tf-locks`, pago por uso, cifrado y PITR.
- CI: `i-0aaea7211026e03d7`, EIP `18.220.203.182`.
- Dev: servidor `i-0713e7f73c304ae7a`, EIP `3.143.61.218`, y worker
  `i-02ed4119a539dbc42`. Ambos nodos k3s estan Ready.
- Stage y prod aun no se han creado. Se encenderan para las pruebas.

CI usa HTTPS publico con Caddy:

- https://jenkins.18-220-203-182.sslip.io/
- https://nexus.18-220-203-182.sslip.io/
- https://sonar.18-220-203-182.sslip.io/
- Registro: `registry.18-220-203-182.sslip.io`.

Los tres servidores actuales son `m7i-flex.large` (2 CPU/8 GiB). AWS rechazo
`t3.xlarge` en el plan FREE; la cuota de vCPU no garantiza elegibilidad del tipo.
La tarifa consultada por Price List es USD 0,09576/h por m7i-flex.large. CI y dev
con dos nodos, 220 GiB gp3 y tres IPv4 rondan USD 7,8/dia, antes de logs,
transferencia y solicitudes. Stage/prod no deben permanecer encendidos fuera
de las pruebas. No se ha cambiado al plan de pago.

## Infraestructura y credenciales

Los estados estan separados por `circleguard/{ci,dev,stage,prod}/terraform.tfstate`.
Los tres tfvars ya contienen proyecto, AMI Canonical, region, llave y registro
reales. SSH y el API externo estan limitados al operador y a CI; el trafico
6443/10250/VXLAN entre nodos se permite solo dentro de su security group.

Terraform local 1.9.8 usa el perfil `circleguard-terraform`, cuyo
credential_process exporta las credenciales temporales de `circleguard`.
No se crearon access keys de IAM. Jenkins usa `CircleGuardCiTerraformRole`.
Su politica se genero con IAM Policy Autopilot a partir de planes reales de
alta y eliminacion (el plan destroy NO se aplico), se acoto a los roles de
nodos, region, logs y estados del proyecto, y se comprobo en IAM Simulator.
Los nodos tienen un permissions boundary limitado a SSM y sus logs.
El rol de Jenkins no puede modificar su propio rol ni leer el estado de CI.

Los secretos locales estan en `.local-evidence/credentials/`, excluidos de Git:

- `ci.env`: operadores de CI, GitHub, Nexus, Sonar y webhook Discord.
- `dev.kubeconfig`: acceso de Jenkins al API dev.
- `circleguard-env-dev`: secretos propios de la aplicacion dev.
- `ssh/circleguard-demo-20261123`: llave privada de la demostracion.

No imprimir estos archivos. `infra/ci/jenkins/import-secret-files.ps1` carga los
archivos de un ambiente en Jenkins sin devolver su contenido. La licencia de
Nexus fue aceptada por el operador. El token GitHub ya fue validado con permiso
de escritura. El webhook Discord sigue pendiente de configuracion local.

## Problemas encontrados y corregidos

- El API interno de k3s anunciaba la EIP, lo que impedia comunicarse a los pods.
  El bootstrap ahora anuncia la IP privada y conserva la EIP para acceso externo.
- El proveedor AWS reporta `associate_public_ip_address=true` despues de asociar
  una EIP. Sin ignorar ese atributo, cada plan reemplazaba la maquina. Esto
  provoco un reemplazo no previsto de CI; se restauro desde la configuracion
  local. CI tiene `prevent_destroy` y el pipeline bloquea planes con bajas o
  reemplazos mediante `check-plan.mjs` antes de apply.
- La identidad del servidor k3s ahora persiste en EBS junto a los PVC. Se
  recupero el nombre original de dev (`ip-10-50-1-25`) y se restablecio su red.
  Los cinco middleware y los tres PVC se conservaron y volvieron a estar Ready.
- Se roto el cluster dev vacio y sus secretos despues de que un importador
  devolviera contenido codificado. El importador corregido termina con return
  null y solo confirma IDs; los accesos anteriores quedaron invalidados.
- Jenkins comparte la misma ruta de workspace con el host para los bind mounts
  de Docker, gitleaks y Trivy. Se agregaron callback y token de SonarQube.
- Registro publico unico para build, scan, push y pull, sin referencias loopback.
- cert-manager 1.21.3 con ACME/Traefik; SITE_ADDRESS configura host, CORS y TLS
  al desplegar. El humo valida confianza TLS; certificados locales requieren
  `CIRCLE_TLS_INSECURE=1` explicito.
- Se corrigio el Bash duplicado en el humo y se incorporo la evaluacion de
  dependencias descrita en `docs/seguridad-spring-demo.md`.

## Comprobaciones y pendientes de release

Ya pasaron las pruebas unitarias y JaCoCo con Spring Boot 3.5.16 y Tomcat
10.1.62; se corrigieron dos pruebas que asignaban un UUID autogenerado.
Pasaron las pruebas de releases/notas, las pruebas del control de planes y VEX,
y dos pruebas Terraform con proveedores simulados. Jenkins valido el Jenkinsfile.
Se comprobo por SQL el permiso CREATE en las cinco bases de dev.
El primer preflight de CI fue SUCCESS; se repetira tras su restauracion.

Queda por completar:

1. Corregir web: el job circleguard-handover #1 construyo ocho imagenes Java pero
   fallo por conflicto NGINX/Alpine en mobile/Dockerfile. No llego a los escaneos
   ni publico imagenes. Repetir el lote tras la correccion y conservar informes.
2. Unitarias, JaCoCo e integrationTest real pasaron en ese Jenkins. Quedan Sonar
   y el pipeline oficial del PR. Movil 13/13 y TypeScript pasaron localmente.
3. Integrar la instalacion de observabilidad en cada cluster, cargar el webhook
   Discord y la credencial de Grafana, y comprobar sus targets y notificaciones.
4. Crear stage/prod para preparar sus kubeconfigs y secretos independientes;
   detenerlos cuando no se esten verificando.
5. Revisar el PR 55, creado en borrador hacia main, pasar checks y fusionarlo.
   `v1.1.0` ya existe
   en `145f0a7` y NO contiene la estabilizacion; no moverlo ni recrearlo.
6. Usar Release Please para una nueva release patch (previsiblemente `v1.1.1`)
   con notas publicadas y version.txt consistente. El pipeline acepta `vX.Y.Z`.
7. Ejecutar despliegue dev, E2E, stage, ZAP y aprobacion del operador para prod.
   Verificar web/API con certificados publicos y guardar evidencia de alertas
   y rollback en los tres ambientes antes de declarar terminado el despliegue.

## Cierre despues de la sustentacion del miercoles 25

Eliminar los ambientes y CI por Terraform usando los perfiles y estados
anteriores. CI tiene prevent_destroy para evitar otro reemplazo accidental;
retirarlo solo al preparar su eliminacion intencionada y revisar el plan completo.
No destruir el backend antes que los ambientes. Comprobar instancias, discos,
EIP y logs restantes y conservar la evidencia sin secretos. El presupuesto y la
etiqueta DemoEnd no apagan automaticamente los recursos.

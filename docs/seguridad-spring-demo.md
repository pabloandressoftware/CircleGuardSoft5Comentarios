# Evaluacion de los avisos de Spring para la demostracion

Revision del 23 de noviembre de 2026. Esta evaluacion no afirma que la biblioteca
Spring MVC este parcheada para estos dos avisos. Documenta por que sus funciones
afectadas no forman parte de la ejecucion de CircleGuard.

La imagen inicial de auth produjo 11 hallazgos CRITICAL. Se actualizo Spring Boot
a 3.5.16, Spring Security a la version 6.5.11 de su BOM y Tomcat a 10.1.62. Las
pruebas unitarias y JaCoCo pasaron. El escaneo de esa nueva imagen dejo dos avisos:

| Aviso | Condicion descrita por Spring | Evaluacion de CircleGuard |
| --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | Renderizado de vistas con XsltView | Los controladores propios son REST; no hay configuracion XSLT ni ViewResolver propio |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Fragmentos de vistas enviados mediante SSE | No hay SseEmitter, ServerSentEvent, FragmentsRendering ni endpoints text/event-stream |

Spring publica la correccion OSS en Framework 7.0.9. La correccion 6.2.20 requiere
soporte empresarial; no se incorporan dependencias comerciales sin licencia.
Migrar a Boot 4 requiere una migracion independiente de APIs y pruebas.

`security/spring-rendering-assessment.json` registra exclusivamente esos dos
avisos sobre `spring-webmvc@6.2.19`. `scripts/spring-rendering-vex.mjs` verifica
las fuentes de los ocho servicios y genera OpenVEX para el **digest OCI exacto**
del SBOM y esa version concreta del componente. Rechaza productos ajenos,
versiones diferentes y cambios que introduzcan vistas o SSE. La evaluacion debe
revisarse antes del 27 de noviembre de 2026; despues el generador falla.

El pipeline conserva el informe original, el SBOM, OpenVEX y el resultado con
hallazgos suprimidos visibles. Mantiene `--exit-code 1 --severity CRITICAL`; los
otros hallazgos siguen bloqueando antes de publicar. La declaracion no debe
reutilizarse fuera de CircleGuard ni interpretarse como una correccion del paquete.

Verificacion local de la politica:

El preflight de las nueve imagenes tambien encontro Netty 4.1.135 y FreeMarker
2.3.34 con correcciones disponibles. El BOM comun ahora selecciona Netty
4.1.137.Final y FreeMarker 2.3.35. Notification usa RestClient y ya no incluye
el starter WebFlux; las pruebas verifican el envio HTTP, la propagacion del
error para retry y el modo mock. Las pruebas unitarias y JaCoCo pasan.

La imagen web mantenia paquetes Alpine fijados a NGINX 1.30.6 aunque se ejecutara
apk upgrade. El Dockerfile exige NGINX y sus modulos >=1.30.7. La publicacion de
snapshots comprueba el arranque sin root y la ruta SPA /admin/console despues
de Trivy. El resultado de ese job, sin anticipar su aprobacion, se registra en
docs/evidence/handover-status.json.

Resultado del job #1: los paquetes/modulos de la base unprivileged siguen
requiriendo NGINX 1.30.6-r1 y apk no pudo resolver la mezcla con 1.30.7. La
imagen web no se construyo; este cambio no es una correccion validada. El lote
no llego a Trivy ni a push. El receptor debe corregir mobile/Dockerfile siguiendo
el apartado 6 de la guia y repetir la validacion completa antes de publicar.

```sh
node --test scripts/spring-rendering-vex.test.mjs
node scripts/spring-rendering-vex.mjs --check-sources
```

El soporte usado esta documentado en [Trivy 0.58.1: local VEX](https://github.com/aquasecurity/trivy/blob/v0.58.1/docs/docs/supply-chain/vex/file.md).

## Excepcion de configuracion: salida a Internet de los nodos k3s

Revision del 24 de noviembre de 2026, aprobada por el responsable de la infraestructura.

Trivy fs reporta `AVD-AWS-0104` (CRITICAL) en `infra/terraform/modules/network/main.tf`:
el security group de los nodos permite salida a `0.0.0.0/0`. Es una decision de
diseno de la demostracion, no un descuido:

- Los nodos estan en una subred publica sin NAT Gateway, por costo del plan FREE.
- Necesitan salida a Nexus, Docker Hub, Let's Encrypt, repositorios del SO y APIs de AWS.
- Limitarla a TCP 80/443 no elimina el hallazgo: la regla marca cualquier destino `0.0.0.0/0`.
- El ingreso sigue restringido: SSH y API a los /32 del operador y Jenkins, 80/443 para el Ingress.
  El trafico entre nodos solo se admite desde el mismo security group.

La excepcion vive en `.trivyignore.yaml`, cubre solo ese ID y ese archivo y **vence el
2026-12-31**. Al vencer, Trivy vuelve a bloquear. El pipeline la ejecuta con
`--show-suppressed`, de modo que el hallazgo sigue visible en `security-reports/trivy-fs.txt`.
No cubre CVE de dependencias ni de imagenes; cualquier otro CRITICAL sigue bloqueando.
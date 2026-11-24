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

```sh
node --test scripts/spring-rendering-vex.test.mjs
node scripts/spring-rendering-vex.mjs --check-sources
```

El soporte usado esta documentado en [Trivy 0.58.1: local VEX](https://github.com/aquasecurity/trivy/blob/v0.58.1/docs/docs/supply-chain/vex/file.md).

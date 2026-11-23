# Seguridad del pipeline (HU-20)

## Controles que corren en Jenkins

| Control | Herramienta | Qué revisa | Cuándo falla |
| --- | --- | --- | --- |
| Secretos | gitleaks | Archivos actuales del repositorio | Si encuentra un secreto |
| Dependencias e IaC | Trivy (`fs`) | Gradle, npm, Dockerfile, compose, plantillas de infraestructura | Si hay un hallazgo CRITICAL con corrección disponible |
| Imágenes | Trivy (`image`) | Las 9 imágenes publicadas en un tag de release | Si hay un hallazgo CRITICAL con corrección disponible |

- Etapa `Seguridad`: corre en cada build (menos en un rollback), antes de publicar.
- Etapa `Escanear imagenes`: corre solo en tags `vX.Y.Z`, justo después de publicar y antes de desplegar.
- El código está en `jenkins/vars/circleGuardSecurity.groovy`. Las herramientas corren en contenedores, así que no hay que instalar nada en el agente.
- `.gitleaks.toml` parte de las reglas por defecto y solo exime claves de prueba (`src/test/`), ejemplos y documentación.
- El umbral es CRITICAL. Para subirlo a HIGH se cambia el parámetro `severity` de la librería.

## Comprobaciones hechas

- gitleaks sobre los archivos actuales: sin hallazgos. Con un secreto de prueba puesto a propósito: falla (salida 1).
- Trivy sobre el repositorio encontró un CRITICAL real: `shell-quote` en `mobile/package-lock.json`. Se corrigió con `overrides` en `mobile/package.json` (versión 1.11.0) y las pruebas Jest del cliente web siguen pasando.
- Trivy sobre las imágenes ya construidas (16-nov-2026, `--severity CRITICAL --ignore-unfixed`, el mismo comando de la etapa «Escanear imágenes»):
  - `web`: 4 CRITICAL (nginx 1.28, brotli, openssl). Se corrigió con la base `nginx-unprivileged:1.30-alpine` más `apk upgrade`: ahora 0 CRITICAL y la imagen responde 200.
  - `circleguard-gateway-service` (los servicios Java comparten base y versión de Spring Boot): **sigue con CRITICAL** por `tomcat-embed-core` 10.1.19, `netty-handler` 4.1.107 y `spring-webmvc` 6.1.5, todos de Spring Boot 3.2.4 (`build.gradle.kts`).
- La etapa «Escanear imágenes» corre después de publicar. Mientras los servicios Java sigan con esos CRITICAL, el primer build de un tag fallará en esa etapa (las imágenes ya quedan en Nexus, pero el tag no se considera sano ni se despliega). **Requiere una decisión del equipo**: subir Spring Boot a una versión 3.5.x parcheada (cambia los 8 servicios y hay que volver a correr sus pruebas) o aceptar un riesgo documentado. No se bajó el umbral para que «pase».
- Falta la prueba de que el pipeline se pone en rojo con un CRITICAL dentro de Jenkins (se comprobó solo con la línea de comandos: salida 1).

## Secretos que ya estuvieron expuestos

gitleaks sobre el historial completo encontró 10 coincidencias. Nueve son claves de prueba o configuración de pruebas. Una es de configuración real: `services/circleguard-identity-service/src/main/resources/application.yml` en el commit inicial `8897c00` (HU-07 ya quitó esos valores por defecto del código actual). Un secreto que está en el historial no se borra con un commit nuevo, así que se trata rotándolo.

| Qué rotar | Dónde | Estado |
| --- | --- | --- |
| Clave de cifrado de identity y de JWT de desarrollo | Jenkins Credentials y variables de cada ambiente | Pendiente de rotar por quien opere cada ambiente |
| Contraseñas de base de datos de desarrollo | Secrets de Kubernetes / `.env` del ambiente | Pendiente |

Regla: los valores nuevos solo viven en Jenkins Credentials y en Secrets de Kubernetes, nunca en el repositorio.

## Secretos en Kubernetes

Los Secrets de cada namespace se crean con HU-18 (Helm). Los valores salen de Jenkins Credentials y se inyectan al desplegar; el chart solo referencia el nombre del Secret.

HU-21 agrega `INTERNAL_API_KEY` para llamadas entre servicios y documenta los
roles de Jenkins, las ServiceAccounts, TLS con cert-manager y la política de
rutas en [seguridad-k8s.md](seguridad-k8s.md).

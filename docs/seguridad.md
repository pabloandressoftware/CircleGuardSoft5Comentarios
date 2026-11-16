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
- Falta ver el escaneo de las imágenes en un build real de un tag, y la prueba de que el pipeline se pone en rojo con un CRITICAL.

## Secretos que ya estuvieron expuestos

gitleaks sobre el historial completo encontró 10 coincidencias. Nueve son claves de prueba o configuración de pruebas. Una es de configuración real: `services/circleguard-identity-service/src/main/resources/application.yml` en el commit inicial `8897c00` (HU-07 ya quitó esos valores por defecto del código actual). Un secreto que está en el historial no se borra con un commit nuevo, así que se trata rotándolo.

| Qué rotar | Dónde | Estado |
| --- | --- | --- |
| Clave de cifrado de identity y de JWT de desarrollo | Jenkins Credentials y variables de cada ambiente | Pendiente de rotar por quien opere cada ambiente |
| Contraseñas de base de datos de desarrollo | Secrets de Kubernetes / `.env` del ambiente | Pendiente |

Regla: los valores nuevos solo viven en Jenkins Credentials y en Secrets de Kubernetes, nunca en el repositorio.

## Secretos en Kubernetes

Los Secrets de cada namespace se crean con HU-18 (Helm). Los valores salen de Jenkins Credentials y se inyectan al desplegar; el chart solo referencia el nombre del Secret.

# Changelog

## [1.1.3](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/compare/v1.1.2...v1.1.3) (2026-11-24)


### Bug Fixes

* **k8s:** no imprimir la clave de Neo4j en el log del despliegue ([7e046e2](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/7e046e24dbf1c49f6b3981660dc3e8196c6b3a7b))
* **k8s:** no imprimir la clave de Neo4j en el log del despliegue ([6551a10](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/6551a100953b96be0e5288afbc0beff9a1aac372))

## [1.1.2](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/compare/v1.1.1...v1.1.2) (2026-11-24)


### Bug Fixes

* **auth:** enviar la clave interna a identity-service durante el login ([6c5fb28](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/6c5fb28d547aca12aca2d4ebafdba35d9093b1f6))
* **ci:** aceptar argumentos con nombre en los pasos de despliegue Helm y Compose ([300d8d7](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/300d8d73b317718e69c4cb77bd610e7c176dae5b))
* desbloquear el despliegue Helm y el login de v1.1.1 ([62229cb](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/62229cb60c12f210562ff24055885a2d1b8f5f62))
* **infra:** no reemplazar el worker al detener stage o prod ([e0a7ebc](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/e0a7ebcb570d09b248afdbc503fe6cdc65a76f87))
* **k8s:** dar margen al primer mapeo de identidad en el login ([f55f844](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/f55f84418c3efe43d5cf8b547116914afeb87890))

## [1.1.1](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/compare/v1.1.0...v1.1.1) (2026-11-24)


### Bug Fixes

* **ci:** desbloquear Terraform del pipeline y observabilidad por cluster ([f14f575](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/f14f575067cb737b3aa4f99ebb3e6627a0e4a348))
* **ci:** evitar que dir oculte el paso de Terraform y aplicar el filtro real de ramas ([92b492e](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/92b492e1dbe927fab143ce80e577d0092c97a758))
* **ci:** omitir el proyecto agrupador :services en el analisis de Sonar ([dffb88d](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/dffb88db6fb12f9d96922fe49d9b653fcb69d0ba))
* **observability:** instalar por cluster sin Discord obligatorio y restaurar alertas ([5b19064](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/5b190649cd425abd15bbaad40dd52b24a02c1a79))
* **observability:** no heredar la ruta Watchdog del chart y exigir Alertmanager listo ([012437f](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/012437febf5ee0947b888f12b7fde77f14c53236))
* **release:** consolidate AWS stabilization and reproducible handover snapshots ([388e8a9](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/388e8a9d746955eb2da6ff13a0e13f62e7d67d5a))
* **release:** estabilizar 1.1.0 y preparar despliegue AWS verificable ([ac5ba1f](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/ac5ba1f9301c048734c03a52208bba9572ce7dd3))
* **release:** estabilizar seguridad, despliegue y HU-23 para 1.1.0 ([69cba57](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/69cba57f127974f04e072aa15bb6f160ae6455c0))
* **security:** acotar gitleaks y Trivy fs a codigo fuente con excepcion documentada ([6505371](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/6505371a98527d277c83e3668f35c496cc370253))
* **web:** subir nginx a 1.30.7 para desbloquear el build de la imagen web ([b842e7f](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/b842e7f8aa438be6141cb295eb9753a4cbbdb9f1))
* **web:** subir nginx a 1.30.7 sin modulos dinamicos que lo fijaban a 1.30.6 ([9ca8059](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/9ca8059231d92f6a2c49798553e0da48ed408dc4))

## [1.1.0](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/compare/v1.0.0...v1.1.0) (2026-11-23)


### Features

* **alerts:** agregar alertas criticas y evidencia para Spidey (HU-24) ([f5720e4](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/f5720e4abcc766266cea49e9139cf95da9d7160c))
* **ci:** agregar plan y apply de Terraform al pipeline (HU-19) ([c03db63](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/c03db634be07b68037b99b954feb267fbff48d46))
* **ci:** definir roles admin, desarrollador y aprobador en Jenkins (HU-21) ([7fbe947](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/7fbe947d60fbe605ac293ce444c70787f22d44b8))
* **ci:** integrar Terraform y Helm al Jenkinsfile con promocion (HU-19) ([f35c218](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/f35c2188fba284557f0fb4faba59453032eb2b36))
* **grafana:** agregar tableros tecnico y de negocio (HU-24) ([b5e9ae9](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/b5e9ae9e21dd8de212fb74bc07e462720b44efdc))
* **infra:** implementar ambientes Terraform reutilizables (HU-17) ([f7438f3](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/f7438f3b9747cbad34348f12688377fc8693d75e))
* **k8s:** chart Helm generico, sondas Actuator y middleware (HU-18) ([702d15f](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/702d15f00c1f5f48ac9c84e939cd74f53a5b38e2))
* **k8s:** chart Helm generico, sondas Actuator y middleware para Kubernetes (HU-18) ([2b537f0](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/2b537f043e48d529528927d49483304f1a709fc9))
* **k8s:** crear ServiceAccounts sin token por defecto (HU-21) ([74eafc2](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/74eafc2142b27bace0a98aabe32899b22ec8f9f4))
* **k8s:** desplegar por ambiente con Helm y esperar rollout status (HU-19) ([4bc47d2](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/4bc47d2b8c6d042dbd11312f02e6a390738308c1))
* **k8s:** habilitar TLS con cert-manager y verificarlo con curl (HU-21) ([6553be9](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/6553be986b009f78aa33b5fac2c14ee8c9bb335d))
* **metrics:** instrumentar promociones, contencion y validaciones (HU-24) ([43af99d](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/43af99d1bf9bc47e4f957bfced845e5a2a6db9e2))
* **observabilidad:** agente OpenTelemetry en el chart y logs con traceId (HU-25) ([68456f7](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/68456f7a11921463041f7e2726af264d7a2436c4))
* **observabilidad:** Jaeger y Loki con Promtail para trazas y logs (HU-25) ([43e92ee](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/43e92eee2b1d4e07f2f756a55e14f7c5fcbe43b4))
* **observability:** desplegar kube-prometheus-stack y ServiceMonitor (HU-24) ([ac10f6e](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/ac10f6ed99ca10b64f39aaef011c30cda3ee872e))
* **releases:** registrar versiones y medir rollback Helm (HU-27) ([85c9429](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/85c9429dc73183d1ba96cf238e7d85df7994a5d7))
* **security:** cerrar auth por defecto y proteger map y admin (HU-21) ([bf49d52](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/bf49d527a4589735de5d7687918fa995b0c286da))


### Bug Fixes

* **ci:** Jenkins incluye buildx para publicar imagenes ([06bbc69](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/06bbc699a58823303eb08852293c7ff7ce66980c))
* **ci:** Jenkins incluye buildx para que los Dockerfile con --mount compilen ([b41714c](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/b41714c8fb11b863bc8d5b029a1248ea35445879))
* **infra:** conservar LF en cloud-init de k3s ([b54eeca](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/b54eecaa0627ca4e2496cebbb6e40c067ba7ac99))
* **k8s:** dar mas tiempo de arranque a PostgreSQL en middleware.sh (HU-18) ([9e3a766](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/9e3a76649216030e67aac3ac44e7bc9bcf45e15e))
* **k8s:** Kafka con imagen oficial, Neo4j 2Gi y sondas de arranque (HU-18) ([44d5284](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/44d5284036d78a0b6313529074ed0258974d8ee0))
* **k8s:** los 8 servicios probados en kind; neo4j sin LoadBalancer y timeout de 10 min (HU-18) ([e1ef29b](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/e1ef29b756d647cf6b147561b1b4e634e1a927f7))
* **observability:** seleccionar targets por namespace en las alertas (HU-24) ([5bdf733](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/5bdf7330d3f5f746b521c26a628332916aca6b7c))
* **promotion:** serializar eventos de Kafka como JSON para que lleguen a notification (HU-25) ([5decf7a](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/5decf7ad4aa6be2bf58434c72614ff2f133cab73))
* **web:** base nginx 1.30 y apk upgrade para dejar la imagen sin CVE criticos (HU-20) ([4b7599b](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/4b7599b7182aa8a93fc46c50fffa78139ba67a17))

## 1.0.0 (2026-11-12)


### Features

* **ci:** aprovisionar Jenkins, SonarQube y Nexus (HU-04) ([7d6d250](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/7d6d2505bb9ff78542ba4d95d647d57c8b5e0f93))
* **ci:** aprovisionar Jenkins, SonarQube y Nexus con un solo script (HU-04) ([ad83e29](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/ad83e297195b3a86b608c2fef15fbcf5dbf33003))
* **ci:** comparar ramas nuevas contra dev para build selectivo ([87196bf](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/87196bf3754e7dd72ebb221d6686b22e14c32609))
* **ci:** desplegar y promover dev, stage y prod en tags (HU-10) ([63fb057](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/63fb0578e69d3032cd0533899646ecfcbc852dab))
* **ci:** desplegar y promover dev, stage y prod en tags (HU-10) ([49ea026](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/49ea0266cb24f4d2487a510922e7dadad32d78b5))
* **ci:** pipeline multibranch con shared library y build selectivo (HU-08) ([557efe7](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/557efe75143fd3b206b875a9f849e9274f12c207))
* **ci:** pipeline multibranch con shared library y build selectivo (HU-08) ([8e796a8](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/8e796a8542d41d02eba9cc6edd80633c1a8dbcdd))
* **config:** secretos por variables y Compose por ambiente (HU-07) ([85b2978](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/85b29781e2859e903c526227b3b42861d6e2bdce))
* **config:** secretos y URLs de los servicios por variables de entorno (HU-07) ([ffe429e](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/ffe429e1a8fac971b973610c6d90016a39c12f45))
* **deploy:** compose por ambiente con proxy HTTPS, healthchecks y variables (HU-07) ([90bb800](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/90bb8001ee36f9d35ffee8170a01253a0e5fc16a))
* **docker:** empaquetar ocho servicios Java y el cliente web (HU-06) ([4c97c47](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/4c97c47b5d3ac23c30f36671b113edc887295743))
* **docker:** empaquetar ocho servicios Java y el cliente web (HU-06) ([fa158da](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/fa158da2f7c58c0db4f179d42516780b5d9dc4c7))
* **governance:** commitlint, plantillas de PR y cambio y tabla de aprobadores (HU-02) ([ac0b625](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/ac0b625939bbbf1dcb7c36adb786058dd24eda6d))
* **governance:** commitlint, plantillas y tabla de aprobadores (HU-02) ([4bf99b4](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/4bf99b4834df2b626c80cfbc8ef6fe216de81aa1))
* **infra:** ambientes dev, stage y prod aislados en AWS EC2 (HU-05) ([4ee8f9d](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/4ee8f9d53d0f94a2d28d617474cf11897604be39))
* **infra:** ambientes dev, stage y prod aislados en AWS EC2 (HU-05) ([2580ca4](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/2580ca45095afb3bd89b83dc13eee6776a27cb75))
* **mobile:** URLs de API por variables EXPO_PUBLIC (HU-07) ([7ed752c](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/7ed752c6f4512d433576979e252ac1827a5bdf45))
* **release:** automatizar SemVer y notas de entrega con release-please (HU-03) ([86229fd](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/86229fd18795a929fe644dd91891c77d3ea4751b))
* **release:** automatizar SemVer y notas de entrega con release-please (HU-03) ([bfa9282](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/bfa928246675c4293670240135511a40fe4db3fa))


### Bug Fixes

* **auth:** vigencia del QR en milisegundos y expiresIn coherente (HU-12) ([c31642c](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/c31642c04c4c58c01d5bd8792218f23725b80d20))
* **ci:** cerrar versionado y Playwright en dev ([c5a3750](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/c5a375037151c661880166ea756946093021da25))
* **ci:** cerrar versionado y Playwright en dev ([304f9a9](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/304f9a9a274705d5d9f8707f7ccc810887da3017))
* **ci:** credenciales opcionales, libraryPath y builds por lotes (HU-08) ([1184b64](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/1184b6472ff4c5c00a253bd96677657676d3d9e2))
* **ci:** el multibranch descubre ramas y PR, no solo tags ([e594163](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/e594163a6a99c5fe95af1402deaeb8c6cb97901a))
* **ci:** el multibranch descubre ramas y PR, no solo tags ([eb72340](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/eb723400b5b1142f90be5c081217c107a455ab93))
* **ci:** el multibranch solo construye dev, main, PR y tags de release ([89678da](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/89678da3d21ed13afb2cf5d1decabd63333a17a2))
* **ci:** mantener la sesion de Jenkins al registrar la credencial de GitHub ([b1f5478](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/b1f5478e614a27c7b2cb5ca7171bcc22657b986e))
* **gateway:** negar acceso a SUSPECT, PROBABLE y CONFIRMED (HU-12) ([f230437](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/f230437069f281b630b30b47ebd959efe4d5bd6c))
* **governance:** permitir siglas al inicio del asunto de commits (HU-02) ([8539636](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/8539636b74973d5845d94f4e52636bc5e403c47d))
* **governance:** permitir siglas y nombres propios al inicio del asunto (HU-02) ([6647e91](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/6647e915251d0761c5ac746d85ead5d98a55c738))
* **HU-07:** quita secretos por defecto del codigo, valida el esquema en stage y prod ([58399c4](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/58399c4708e97044727322e9dd531068942c01b4))
* **HU-07:** quita secretos por defecto y valida el esquema en stage y prod ([fbd5e0a](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/fbd5e0a1f4cba620750aafe831d82d5bb17c1e33))
* **HU-11:** el aviso de fallo trae enlace al build y nombre legible ([0066819](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/0066819b923635444e02c965f7fe1ccd9c4414c2))
* **HU-11:** la notificacion de fallo trae enlace al build y nombre legible ([aae616e](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/aae616eba596625374fe44359f212309d4c8bc61))
* **test:** compatibilidad de Testcontainers con Docker Engine 29 (HU-14) ([b6773b7](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/b6773b73160c68e6dc610a284db8fe3d413fa724))
* **test:** reparar pruebas de backend de identity, form y promotion (HU-12) ([545b942](https://github.com/pabloandressoftware/CircleGuardSoft5Comentarios/commit/545b942934d84ccb4f7649362a28d0a844b3e182))

## Changelog

Release Please actualiza este archivo con los cambios de cada release.

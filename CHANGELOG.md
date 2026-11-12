# Changelog

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

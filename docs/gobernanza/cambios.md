# Gobernanza de cambios (HU-02)

## Estrategia de ramas

```
hu-XX  ->  dev  ->  main
```

- Una rama por historia de usuario (`hu-04`, `hu-06`, ...). Sale de `main`, o de la
  HU de la que depende cuando hay herencia (por ejemplo `hu-08` parte de `hu-04`).
- Los cambios entran a `dev` por Pull Request. `dev` pasa a `main` por Pull Request
  cuando el sprint queda verificado.
- Nadie hace push directo a `main` ni a `dev`.

## Proteccion de ramas

| Regla | `main` | `dev` |
| --- | --- | --- |
| Pull Request obligatorio | Si | Si |
| Aprobaciones requeridas | 1 | 1 |
| Checks requeridos | `commitlint` | `commitlint` |
| Rama al dia antes de mergear | Si | No |
| Resolver conversaciones | Si | Si |
| Push forzado y borrado de la rama | Prohibidos | Prohibidos |

Jenkins construye cada PR, pero su estado no es un check requerido: el multibranch
corre en el CI del equipo y no reporta como check obligatorio en GitHub. La revision de
Jenkins (pruebas, cobertura y Sonar) se mira en el build antes de aprobar.

## Commits

Se validan con commitlint (Conventional Commits): `tipo(alcance): descripcion`, con los
tipos `feat`, `fix`, `docs`, `ci`, `chore`, `test`, `refactor`, `perf`, `build`, `revert`.
El workflow `.github/workflows/commitlint.yml` revisa los commits de cada PR.

## Plantillas

- Pull Request: `.github/pull_request_template.md` (tipo, riesgo, rollback, verificacion).
- Solicitud de cambio: `.github/ISSUE_TEMPLATE/solicitud-de-cambio.yml`.

## Quien aprueba cada tipo de cambio

El autor del PR nunca aprueba el suyo.

| Tipo de cambio | Aprobador | Respaldo |
| --- | --- | --- |
| Infraestructura, Docker, Nexus, SonarQube (HU-04, HU-06) | Rol B · pabloandressoftware | Rol A |
| Versiones, ambientes AWS, pruebas E2E (HU-03, HU-05, HU-15) | Rol A · damaral2005 | Rol B |
| Pipeline de Jenkins y despliegue (HU-08, HU-10, HU-11) | Rol C · Rodolfo Moreno (@Matatrolls, por confirmar) | Rol B |
| Configuracion, calidad y pruebas de servicios (HU-07, HU-09, HU-12) | Rol D · itsnigori98 | Rol A |
| Documentacion y gestion agil (HU-01, HU-02, HU-16) | Rol A · damaral2005 | Cualquier otro rol |
| Cambios de riesgo alto (autenticacion, datos, produccion) | Dos aprobaciones: el rol del area y un segundo rol | Propietario del repositorio |

## Flujo de una solicitud de cambio

1. Se abre una solicitud con la plantilla (HU de Jira, tipo, riesgo, descripcion).
2. Se trabaja en una rama de la HU y se abre un PR con su plantilla (riesgo, rollback, verificacion).
3. El PR dice quien es el responsable en Jira. Aprueba una persona distinta del autor,
   segun la tabla anterior; si el riesgo es alto, se piden dos aprobaciones.
4. Con la aprobacion, `commitlint` en verde y Jenkins revisado, se une a `dev`.
5. Cuando el sprint queda verificado, `dev` pasa a `main` por otro PR con la misma regla.

## Versionado y release notes

- Se usa **SemVer** (`MAJOR.MINOR.PATCH`) y se decide solo a partir de los commits:
  `fix` sube PATCH, `feat` sube MINOR y un cambio incompatible (`!`) sube MAJOR.
- **release-please** (`.github/workflows/release-please.yml`) corre al unir a `main`. Abre un
  PR de release con el `CHANGELOG.md` generado a partir de los commits. Al unirlo se crea
  el tag `vX.Y.Z` y la release en GitHub, con las notas automaticas.
- El tag `v*` dispara el build de release en Jenkins (publica las imagenes con esa version
  y sigue hacia stage y prod con aprobacion manual).
- Primera release: `v1.0.0` (PR #37).
- Volver atras: ejecutar el pipeline con `ROLLBACK_TAG` y `ROLLBACK_ENV` (ver README).

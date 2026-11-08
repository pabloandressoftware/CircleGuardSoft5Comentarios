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
| Checks requeridos | `commitlint` y `continuous-integration/jenkins/pr-merge` | `commitlint` |
| Rama al dia antes de mergear | Si | No |
| Resolver conversaciones | Si | Si |
| Push forzado y borrado de la rama | Prohibidos | Prohibidos |

El check de Jenkins lo publica el multibranch de HU-08 con GitHub Branch Source.
Mientras Jenkins no reporte en este repositorio, el propietario puede mergear sin ese
check; el resto del equipo queda bloqueado, que es lo esperado.

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

## Resumen

<!-- Que cambia y por que. Enlaza la HU de Jira (KAN-xx). -->

HU: KAN-

## Tipo de cambio

- [ ] feat (funcionalidad nueva)
- [ ] fix (correccion)
- [ ] ci / infra (pipeline, Docker, AWS)
- [ ] docs
- [ ] test
- [ ] refactor / chore

## Riesgo

- [ ] Bajo (documentacion, pruebas, sin impacto en ejecucion)
- [ ] Medio (cambia comportamiento de un servicio o del pipeline)
- [ ] Alto (afecta autenticacion, datos, ambientes o produccion)

Que puede salir mal:

## Rollback

<!-- Como se revierte: revert del PR, imagen anterior en Nexus, helm rollback, etc. -->

## Verificacion

- [ ] Los checks de Jenkins y commitlint pasan
- [ ] Probado localmente (describe como)
- [ ] Documentacion actualizada si aplica
- [ ] El aprobador corresponde a la tabla de `docs/gobernanza/cambios.md`

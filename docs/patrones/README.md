# Patrones de arquitectura de CircleGuard (HU-22)

Esta carpeta documenta los patrones que usa CircleGuard: para qué sirve cada uno,
dónde está en el código, qué se gana y qué se paga al usarlo, y cómo comprobar que
funciona. Los cuatro primeros ya existían en el código; el quinto (Retry + Circuit
Breaker en los clientes HTTP) se implementó en esta historia.

| Patrón | Dónde | Propósito | Trade-off principal | Documento |
| --- | --- | --- | --- | --- |
| Base de datos por servicio | auth, identity, promotion, form, dashboard | Cada servicio es dueño de sus datos y evoluciona su esquema sin coordinar con los demás | No hay *joins* ni transacciones entre servicios; la consistencia entre ellos es eventual | [base-de-datos-por-servicio.md](base-de-datos-por-servicio.md) |
| Eventos (Kafka) | form → promotion → notification | Desacoplar a quien produce un cambio de quienes reaccionan | Más piezas que operar y procesamiento asíncrono: el efecto no es inmediato | [eventos.md](eventos.md) |
| Cache-aside | promotion (Caffeine + Redis), gateway (Redis) | Responder rápido el estado de salud sin consultar el grafo cada vez | Puede servir un dato viejo hasta que se invalida o vence | [cache-aside.md](cache-aside.md) |
| Máquina de estados | promotion (`HealthStatusService`, `StatusLifecycleService`) | Que el estado de salud solo cambie por transiciones válidas | Las reglas de transición viven en Cypher y en Java; cambiarlas exige probar ambos | [maquina-de-estados.md](maquina-de-estados.md) |
| Retry + Circuit Breaker | auth → identity, dashboard → promotion (Resilience4j); notification (Spring Retry) | Que una caída de otro servicio se responda rápido y de forma controlada | Un fallo pasajero puede rechazar llamadas válidas mientras el circuito está abierto | [retry-y-circuit-breaker.md](retry-y-circuit-breaker.md) |

## Umbral de latencia propuesto (para confirmar con el equipo)

El análisis del proyecto propone que, con identity-service apagado, el login responda
con un error controlado en **menos de 1 segundo**. La configuración por defecto lo
cumple así:

| Situación | Tiempo de respuesta del login | Por qué |
| --- | --- | --- |
| identity responde normal | Igual que antes | Las anotaciones no agregan esperas |
| identity apagado, circuito cerrado | Peor caso teórico **950 ms**; en la práctica decenas de ms (conexión rechazada) | 2 intentos × (200 ms de conexión + 250 ms de lectura) + 50 ms de espera |
| identity apagado, circuito abierto | **Casi inmediato** (< 100 ms en la prueba) | El circuito rechaza la llamada sin salir a la red |

Todos los valores se pueden ajustar por variables de entorno (ver
[retry-y-circuit-breaker.md](retry-y-circuit-breaker.md#configuración)).

## Cómo comprobarlo

```bash
docker run --rm -v "$PWD":/workspace -w /workspace \
  -v circleguard-gradle-cache:/home/gradle/.gradle gradle:8.14.5-jdk21 \
  gradle --no-daemon :services:circleguard-auth-service:test :services:circleguard-dashboard-service:test
```

Las pruebas `IdentityClientResilienceTest`, `LoginControllerTest` y
`PromotionClientResilienceTest` cubren los criterios 2 y 3 de la HU-22. La prueba manual
con identity apagado en un ambiente está en
[retry-y-circuit-breaker.md](retry-y-circuit-breaker.md#evidencia).

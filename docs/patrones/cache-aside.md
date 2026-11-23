# Cache-aside

## Propósito

El estado de salud de cada persona se consulta muchas veces (cada QR en la puerta, cada
pantalla) pero cambia pocas. Calcularlo desde el grafo de Neo4j en cada consulta sería lento.
Con **cache-aside** la aplicación busca primero en la caché; si no está, lo lee de la fuente,
lo guarda en la caché y lo devuelve. Cuando el estado cambia, se **invalida** la entrada para
que la siguiente lectura traiga el valor nuevo.

## Dónde está

Hay dos niveles, ambos en promotion-service, y un lector en gateway:

| Nivel | Qué guarda | Dónde |
| --- | --- | --- |
| Caché local (Caffeine) | `userStatus`, `proximityMatches`, `buildingMetadata`, `systemSettings`; máximo 10 000 entradas, vencen a los 5 minutos | `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/CacheConfig.java` |
| Redis compartido | Clave `user:status:{anonymousId}` con el estado actual | Escribe `HealthStatusService` y `StatusLifecycleService`; lee `QrValidationService` (gateway) |

En `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java`:

```java
@Cacheable(cacheNames = "userStatus", key = "#anonymousId")   // 1. busca en caché; si no, lee Redis y guarda
public String getCachedStatus(String anonymousId) { ... }

@CacheEvict(cacheNames = "userStatus", allEntries = true)      // 2. un cambio de estado invalida la caché
public void updateStatus(String anonymousId, String status, boolean adminOverride) { ... }

@CacheEvict(cacheNames = "userStatus", key = "#anonymousId")   // 3. invalidación puntual
public void evictUserCache(String anonymousId) { ... }
```

Al cambiar un estado, promotion actualiza Neo4j (la fuente), escribe Redis en lote
(`multiSet`) y vacía la caché local. El gateway lee Redis directamente para decidir en la
puerta sin llamar a promotion.

## Trade-offs

| Se gana | Se paga |
| --- | --- |
| Lecturas rápidas: la puerta decide sin consultar el grafo | **Datos posiblemente viejos**: entre el cambio y la invalidación (o el vencimiento de 5 min) puede leerse el valor anterior |
| Menos carga sobre Neo4j | Hay que acordarse de invalidar en cada escritura; olvidar un `@CacheEvict` es un error silencioso |
| La caché local no necesita red | Con varias réplicas de promotion, cada una tiene su propia caché local; por eso el valor compartido está en Redis |
| Si Redis no tiene la clave, el sistema sigue funcionando | La primera lectura después de invalidar es más lenta (*cache miss*) |

`updateStatus` invalida **toda** la caché `userStatus` (`allEntries = true`) porque un cambio
se propaga a los contactos del grafo; es más simple y seguro que invalidar una por una, a
cambio de más *cache misses* justo después de un cambio.

## Evidencia

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/service/HealthStatusServiceTest.java`:
  actualiza estados y verifica la escritura en Redis.
- En un ambiente, el valor que lee la puerta:
  ```bash
  sudo docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning keys 'user:status:*'
  ```

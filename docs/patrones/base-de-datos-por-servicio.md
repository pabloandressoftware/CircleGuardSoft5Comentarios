# Base de datos por servicio

## Propósito

Cada microservicio guarda sus datos en su propia base y ningún otro servicio la lee ni
la escribe directamente. Si otro servicio necesita un dato, lo pide por la API del dueño
o lo recibe por un evento. Así cada equipo puede cambiar su esquema, migrarlo y escalarlo
sin coordinar con los demás, y un error en una base no corrompe los datos de otro servicio.

## Dónde está

| Servicio | Base | Configuración |
| --- | --- | --- |
| auth-service | `circleguard_auth` (PostgreSQL) | `services/circleguard-auth-service/src/main/resources/application.yml` |
| identity-service | `circleguard_identity` (PostgreSQL) | `services/circleguard-identity-service/src/main/resources/application.yml` |
| promotion-service | `circleguard_promotion` (PostgreSQL) + Neo4j (grafo de contactos) + Redis (estado) | `services/circleguard-promotion-service/src/main/resources/application.yml` |
| form-service | `circleguard_form` (PostgreSQL) | `services/circleguard-form-service/src/main/resources/application.yml` |
| dashboard-service | `circleguard_dashboard` (PostgreSQL) | `services/circleguard-dashboard-service/src/main/resources/application.yml` |

Las cinco bases se crean al primer arranque en `deploy/postgres/init-db.sql`. Cada
servicio versiona su propio esquema con Flyway (`src/main/resources/db/migration`).

Ejemplo de la regla en la práctica: auth-service **no** consulta la tabla de identidades.
Para obtener el `anonymousId` de un usuario llama a la API de identity
(`IdentityClient` → `POST /api/v1/identities/map`). Lo mismo hace dashboard con
promotion (`PromotionClient`).

## Trade-offs

| Se gana | Se paga |
| --- | --- |
| Autonomía: cada servicio cambia su esquema sin romper a otros | No hay *joins* entre servicios; los datos se combinan por API o por eventos |
| Aislamiento de fallos y de carga | Sin transacciones distribuidas: la consistencia entre servicios es eventual |
| Cada servicio usa el almacenamiento que le sirve (PostgreSQL, Neo4j, Redis) | Más bases que respaldar, migrar y monitorear |
| La privacidad se refuerza: solo identity guarda la relación entre identidad real y anónima | Las llamadas entre servicios pueden fallar; por eso existen [Retry y Circuit Breaker](retry-y-circuit-breaker.md) |

**Decisión de costo:** en los ambientes las cinco bases viven en el **mismo servidor**
PostgreSQL (un contenedor). Se mantiene la separación lógica (base, usuario de conexión y
migraciones propias) sin pagar cinco servidores. La contrapartida es que si ese servidor
cae, caen los cinco servicios que dependen de él.

## Evidencia

- `deploy/postgres/init-db.sql`: crea `circleguard_auth`, `circleguard_identity`,
  `circleguard_promotion`, `circleguard_dashboard` y `circleguard_form`.
- La URL JDBC de cada `application.yml` apunta a su propia base.
- En un ambiente desplegado:
  ```bash
  sudo docker compose exec -T postgres psql -U "$DB_USERNAME" -c '\l' | grep circleguard
  ```

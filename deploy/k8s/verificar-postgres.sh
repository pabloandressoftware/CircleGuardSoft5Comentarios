#!/usr/bin/env bash
set -euo pipefail
environment=${1:?Uso: verificar-postgres.sh dev|stage|prod}
[[ $environment =~ ^(dev|stage|prod)$ ]] || exit 2
for database in circleguard_auth circleguard_identity circleguard_promotion circleguard_dashboard circleguard_form; do
  kubectl -n "circleguard-$environment" exec -i postgres-0 -- bash -c '
    export PGPASSWORD="$(cat "$POSTGRES_PASSWORD_FILE")"
    exec psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1
  ' -- "$database" <<'SQL'
SELECT current_user, current_database(), has_schema_privilege(current_user, 'public', 'CREATE') AS can_create_schema_objects;
DO $$ BEGIN
  IF NOT has_schema_privilege(current_user, 'public', 'CREATE') THEN
    RAISE EXCEPTION 'El usuario de la aplicacion no puede ejecutar migraciones en public';
  END IF;
END $$;
SQL
done

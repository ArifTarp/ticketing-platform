#!/bin/bash
set -e

# One database AND one least-privilege role per service. A service's future datasource
# should authenticate as its own role, not the POSTGRES_USER superuser — that way a
# copy-pasted/misconfigured datasource.url pointing at another service's database fails
# to connect (or fails to write, since the role doesn't own that schema) instead of
# silently succeeding, which is what ADR-0001's "no shared schema" boundary depends on.
for service in auth event booking payment notification; do
  db="ticketing_${service}"
  role="${service}_app"
  password="${service}_app_pw"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    CREATE DATABASE ${db};
    CREATE ROLE ${role} WITH LOGIN PASSWORD '${password}';
    REVOKE CONNECT ON DATABASE ${db} FROM PUBLIC;
    GRANT CONNECT ON DATABASE ${db} TO ${role};
    GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${role};
EOSQL

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${db}" -c "ALTER SCHEMA public OWNER TO ${role};"
done

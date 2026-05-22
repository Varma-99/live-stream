#!/usr/bin/env bash
# Creates DB/user and applies Phase 2 SQL (requires local PostgreSQL).
set -euo pipefail

DB_NAME="${DB_NAME:-livestream}"
DB_USER="${DB_USER:-livestream}"
DB_PASSWORD="${DB_PASSWORD:-change-me}"

psql postgres -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';
  END IF;
END
\$\$;
SELECT 'CREATE DATABASE ${DB_NAME} OWNER ${DB_USER}'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${DB_NAME}')\gexec
GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
SQL

export PGPASSWORD="${DB_PASSWORD}"
psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -f src/main/resources/db/migration/V1__schema.sql
psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -f src/main/resources/db/migration/V2__seed_dev_users.sql

echo "Database ready: ${DB_NAME} (user: ${DB_USER})"

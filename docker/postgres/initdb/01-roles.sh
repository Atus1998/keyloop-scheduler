#!/usr/bin/env bash
# Creates the two database users (docs/system-design.md, section 2). PostgreSQL runs this once,
# when the data volume is new: after a change here, recreate the volume (docker compose down -v).
#   scheduler_migrator  owns the database; Flyway migrates with it (DDL, CREATE EXTENSION).
#   scheduler_app       the API; reads and writes rows, cannot change the schema.
# Passwords are optional. Docker Compose uses no passwords: it publishes no database port, and
# 02-pg_hba.sh lets only these two users reach this database. Anywhere else, set
# SCHEDULER_MIGRATOR_PASSWORD and SCHEDULER_APP_PASSWORD (an empty value removes the password).
set -euo pipefail

# Passwords are read inside psql (\getenv), so they never appear on a command line.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" <<'SQL'
CREATE ROLE scheduler_migrator LOGIN;
CREATE ROLE scheduler_app LOGIN;

\getenv migrator_password SCHEDULER_MIGRATOR_PASSWORD
\if :{?migrator_password}
  ALTER ROLE scheduler_migrator PASSWORD :'migrator_password';
\endif
\getenv app_password SCHEDULER_APP_PASSWORD
\if :{?app_password}
  ALTER ROLE scheduler_app PASSWORD :'app_password';
\endif

-- The owner may create in schema public (PostgreSQL 15+) and install trusted extensions.
ALTER DATABASE :"db" OWNER TO scheduler_migrator;
-- No CONNECT or TEMP for every role by default; the app gets CONNECT only.
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO scheduler_app;
GRANT USAGE ON SCHEMA public TO scheduler_app;

-- Tables and sequences Flyway creates later: data access for the app, nothing more.
-- db/migration/afterMigrate.sql takes these rights back on flyway_schema_history.
ALTER DEFAULT PRIVILEGES FOR ROLE scheduler_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO scheduler_app;
ALTER DEFAULT PRIVILEGES FOR ROLE scheduler_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO scheduler_app;

-- Functions Flyway creates later (lock_booking_day): only the app may call them. The revoke
-- from PUBLIC must be database-wide; a per-schema rule can only add rights.
ALTER DEFAULT PRIVILEGES FOR ROLE scheduler_migrator
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE scheduler_migrator IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO scheduler_app;
SQL

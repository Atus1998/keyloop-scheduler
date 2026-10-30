#!/usr/bin/env bash
# Docker Compose only (tests mount 01-roles.sh alone). Compose uses no passwords, so who may
# connect is narrowed here instead: over TCP, only the two app users and only to the scheduler
# database; the superuser only through the socket inside this container (docker compose exec).
# Replaces the "host all all all trust" line that POSTGRES_HOST_AUTH_METHOD=trust writes.
# Read when the server starts after the init scripts.
set -euo pipefail

cat > "$PGDATA/pg_hba.conf" <<'HBA'
# TYPE  DATABASE   USER                              ADDRESS  METHOD
local   all        postgres                                   trust
local   scheduler  scheduler_app,scheduler_migrator           trust
host    scheduler  scheduler_app,scheduler_migrator  all      trust
local   all        all                                        reject
host    all        all                               all      reject
HBA

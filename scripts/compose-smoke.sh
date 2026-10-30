#!/usr/bin/env bash
# Smoke test for Docker Compose (backlog #11). Builds the image, starts the stack under its own
# project name and volume, books one appointment, restarts on the same volume, and removes
# everything at the end. Needs Docker and a free port 8080.
# KEEP=1 leaves the stack running for a look around.
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT=scheduler-smoke
BASE_URL=http://localhost:8080
HEALTH_WAIT_SECONDS=120
compose() { docker compose -p "$PROJECT" -f compose.yaml "$@"; }

failures=0
pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1"; failures=$((failures + 1)); }
check() { local name=$1; shift; if "$@"; then pass "$name"; else fail "$name"; fi; }

cleanup() {
  if [[ "${KEEP:-0}" != 1 ]]; then
    compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
}

# Two days ahead at 09:00 Saigon time: inside opening hours and the 90-day window.
start_time() {
  if date -v+2d +%F >/dev/null 2>&1; then
    printf '%sT09:00:00+07:00' "$(date -v+2d +%F)"
  else
    printf '%sT09:00:00+07:00' "$(date -d '+2 days' +%F)"
  fi
}

wait_for_health() {
  local deadline=$((SECONDS + HEALTH_WAIT_SECONDS))
  while ((SECONDS < deadline)); do
    if curl -fsS "$BASE_URL/actuator/health" 2>/dev/null | grep -q '"db":{"status":"UP"}'; then
      return 0
    fi
    sleep 2
  done
  return 1
}

[[ -f compose.yaml ]] || { echo "FAIL  compose.yaml not found"; exit 1; }
if lsof -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "FAIL  port 8080 is in use; stop that process first"
  exit 1
fi

trap cleanup EXIT
compose down -v --remove-orphans >/dev/null 2>&1 || true

echo "Building and starting (first build downloads Gradle and dependencies)..."
compose up -d --build

check "db container is healthy" \
  test "$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q db)")" = healthy
check "health is UP with db UP" wait_for_health

BODY=$(printf '{"dealershipId": 1, "vehicleId": 1, "serviceTypeId": 1, "startTime": "%s"}' \
  "$(start_time)")
created=$(curl -sS -i -X POST "$BASE_URL/appointments" \
  -H 'Content-Type: application/json' -d "$BODY" | tr -d '\r')
location=$(printf '%s\n' "$created" | awk 'tolower($1) == "location:" {print $2}')
check "POST /appointments gives 201" grep -q '^HTTP/1.1 201' <<<"$created"
check "201 has a Location header" test -n "$location"
check "GET Location gives 200" \
  test "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$location")" = 200

# "docker compose port" prints ":0" for an unpublished port, so read the bindings themselves.
check "db publishes no port to the host" \
  test "$(docker inspect -f '{{len .HostConfig.PortBindings}}' "$(compose ps -q db)")" = 0
api_uid=$(compose exec -T api id -u 2>/dev/null | tr -d '\r' || true)
check "api does not run as root" bash -c "[[ '$api_uid' =~ ^[0-9]+\$ && '$api_uid' != 0 ]]"
check "app user cannot create tables" \
  bash -c "! docker compose -p $PROJECT -f compose.yaml exec -T db \
    psql -v ON_ERROR_STOP=1 -U scheduler_app -d scheduler -c 'CREATE TABLE smoke (i int)' \
    >/dev/null 2>&1"

# From another container on the Compose network, over TCP like the API.
on_network() {
  docker run --rm --network "${PROJECT}_default" postgres:16 \
    psql -v ON_ERROR_STOP=1 -h db "$@" -c 'SELECT 1' >/dev/null 2>&1
}
refused() { ! on_network "$@"; }
check "app user reaches the scheduler database over TCP" on_network -U scheduler_app -d scheduler
check "superuser is refused over TCP" refused -U postgres -d scheduler
check "app user is refused on other databases over TCP" refused -U scheduler_app -d postgres

echo "Restarting on the same volume..."
compose down
compose up -d
check "health is UP again after restart" wait_for_health
check "booked appointment survives restart" \
  test "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$location")" = 200
check "same booking again gives 200 (retry)" \
  test "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/appointments" \
    -H 'Content-Type: application/json' -d "$BODY")" = 200
seed=$(compose exec -T db psql -tA -U postgres -d scheduler -c \
  "SELECT (SELECT count(*) FROM dealership) || ',' ||
          (SELECT count(*) FROM flyway_schema_history WHERE script = 'R__seed.sql')" | tr -d '\r')
check "seed loaded once: 2 dealerships, 1 seed run" test "$seed" = "2,1"
check "app user cannot read the migration history after restart" \
  bash -c "! docker compose -p $PROJECT -f compose.yaml exec -T db \
    psql -v ON_ERROR_STOP=1 -U scheduler_app -d scheduler \
    -c 'SELECT 1 FROM flyway_schema_history' >/dev/null 2>&1"

if ((failures > 0)); then
  echo "$failures check(s) failed"
  exit 1
fi
echo "All checks passed"

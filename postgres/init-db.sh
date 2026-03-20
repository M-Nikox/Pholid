#!/usr/bin/env bash
set -euo pipefail

# Fail early if required env vars are missing
: "${PANGOLIN_POSTGRES_USER:?PANGOLIN_POSTGRES_USER must be set}"
: "${PANGOLIN_POSTGRES_PASSWORD:?PANGOLIN_POSTGRES_PASSWORD must be set}"
: "${PANGOLIN_POSTGRES_DB:?PANGOLIN_POSTGRES_DB must be set}"

# The initial superuser connection (postgres user → keycloak DB, as set in the compose)
PSQL_CONNECT=(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB")

# ── Pangolin role ─────────────────────────────────────────────────────────────

echo "==> Checking/creating role ${PANGOLIN_POSTGRES_USER}"
ROLE_EXISTS=$("${PSQL_CONNECT[@]}" -tAc "SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = '${PANGOLIN_POSTGRES_USER}';")
if [ "${ROLE_EXISTS}" != "1" ]; then
  echo "Creating role ${PANGOLIN_POSTGRES_USER}"
  "${PSQL_CONNECT[@]}" -c "CREATE ROLE \"${PANGOLIN_POSTGRES_USER}\" WITH LOGIN PASSWORD '${PANGOLIN_POSTGRES_PASSWORD}';"
else
  echo "Role ${PANGOLIN_POSTGRES_USER} already exists"
fi

# ── Pangolin database ─────────────────────────────────────────────────────────

echo "==> Checking/creating database ${PANGOLIN_POSTGRES_DB}"
DB_EXISTS=$("${PSQL_CONNECT[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname = '${PANGOLIN_POSTGRES_DB}';")
if [ "${DB_EXISTS}" != "1" ]; then
  echo "Creating database ${PANGOLIN_POSTGRES_DB} owned by ${PANGOLIN_POSTGRES_USER}"
  "${PSQL_CONNECT[@]}" -c "CREATE DATABASE \"${PANGOLIN_POSTGRES_DB}\" OWNER \"${PANGOLIN_POSTGRES_USER}\";"
else
  echo "Database ${PANGOLIN_POSTGRES_DB} already exists"
  CURRENT_OWNER=$("${PSQL_CONNECT[@]}" -tAc "SELECT pg_catalog.pg_get_userbyid(datdba) FROM pg_database WHERE datname = '${PANGOLIN_POSTGRES_DB}';" | tr -d '[:space:]')
  if [ "${CURRENT_OWNER}" != "${PANGOLIN_POSTGRES_USER}" ]; then
    echo "Setting owner of ${PANGOLIN_POSTGRES_DB} to ${PANGOLIN_POSTGRES_USER}"
    "${PSQL_CONNECT[@]}" -c "ALTER DATABASE \"${PANGOLIN_POSTGRES_DB}\" OWNER TO \"${PANGOLIN_POSTGRES_USER}\";"
  fi
fi

# ── Schema privileges ─────────────────────────────────────────────────────────

echo "==> Setting schema privileges for ${PANGOLIN_POSTGRES_USER} on ${PANGOLIN_POSTGRES_DB}"
PSQL_PANGOLIN=(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$PANGOLIN_POSTGRES_DB")
"${PSQL_PANGOLIN[@]}" -c "GRANT ALL PRIVILEGES ON DATABASE \"${PANGOLIN_POSTGRES_DB}\" TO \"${PANGOLIN_POSTGRES_USER}\";"
"${PSQL_PANGOLIN[@]}" -c "GRANT USAGE, CREATE ON SCHEMA public TO \"${PANGOLIN_POSTGRES_USER}\";"
"${PSQL_PANGOLIN[@]}" -c "ALTER SCHEMA public OWNER TO \"${PANGOLIN_POSTGRES_USER}\";"

echo "==> Done."

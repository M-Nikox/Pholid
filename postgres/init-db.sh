#!/usr/bin/env bash
set -euo pipefail

# Fail early if required env vars are missing
: "${PHOLID_POSTGRES_USER:?PHOLID_POSTGRES_USER must be set}"
: "${PHOLID_POSTGRES_PASSWORD:?PHOLID_POSTGRES_PASSWORD must be set}"
: "${PHOLID_POSTGRES_DB:?PHOLID_POSTGRES_DB must be set}"

# The initial superuser connection (postgres user → keycloak DB, as set in the compose)
PSQL_CONNECT=(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB")

# ── Pholid role ─────────────────────────────────────────────────────────────

echo "==> Checking/creating role ${PHOLID_POSTGRES_USER}"
ROLE_EXISTS=$("${PSQL_CONNECT[@]}" -tAc "SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = '${PHOLID_POSTGRES_USER}';")
if [ "${ROLE_EXISTS}" != "1" ]; then
  echo "Creating role ${PHOLID_POSTGRES_USER}"
  "${PSQL_CONNECT[@]}" -c "CREATE ROLE \"${PHOLID_POSTGRES_USER}\" WITH LOGIN PASSWORD '${PHOLID_POSTGRES_PASSWORD}';"
else
  echo "Role ${PHOLID_POSTGRES_USER} already exists"
fi

# ── Pholid database ─────────────────────────────────────────────────────────

echo "==> Checking/creating database ${PHOLID_POSTGRES_DB}"
DB_EXISTS=$("${PSQL_CONNECT[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname = '${PHOLID_POSTGRES_DB}';")
if [ "${DB_EXISTS}" != "1" ]; then
  echo "Creating database ${PHOLID_POSTGRES_DB} owned by ${PHOLID_POSTGRES_USER}"
  "${PSQL_CONNECT[@]}" -c "CREATE DATABASE \"${PHOLID_POSTGRES_DB}\" OWNER \"${PHOLID_POSTGRES_USER}\";"
else
  echo "Database ${PHOLID_POSTGRES_DB} already exists"
  CURRENT_OWNER=$("${PSQL_CONNECT[@]}" -tAc "SELECT pg_catalog.pg_get_userbyid(datdba) FROM pg_database WHERE datname = '${PHOLID_POSTGRES_DB}';" | tr -d '[:space:]')
  if [ "${CURRENT_OWNER}" != "${PHOLID_POSTGRES_USER}" ]; then
    echo "Setting owner of ${PHOLID_POSTGRES_DB} to ${PHOLID_POSTGRES_USER}"
    "${PSQL_CONNECT[@]}" -c "ALTER DATABASE \"${PHOLID_POSTGRES_DB}\" OWNER TO \"${PHOLID_POSTGRES_USER}\";"
  fi
fi

# ── Schema privileges ─────────────────────────────────────────────────────────

echo "==> Setting schema privileges for ${PHOLID_POSTGRES_USER} on ${PHOLID_POSTGRES_DB}"
PSQL_PHOLID=(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$PHOLID_POSTGRES_DB")
"${PSQL_PHOLID[@]}" -c "GRANT ALL PRIVILEGES ON DATABASE \"${PHOLID_POSTGRES_DB}\" TO \"${PHOLID_POSTGRES_USER}\";"
"${PSQL_PHOLID[@]}" -c "GRANT USAGE, CREATE ON SCHEMA public TO \"${PHOLID_POSTGRES_USER}\";"
"${PSQL_PHOLID[@]}" -c "ALTER SCHEMA public OWNER TO \"${PHOLID_POSTGRES_USER}\";"

echo "==> Done."

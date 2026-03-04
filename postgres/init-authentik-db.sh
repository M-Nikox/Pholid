#!/bin/sh
# Creates the 'authentik' database on first Postgres initialisation.
# This script is executed by the official postgres image when /var/lib/postgresql/data is empty.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE authentik OWNER $POSTGRES_USER;
EOSQL

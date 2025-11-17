#!/bin/bash
set -e

# Script para garantir que o banco e o schema existem
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    SELECT 'CREATE DATABASE clube_quinze'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'clube_quinze')\gexec
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "clube_quinze" <<-EOSQL
    CREATE SCHEMA IF NOT EXISTS clube_quinze_app;
    GRANT ALL ON SCHEMA clube_quinze_app TO $POSTGRES_USER;
    ALTER DATABASE clube_quinze SET search_path TO clube_quinze_app, public;
EOSQL

echo "Database clube_quinze e schema clube_quinze_app prontos!"


#!/bin/bash
set -e

# 建立複製用帳號
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator_pass';
EOSQL

# 允許 replicator 進行複製連線
echo "host    replication     replicator     0.0.0.0/0    md5" >> ${PGDATA}/pg_hba.conf

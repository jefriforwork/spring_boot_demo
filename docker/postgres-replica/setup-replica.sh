#!/bin/bash
set -e
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  until pg_isready -h postgres-primary -p 5432 -U postgres; do
    sleep 2
  done
  rm -rf "${PGDATA:?}"/*
  PGPASSWORD=replicator pg_basebackup \
    -h postgres-primary \
    -p 5432 \
    -U replicator \
    -D "$PGDATA" \
    -Fp -Xs -P -R
  chown -R postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"
fi
exec docker-entrypoint.sh postgres -c hot_standby=on

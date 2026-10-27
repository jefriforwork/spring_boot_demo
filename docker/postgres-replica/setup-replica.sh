#!/bin/bash
set -e

DATA_DIR="/var/lib/postgresql/data"

# 如果已經初始化過，修正權限後直接啟動
if [ -f "$DATA_DIR/standby.signal" ]; then
  echo "Replica already initialized, starting server..."
  chmod 700 "$DATA_DIR"
  exec gosu postgres postgres -c hot_standby=on -c listen_addresses='*'
fi

# 等待主庫就緒
until pg_isready -h postgres-primary -p 5432 -U postgres; do
  echo "Waiting for primary to be ready..."
  sleep 2
done

# 清空資料目錄並設定權限
rm -rf "$DATA_DIR"/*
chown postgres:postgres "$DATA_DIR"
chmod 700 "$DATA_DIR"

# 從主庫複製資料（-R 自動建立 standby.signal 和連線設定）
gosu postgres bash -c 'PGPASSWORD=replicator_pass pg_basebackup \
  -h postgres-primary \
  -p 5432 \
  -U replicator \
  -D /var/lib/postgresql/data \
  -Fp -Xs -P -R'

# 啟動 standby 模式
exec gosu postgres postgres -c hot_standby=on -c listen_addresses='*'

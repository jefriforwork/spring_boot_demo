# PostgreSQL 主從複製 + Spring Boot 讀寫分離教學

## 目錄

1. [概念：什麼是主從複製？](#1-概念什麼是主從複製)
2. [概念：什麼是讀寫分離？](#2-概念什麼是讀寫分離)
3. [Docker Compose 設定](#3-docker-compose-設定)
4. [Spring Boot 設定](#4-spring-boot-設定)
5. [運作原理：一個請求的完整旅程](#5-運作原理一個請求的完整旅程)
6. [啟動與驗證](#6-啟動與驗證)
7. [檔案結構總覽](#7-檔案結構總覽)

---

## 1. 概念：什麼是主從複製？

```
                    寫入（INSERT/UPDATE/DELETE）
                    │
                    ▼
              ┌──────────┐
              │   主庫    │  Primary
              │ port 5432 │
              └─────┬─────┘
                    │
            WAL 串流複製
            （即時同步資料）
                    │
                    ▼
              ┌──────────┐
              │   從庫    │  Replica
              │ port 5433 │
              └──────────┘
                    │
                    ▼
              唯讀（SELECT）
```

### 關鍵術語

| 術語 | 說明 |
|------|------|
| **WAL（Write-Ahead Log）** | PostgreSQL 的交易紀錄檔。每次寫入資料時，PostgreSQL 會先把「要做什麼」寫進 WAL，再實際修改資料 |
| **Streaming Replication** | 從庫持續即時讀取主庫的 WAL，把主庫的變更同步過來 |
| `wal_level=replica` | 告訴主庫「請記錄足夠的 WAL 資訊，讓從庫可以複製」 |
| `pg_basebackup` | PostgreSQL 提供的工具，把主庫的完整資料複製一份到從庫 |
| `standby.signal` | 一個空檔案。PostgreSQL 啟動時看到這個檔案，就知道自己是從庫，進入 standby 模式 |

### 為什麼需要複製？

- **效能**：讀取請求可以分散到多個從庫
- **備援**：主庫故障時，從庫可以升級為主庫
- **本教學的重點**：在同一台電腦用 Docker 模擬，理解複製和讀寫分離的原理

---

## 2. 概念：什麼是讀寫分離？

```
寫入請求（POST /books）         讀取請求（GET /books）
        │                              │
        ▼                              ▼
   走主庫 (5432)                 走從庫 (5433)
```

核心想法很簡單：

- **寫入**（INSERT、UPDATE、DELETE）→ 送到主庫
- **讀取**（SELECT）→ 送到從庫

在 Spring Boot 中，我們用 `@Transactional(readOnly = true)` 來標記「這個方法只讀不寫」，然後讓框架自動決定連哪個庫。

---

## 3. Docker Compose 設定

### 檔案：`compose.yaml`

```yaml
services:
  # ==================== 主庫 ====================
  postgres-primary:
    image: postgres:15
    container_name: postgres-primary
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: mydb

    # 主庫專用 PostgreSQL 參數
    command: >
      postgres
      -c wal_level=replica          # 開啟複製所需的 WAL 等級
      -c max_wal_senders=3          # 最多 3 個從庫同時連入
      -c max_replication_slots=3    # 最多 3 個複製插槽
      -c hot_standby=on             # 允許 standby 模式下接受查詢
      -c listen_addresses='*'       # 監聽所有網路介面

    ports:
      - "5432:5432"

    volumes:
      - pgdata_primary:/var/lib/postgresql/data
      # 掛載初始化腳本（首次啟動時執行）
      - ./docker/postgres-primary/init-primary.sh:/docker-entrypoint-initdb.d/init-primary.sh

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 10
    restart: always

  # ==================== 從庫 ====================
  postgres-replica:
    image: postgres:15
    container_name: postgres-replica
    environment:
      PGUSER: postgres

    ports:
      - "5433:5432"   # 外部用 5433 存取，內部仍是 5432

    volumes:
      - pgdata_replica:/var/lib/postgresql/data
      # 掛載自訂啟動腳本
      - ./docker/postgres-replica/setup-replica.sh:/usr/local/bin/setup-replica.sh

    # 用自訂腳本取代預設的 entrypoint
    entrypoint: ["/usr/local/bin/setup-replica.sh"]

    # 等主庫健康後才啟動
    depends_on:
      postgres-primary:
        condition: service_healthy

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 10
    restart: always

volumes:
  pgdata_primary:
  pgdata_replica:
```

### 重點解析

#### `command` vs `entrypoint` 差在哪？

| | `command` | `entrypoint` |
|---|---|---|
| **作用** | 傳參數給預設的 entrypoint | 完全取代容器的啟動指令 |
| **主庫用** | 傳 PostgreSQL 參數給官方 entrypoint | — |
| **從庫用** | — | 用自訂腳本取代（因為需要先跑 `pg_basebackup`） |

#### `ports: "5433:5432"` 是什麼意思？

```
主機端 port          容器內 port
   5433    ────→      5432
```

從庫容器內部 PostgreSQL 一樣跑在 5432，但從主機（你的 Mac）連線時要用 5433。這樣才不會跟主庫的 5432 衝突。

#### `depends_on: condition: service_healthy`

確保主庫的 `healthcheck`（`pg_isready`）通過後，從庫才會啟動。否則從庫嘗試 `pg_basebackup` 時主庫還沒準備好。

---

### 檔案：`docker/postgres-primary/init-primary.sh`

主庫**首次啟動**時自動執行（Docker postgres image 的 `/docker-entrypoint-initdb.d/` 機制）：

```bash
#!/bin/bash
set -e

# 建立專門用來複製的帳號
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator_pass';
EOSQL

# 在 pg_hba.conf 加一條規則，允許 replicator 進行複製連線
echo "host    replication     replicator     0.0.0.0/0    md5" >> ${PGDATA}/pg_hba.conf
```

**為什麼需要額外的帳號？** 安全考量。複製帳號只需要 `REPLICATION` 權限，不應該用 postgres 超級使用者。

### 檔案：`docker/postgres-replica/setup-replica.sh`

從庫的啟動腳本：

```bash
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

# 從主庫複製完整資料（-R 自動建立 standby.signal）
gosu postgres bash -c 'PGPASSWORD=replicator_pass pg_basebackup \
  -h postgres-primary \
  -p 5432 \
  -U replicator \
  -D /var/lib/postgresql/data \
  -Fp -Xs -P -R'

# 啟動 standby 模式
exec gosu postgres postgres -c hot_standby=on -c listen_addresses='*'
```

#### 關鍵參數解析

| 參數 | 說明 |
|------|------|
| `pg_basebackup` | 從主庫完整複製資料目錄的工具 |
| `-h postgres-primary` | 主庫的主機名（Docker 網路內的服務名） |
| `-U replicator` | 用剛建立的複製帳號連線 |
| `-R` | **關鍵**！自動建立 `standby.signal` 和連線設定，讓這個 PostgreSQL 以從庫模式啟動 |
| `gosu postgres` | 切換到 postgres 使用者（PostgreSQL 不允許 root 啟動） |
| `exec` | 把 postgres 進程替換掉當前 shell，讓它成為 PID 1（Docker 信號處理需要） |

---

## 4. Spring Boot 設定

### 4.1 `build.gradle` — 移除自動 Docker 管理

```groovy
// 移除這行
// developmentOnly 'org.springframework.boot:spring-boot-docker-compose'
```

**為什麼？** 這個依賴會讓 Spring Boot 自動讀 compose.yaml、自動建立一個 DataSource。但我們需要兩個 DataSource（主+從），所以要移除它，改為手動建立。

### 4.2 `application.properties` — 雙資料源設定

```properties
# 關閉自動管理
spring.docker.compose.enabled=false

# 主庫（寫入）— 用自訂前綴 app.datasource.primary
app.datasource.primary.url=jdbc:postgresql://localhost:5432/mydb
app.datasource.primary.username=postgres
app.datasource.primary.password=postgres
app.datasource.primary.driver-class-name=org.postgresql.Driver

# 從庫（讀取）— 用自訂前綴 app.datasource.replica
app.datasource.replica.url=jdbc:postgresql://localhost:5433/mydb
app.datasource.replica.username=postgres
app.datasource.replica.password=postgres
app.datasource.replica.driver-class-name=org.postgresql.Driver

# Flyway 只指向主庫（從庫是唯讀的，不能寫 flyway_schema_history）
spring.flyway.url=jdbc:postgresql://localhost:5432/mydb
spring.flyway.user=postgres
spring.flyway.password=postgres
```

**為什麼用 `app.datasource.*` 而不是 `spring.datasource.*`？**

Spring Boot 看到 `spring.datasource.*` 就會自動建立 DataSource。用自訂前綴可以避免自動建立，讓我們在 Java 程式碼裡手動控制。

### 4.3 `DemoApplication.java` — 排除自動設定

```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,     // 不要自動建立 DataSource
    HibernateJpaAutoConfiguration.class    // 不要自動設定 Hibernate
})
```

跟 `application.properties` 同一個原因：我們要手動建立，所以告訴 Spring Boot「不要自動幫我做」。

### 4.4 `DbContextHolder.java` — 執行緒安全的標記

```java
public class DbContextHolder {

    // ThreadLocal：每個執行緒有自己的一份副本，互不干擾
    private static final ThreadLocal<Boolean> READ_ONLY = ThreadLocal.withInitial(() -> false);

    public static void setReadOnly(boolean readOnly) {
        READ_ONLY.set(readOnly);
    }

    public static boolean isReadOnly() {
        return READ_ONLY.get();
    }

    public static void clear() {
        READ_ONLY.remove();
    }
}
```

**為什麼需要 ThreadLocal？**

Web 應用同時處理多個請求，每個請求在不同的執行緒上。ThreadLocal 確保「請求 A 的 readOnly 標記」不會影響「請求 B」。

```
執行緒 1（POST /books）  → READ_ONLY = false → 主庫
執行緒 2（GET /books）   → READ_ONLY = true  → 從庫
執行緒 3（GET /books）   → READ_ONLY = true  → 從庫
```

### 4.5 `RoutingDataSource.java` — 路由決策

```java
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DbContextHolder.isReadOnly()
            ? "replica"    // readOnly → 從庫
            : "primary";   // 寫入    → 主庫
    }
}
```

`AbstractRoutingDataSource` 是 Spring 內建的類別。它管理多個真實的 DataSource，每次需要連線時，呼叫 `determineCurrentLookupKey()` 來決定用哪一個。

```
RoutingDataSource
  ├── "primary"  → HikariPool-1 (port 5432)
  └── "replica"  → HikariPool-2 (port 5433)
```

### 4.6 `DataSourceConfig.java` — 手動建立所有 Bean

這是最核心的設定類別。逐一解析每個方法：

#### (a) 讀取設定檔

```java
@Bean
@ConfigurationProperties("app.datasource.primary")    // 綁定 application.properties
public DataSourceProperties primaryDataSourceProperties() {
    return new DataSourceProperties();
}
```

`@ConfigurationProperties` 會把 `app.datasource.primary.*` 的值自動對應到 `DataSourceProperties` 物件的欄位。

#### (b) 建立真實的 DataSource

```java
@Bean
public DataSource primaryDataSource() {
    return primaryDataSourceProperties()
        .initializeDataSourceBuilder()
        .type(HikariDataSource.class)    // 使用 HikariCP 連線池
        .build();
}
```

每個 DataSource 底下是一個 **HikariCP 連線池**。HikariCP 預先建立多條資料庫連線，需要時直接取用，用完歸還，避免頻繁建立/關閉連線的開銷。

#### (c) 建立路由 DataSource

```java
@Bean
@Primary    // 告訴 Spring：有多个 DataSource Bean 時，優先注入這個
public DataSource dataSource() {
    AbstractRoutingDataSource routingDataSource = new RoutingDataSource();

    Map<Object, Object> targetDataSources = new HashMap<>();
    targetDataSources.put("primary", primaryDataSource());
    targetDataSources.put("replica", replicaDataSource());

    routingDataSource.setTargetDataSources(targetDataSources);
    routingDataSource.setDefaultTargetDataSource(primaryDataSource());

    return routingDataSource;
}
```

```
               RoutingDataSource（@Primary）
                    │
        ┌───────────┼───────────┐
        │                       │
   "primary"               "replica"
        │                       │
  HikariPool-1           HikariPool-2
   (5432)                 (5433)
```

#### (d) 自訂 TransactionManager — 最關鍵的部分

```java
@Bean
public PlatformTransactionManager transactionManager(
        LocalContainerEntityManagerFactoryBean entityManagerFactory) {
    JpaTransactionManager txManager = new JpaTransactionManager() {

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 在取得連線之前，就設定好 readOnly 標記
            DbContextHolder.setReadOnly(definition.isReadOnly());
            super.doBegin(transaction, definition);
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            super.doCleanupAfterCompletion(transaction);
            // 清除標記，避免影響下一個請求
            DbContextHolder.clear();
        }
    };
    txManager.setEntityManagerFactory(entityManagerFactory.getObject());
    return txManager;
}
```

**為什麼要覆寫 `doBegin`？**

Spring 的事務管理時序：

```
TransactionManager.getTransaction()
  │
  ├─ 1. doBegin()         ← 取得資料庫連線（此時要決定連哪個庫）
  │
  └─ 2. prepareSynchronization()  ← 設定 readOnly 標記（但已經太晚了）
```

如果我們在 `determineCurrentLookupKey()` 裡用 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`，在步驟 1 時它還是 `false`（步驟 2 還沒執行）。所以所有請求都會走到主庫。

覆寫 `doBegin` 的目的：在步驟 1 之前就從 `TransactionDefinition`（Spring 傳進來的事務定義）讀取 `readOnly`，設定到我們自己的 `DbContextHolder`。

### 4.7 `BookService.java` — 標記讀寫

```java
@Transactional                        // 寫入事務 → DbContextHolder = false → 主庫
public Map<String, Object> createBook(String name, String author) { ... }

@Transactional(readOnly = true)        // 只讀事務 → DbContextHolder = true → 從庫
public Map<String, Object> getAllBooks() { ... }
```

`@Transactional` 告訴 Spring：
- 這個方法需要在資料庫事務中執行
- `readOnly = true` 表示這個事務不會修改資料

Spring 會把這個 `readOnly` 值放進 `TransactionDefinition`，最終傳到我們的 `doBegin()`。

---

## 5. 運作原理：一個請求的完整旅程

### 寫入請求（POST /books）

```
1. 使用者發送 POST /books?name=Test&author=Author

2. DispatcherServlet 收到請求，路由到 BookController.createBook()

3. BookController 呼叫 BookService.createBook()

4. Spring 的 TransactionInterceptor 攔截（因為 @Transactional）
   │
   ├─ TransactionManager.doBegin()
   │   ├─ definition.isReadOnly() = false
   │   ├─ DbContextHolder.setReadOnly(false)     ← 我們的覆寫
   │   └─ 從 DataSource 取得連線
   │       └─ RoutingDataSource.determineCurrentLookupKey()
   │           └─ DbContextHolder.isReadOnly() = false
   │               └─ 回傳 "primary"
   │                   └─ 從 HikariPool-1 取得連線 (port 5432)
   │
   ├─ 執行 createBook() 內容
   │   └─ INSERT INTO books ...
   │
   └─ TransactionManager.doCleanupAfterCompletion()
       └─ DbContextHolder.clear()

5. 回傳 {"dataSource": "primary (port 5432)", "data": {...}}
```

### 讀取請求（GET /books）

```
1. 使用者發送 GET /books

2. DispatcherServlet 收到請求，路由到 BookController.getAllBooks()

3. BookController 呼叫 BookService.getAllBooks()

4. Spring 的 TransactionInterceptor 攔截（因為 @Transactional(readOnly = true)）
   │
   ├─ TransactionManager.doBegin()
   │   ├─ definition.isReadOnly() = true
   │   ├─ DbContextHolder.setReadOnly(true)      ← 我們的覆寫
   │   └─ 從 DataSource 取得連線
   │       └─ RoutingDataSource.determineCurrentLookupKey()
   │           └─ DbContextHolder.isReadOnly() = true
   │               └─ 回傳 "replica"
   │                   └─ 從 HikariPool-2 取得連線 (port 5433)
   │
   ├─ 執行 getAllBooks() 內容
   │   └─ SELECT * FROM books
   │
   └─ TransactionManager.doCleanupAfterCompletion()
       └─ DbContextHolder.clear()

5. 回傳 {"dataSource": "replica (port 5433)", "data": [...]}
```

---

## 6. 啟動與驗證

### 啟動

```bash
# 1. 啟動主從資料庫
docker compose up -d

# 2. 等兩個容器都 healthy
docker ps --filter name=postgres

# 3. 啟動 Spring Boot
./gradlew bootRun
```

### 驗證複製狀態

```bash
# 查看主庫的複製連線（應該看到一條 streaming 的連線）
docker exec postgres-primary psql -U postgres -c "SELECT client_addr, state, sent_lsn, write_lsn FROM pg_stat_replication;"
```

預期輸出：
```
 client_addr |   state   | sent_lsn  | write_lsn
-------------+-----------+-----------+-----------
 172.18.0.3  | streaming | 0/30394B8 | 0/30394B8
```

### 驗證讀寫分離

```bash
# 寫入（應顯示 primary）
curl -s -X POST "http://localhost:8080/books?name=Test&author=Author" | python3 -m json.tool

# 讀取（應顯示 replica）
curl -s "http://localhost:8080/books" | python3 -m json.tool
```

### 進階驗證：停庫測試

```bash
# 停掉主庫 → 讀取應該仍然成功（走從庫）
docker stop postgres-primary
curl -s "http://localhost:8080/books"

# 停掉主庫 → 寫入應該失敗
curl -s -X POST "http://localhost:8080/books?name=Fail&author=Test"

# 恢復
docker start postgres-primary
```

### 重置（完全重新開始）

```bash
# 停掉所有容器並刪除資料 volumes
docker compose down -v
docker compose up -d
```

---

## 7. 檔案結構總覽

```
demo/
├── compose.yaml                          # Docker 主從設定
├── build.gradle                          # 移除了 spring-boot-docker-compose
├── docker/
│   ├── postgres-primary/
│   │   └── init-primary.sh               # 主庫初始化（建立複製帳號）
│   └── postgres-replica/
│       └── setup-replica.sh              # 從庫啟動（pg_basebackup）
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java              # 排除 DataSource/Hibernate 自動設定
│   ├── config/
│   │   ├── DbContextHolder.java          # ThreadLocal 標記（readOnly or not）
│   │   ├── RoutingDataSource.java        # 根據標記決定連哪個庫
│   │   └── DataSourceConfig.java         # 手動建立主/從 DataSource + 路由
│   ├── entity/Book.java
│   ├── repository/BookRepository.java
│   ├── service/BookService.java          # @Transactional(readOnly=true/false)
│   └── controller/BookController.java
└── src/main/resources/
    ├── application.properties            # 雙資料源 + Flyway 指向主庫
    └── db/migration/V1__create_books_table.sql
```

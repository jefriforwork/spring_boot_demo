# demo

Spring Boot 書籍與作者 REST API。寫入走 PostgreSQL 主庫，讀取走從庫，單筆書籍查詢用 Redis 快取，書籍建立與刪除會發 Kafka 事件。

主從複製與讀寫分離的逐步說明見 [TUTORIAL.md](TUTORIAL.md)。

## 技術

| 項目 | 版本 / 說明 |
|------|-------------|
| Java | 17 |
| Spring Boot | 4.0.6 |
| Gradle | 9.5.1（Wrapper） |
| PostgreSQL | 15，主庫 `5432`、從庫 `5433` |
| Redis | 7，快取 `books`，TTL 10 分鐘 |
| Kafka | Confluent 7.5.0，topic `book-events` |
| Flyway | schema 遷移只對主庫執行 |

## 功能

- 作者與書籍 CRUD（作者只有建立與查詢）
- 書籍名稱唯一；書名重複回 `409`，找不到資源回 `404`，欄位驗證失敗回 `400`
- `@Transactional(readOnly = true)` 的查詢路由到從庫
- `GET /books/{id}` 結果快取在 Redis；寫入或刪除會清掉 `books` 快取
- 建立書籍送出 `新書上架`，刪除書籍送出 `書本已刪除`，consumer 印到主控台
- 書籍列表支援分頁、排序，以及依作者國家、書名關鍵字過濾

## 需求

- JDK 17
- Docker 與 Docker Compose

## 啟動

先起資料庫、快取與訊息佇列：

```bash
docker compose up -d
```

等主庫健康檢查通過後，從庫才會用 `pg_basebackup` 接上串流複製。確認容器都在跑：

```bash
docker compose ps
```

再啟動應用（預設 `http://localhost:8080`）：

```bash
./gradlew bootRun
```

測試：

```bash
./gradlew test
```

停止基礎服務：

```bash
docker compose down
```

若要連資料卷一併清掉（主從都要重建）：

```bash
docker compose down -v
```

## 連線（本機示範）

| 服務 | 位址 | 帳號 / 密碼 |
|------|------|-------------|
| PostgreSQL 主庫 | `localhost:5432` / `mydb` | `postgres` / `postgres` |
| PostgreSQL 從庫 | `localhost:5433` / `mydb` | `postgres` / `postgres` |
| Redis | `localhost:6379` | 無 |
| Kafka | `localhost:9092` | — |

設定在 `src/main/resources/application.properties`。`spring.docker.compose.enabled=false`，應用不會自己拉起 Compose。

## API

回應裡的 `dataSource` 標出這次請求走主庫還是從庫。

### 作者

```bash
curl -s -X POST http://localhost:8080/authors \
  -H 'Content-Type: application/json' \
  -d '{"name":"村上春樹","country":"Japan"}'

curl -s http://localhost:8080/authors
curl -s http://localhost:8080/authors/1
```

| 方法 | 路徑 | 說明 |
|------|------|------|
| `POST` | `/authors` | 建立。body：`name`、`country`（皆必填，最長 255） |
| `GET` | `/authors` | 全部作者 |
| `GET` | `/authors/{id}` | 單筆 |

### 書籍

先有作者，再建立書籍（`authorId` 必填）。

```bash
curl -s -X POST http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -d '{"name":"挪威的森林","authorId":1}'

curl -s 'http://localhost:8080/books?page=0&size=10&sort=id,asc'
curl -s 'http://localhost:8080/books?country=Japan&keyword=森林'
curl -s http://localhost:8080/books/1

curl -s -X PUT http://localhost:8080/books/1 \
  -H 'Content-Type: application/json' \
  -d '{"name":"海邊的卡夫卡","authorId":1}'

curl -s -X DELETE http://localhost:8080/books/1
```

| 方法 | 路徑 | 說明 |
|------|------|------|
| `POST` | `/books` | 建立，並送 Kafka `book-events` |
| `GET` | `/books` | 分頁列表。`page`（預設 0）、`size`（預設 10）、`sort`（預設 `id,asc`）、選填 `country`、`keyword` |
| `GET` | `/books/{id}` | 單筆，Redis 快取 |
| `PUT` | `/books/{id}` | 更新書名與作者 |
| `DELETE` | `/books/{id}` | 刪除，並送 Kafka `book-events` |

列表回應含 `pagination`：`page`、`size`、`totalElements`、`totalPages`、`first`、`last`。

## 資料模型

Flyway 遷移在 `src/main/resources/db/migration`：

1. `V1` 建立 `books`（含字串欄位 `author`）
2. `V2` 書名與作者改為 `NOT NULL`，書名唯一
3. `V3` 建立 `authors`，把既有書的作者名稱遷成外鍵 `books.author_id`，再刪掉 `books.author`

```
authors (id, name, country)
   ▲
   │ author_id
books (id, name)
```

## 專案結構

```
compose.yaml                          PostgreSQL 主從、Redis、Zookeeper、Kafka
docker/postgres-primary/init-primary.sh
docker/postgres-replica/setup-replica.sh
src/main/java/com/example/demo/
  controller/                         /books、/authors
  service/                            交易、快取、Kafka 發送
  config/                             讀寫分離資料源、Redis
  kafka/                              producer、consumer
  entity/  repository/  dto/
  exception/
src/main/resources/
  application.properties
  db/migration/
```

讀取路由：`RoutingDataSource` 看 `DbContextHolder`。唯讀交易走從庫（5433），其餘走主庫（5432）。Flyway 只連主庫，變更經 WAL 串流到從庫。

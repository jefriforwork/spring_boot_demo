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

等主庫健康檢查通過後，從庫才會用 `pg_basebackup` 接上串流複製。你下這條指令的當下，會一併啟動 ZooKeeper（`2181`）和 Kafka（`9092`）。指令結束後 Compose 就不再看著它們；Kafka 若自己退出，不會自動再拉起來。確認容器都在跑：

```bash
docker compose ps
```

`kafka` 或 `zookeeper` 顯示 `Exited` 時，應用不會把它們拉起來。只啟動這兩個：

```bash
docker compose start zookeeper kafka
```

再啟動應用（預設 `http://localhost:8080`）：

```bash
./gradlew bootRun
```

測試：

```bash
./gradlew test
```

## 停止

先停應用，再停容器。`./gradlew bootRun` 佔著那個終端機，在**同一個視窗**按 `Ctrl+C`，等到 Gradle 印出 `BUILD SUCCESSFUL`（或行程結束）再繼續。只執行 `docker compose down` 不會關掉 8080 上的 Spring Boot。

容器（PostgreSQL、Redis、Kafka、ZooKeeper）：

```bash
docker compose down
```

若要連資料卷一併清掉（主從都要重建）：

```bash
docker compose down -v
```

終端機已經關掉、`Ctrl+C` 按不到時，查出佔用 8080 的行程再停掉：

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
kill <PID>
```

`<PID>` 換成上一行輸出的行程編號。停不掉再改用 `kill -9 <PID>`。

## Kafka 沒起來

建立書籍和刪除書籍會送 topic `book-events`。`9092` 沒有在聽時，請求會空等約 60 秒，然後回 500。`GET /books` 不送 Kafka。

看容器狀態與日誌：

```bash
docker compose ps
docker logs kafka --tail 50
```

日誌若出現 `NodeExists` 或 `/brokers/ids/1`，ZooKeeper 裡還留著舊的 broker 註冊。先重啟 ZooKeeper 清掉那筆註冊，再啟動 Kafka：

```bash
docker restart zookeeper
docker start kafka
```

`docker ps` 裡 `kafka` 是 `Up`，且本機能連上 `localhost:9092`，再重新建立書籍。

## Debug

一般的 `./gradlew bootRun` 不會停在 breakpoint。要改用 debug 模式，讓 debugger 接上 JVM 的 `5005`。

1. 在跑 `bootRun` 的終端機按 `Ctrl+C`，先停掉現在的應用。
2. 在編輯器裡點行號左側設 breakpoint。例如 `BookService.createBook` 裡的 `kafkaProducer.send(...)`。
3. 用 debug 模式啟動。行程會先停住，等到 debugger 接上才繼續：

```bash
./gradlew bootRun --debug-jvm
```

4. 終端機出現 `Listening for transport dt_socket at address: 5005` 後，在 Cursor 或 IntelliJ 新增 **Attach** 設定，主機 `localhost`、埠 `5005`，再開始偵錯。
5. 對 `http://localhost:8080` 發請求。執行到 breakpoint 會停下，可以看變數、單步執行。
6. 停止方式和一般啟動相同：在 `bootRun` 的終端機按 `Ctrl+C`。

## 連線（本機示範）

| 服務 | `./gradlew bootRun`（Compose） | 叢集裡的 API Pod |
|------|------|-------------|
| PostgreSQL 主庫 | `localhost:5432` / `mydb` | `postgres-primary:5432` / `mydb` |
| PostgreSQL 從庫 | `localhost:5433` / `mydb` | `postgres-replica:5432` / `mydb` |
| Redis | `localhost:6379` | `redis:6379` |
| Kafka | `localhost:9092` | `kafka-0.kafka.default.svc.cluster.local:9092` |
| Mailpit | `localhost:1025`，看信頁 `localhost:8025` | `mailpit:1025`，看信頁 `localhost:30025` |

帳號密碼兩邊都是 `postgres` / `postgres`。Redis 與 Kafka 沒有帳號。`bootRun` 的位址在 `application.properties`。叢集那一欄在 `k8s/demo.yaml` 的 ConfigMap，會蓋過 Pod 裡的同一組設定。`spring.docker.compose.enabled=false`，應用不會自己拉起 Compose。

## 在本機 Kubernetes 跑整份系統

這條路徑把 API、PostgreSQL、Redis、Kafka、Mailpit 都放進叢集，彼此用 Service 名字連線，比較接近雲端。雲端的節點沒有 Docker Desktop，也沒有 `host.docker.internal`。

仍需要 Docker Desktop，並在設定裡打開 Kubernetes。它在這裡只是這台 Mac 上的小型叢集。`compose.yaml` 留著給 `./gradlew bootRun`：那條路徑還是連 `localhost` 上的埠，資料庫是另一份，和叢集裡的資料不互通。

叢集裡每個程式是一個 Pod（Kubernetes 拿來跑 container 的盒子）。Pod 會重開、IP 會變。Service 是固定名字，例如 `postgres-primary`、`kafka`。API 的設定在 `k8s/demo.yaml` 的 ConfigMap，連的就是這些名字。

| 誰 | Service 名字 | 叢集內的埠 | 你在 Mac 上怎麼碰到 |
|---|---|---|---|
| API | `demo` | `8080` | `http://localhost:30080` |
| PostgreSQL 主庫 | `postgres-primary` | `5432` | 只給叢集裡的 Pod |
| PostgreSQL 從庫 | `postgres-replica` | `5432` | 只給叢集裡的 Pod。本機 Compose 的從庫才是 `5433` |
| Redis | `redis` | `6379` | 只給叢集裡的 Pod |
| Kafka | Service 名字是 `kafka`。API 連 `kafka-0.kafka.default.svc.cluster.local` | `9092` | 只給叢集裡的 Pod。`kafka-0` 是這組 Pod 裡第一個的固定名字 |
| Mailpit 看信頁 | `mailpit` | `8025` | `http://localhost:30025` |

先打包 API 映像檔。名稱要和 `k8s/demo.yaml` 裡的 `image: demo:0.0.1` 一致。PostgreSQL、Redis、Kafka、Mailpit 的映像檔會由叢集從 Docker Hub 拉取，不用在這一步建。

```bash
docker build -t demo:0.0.1 .
kubectl apply -f k8s/dependencies.yaml
kubectl apply -f k8s/demo.yaml
kubectl rollout status statefulset/postgres-primary
kubectl rollout status deployment/demo
```

第一次會下載映像檔，從庫還要等主庫就緒後做一次基礎備份。API 的 Pod 會等到主庫、從庫、Redis、Kafka、Mailpit 的埠都開了才啟動 Spring Boot。

```bash
curl -s http://localhost:30080/actuator/health/readiness
curl -s http://localhost:30080/api/v1/books
```

`30080` 和 `./gradlew bootRun` 的 `8080` 不同，兩個可以同時開，但寫進去的是兩份資料庫。叢集裡 API 的副本數是 1，因為 outbox 排程還沒有跨副本的鎖，多開會重複送 Kafka。回應裡的 `dataSource` 仍會寫 `port 5433` / `port 5432`，那是程式裡為本機 Compose 寫死的標籤；叢集裡從庫的 Service 埠是 `5432`。

拆掉叢集裡的 Pod 和 Service。主庫、從庫的磁碟不會跟著檔案一起刪，資料還在。要連資料清掉時，再刪那兩個 PersistentVolumeClaim（磁碟的申請單）：

```bash
kubectl delete -f k8s/demo.yaml
kubectl delete -f k8s/dependencies.yaml
kubectl delete pvc data-postgres-primary-0 data-postgres-replica-0
```

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
Dockerfile                            把 API 打成映像檔 demo:0.0.1
k8s/dependencies.yaml                叢集裡的 PostgreSQL、Redis、ZooKeeper、Kafka、Mailpit
k8s/demo.yaml                         API 的 Deployment、Service、ConfigMap、Secret
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

讀取路由：`RoutingDataSource` 看 `DbContextHolder`。唯讀交易走從庫，其餘走主庫。本機 Compose 把從庫公布在 `5433`，主庫在 `5432`。叢集裡兩個 Service 的埠都是 `5432`，靠名字 `postgres-replica` 和 `postgres-primary` 分開。Flyway 只連主庫，變更經 WAL 串流到從庫。

# Spring Boot：一個請求怎麼變成回應

複習用筆記。一個 HTTP 請求要被處理，最後一定打到 container 裡的 bean。Bean 可以用不同方式註冊進來。進 container 之後，每個請求只剩一條路。Kafka 和 Redis 接在這條路的 service 旁邊，記在文末。

`new` 出來的物件不在這條路上。請求進來時沒有人呼叫它，也沒有人幫它填依賴、開交易。

## 總流程

```text
HTTP 請求
└─ Tomcat                          starter-web 放進來的內嵌伺服器
      └─ DispatcherServlet         這顆 servlet 本身也是一個 bean
            └─ HandlerMapping      用 URL 和 HTTP method 找方法
                  └─ 參數組裝       JSON、path、query 轉成方法參數
                        └─ Controller 方法
                              └─ Service             container 注入進來的那個物件
                                    └─ Repository    介面的 proxy，不是你寫的類別
                                          └─ EntityManager
                                                └─ DataSource → 資料庫

回傳的物件
└─ HttpMessageConverter            通常是 Jackson
      └─ HTTP 回應
```

方法丟出例外時，正常回傳值這條路停掉。`@RestControllerAdvice` 接住例外，改寫狀態碼和 body。

三條註冊 bean 的路，差別只在「誰把物件交給 container」。註冊完之後，注入和請求分發是同一條路。Controller 不在乎 `BookService` 是掃描出來的，還是 `@Bean` 方法建立的。它的建構子只拿到 container 給的那一個物件。

## 各層在做什麼

```text
@SpringBootApplication
└─ SpringApplication.run
      └─ ApplicationContext        這個 container 保存全部 bean
            ├─ 掃描 @Component、@Service、@Repository、@RestController、@Configuration
            ├─ 執行 @Bean 方法
            └─ 執行 auto-configuration
                  └─ 依賴關係在建立時填好
                        └─ 請求來了才呼叫方法
```

- **Controller** 接 HTTP。它把路徑、query、JSON 收成方法參數，呼叫 service，把回傳值交回去。它不開交易，也不寫 SQL。
- **Service** 放這次操作的步驟。`@Transactional` 標在這裡。交易由包在 service 外面的 proxy 開啟，不是標註自己開啟。
- **Repository** 是介面。Spring Data 在啟動時做出這個介面的 proxy，成為一個 bean。方法名稱會被解析成查詢。
- **Entity** 是資料列在記憶體裡的物件。它不是 bean。每次查詢得到自己的一份。container 裡不會有一顆叫做某本書的 singleton。
- **DTO**（例如 `BookRequest`）是跨過一層邊界時用的資料形狀。它不是 bean。Jackson 每個請求新做一個。
- **Repository** 就是以前叫做 DAO 的那一層。這個專案沒有另外一顆 `BookDao`。

Bean 是 container 管理的物件。一顆 bean 服務很多請求。請求自己的資料放在方法參數和區域變數，不放在 bean 的欄位。

## Entity、DTO，還有 DAO 去哪了

這三個名字說的是物件的角色，不是 Spring 的三種 bean。Entity 和 DTO 每個請求各做各的。Repository 才是 bean，全程式共用一顆 proxy。

```text
POST /books   JSON { name, authorId }
└─ BookRequest                 DTO。這次請求的輸入形狀
      └─ BookService
            ├─ 用 authorId 查出 Author
            └─ new Book(name, author)    Entity。準備變成 books 表的一列
                  └─ BookRepository      以前這層叫 DAO
                        └─ INSERT
```

### Entity

`Book` 上的 `@Entity` 告訴 Hibernate：這個類別對應一張表。`@Table(name = "books")` 寫明表名。`@Id` 是主鍵。`@ManyToOne` 是 `author_id` 那條外鍵，欄位型別是另一個 entity `Author`，不是一個數字。

```java
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;
}
```

交易裡的 `Book` 被 persistence context 追蹤。改 setter，提交時可以變成 UPDATE。這是 entity 才有的行為。一個普通 Java 物件沒有人在交易結束時幫它寫回資料庫。

Entity 的形狀跟資料庫走。表上有什麼關聯，類別裡就有對應欄位。它知道 `Author` 這整個物件，也知道這條關聯是 LAZY。

### DTO

DTO（Data Transfer Object）是拿來搬資料的物件。搬的那一段結束，它的工作就結束。沒有 persistence context 追蹤它，也沒有表跟它對應。

`BookRequest` 是進來的 JSON 要長成的樣子。客戶端交得出 `name` 和 `authorId`。它交不出一個被 Hibernate 管著的 `Author`。

```java
public class BookRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    @NotNull(message = "authorId must not be null")
    private Long authorId;
}
```

`@NotBlank`、`@NotNull` 是這次輸入的規則，在進 controller 方法之前檢查。Entity 上的 `@JoinColumn(nullable = false)` 是資料庫那一列的規則，在 flush 時才會碰到。兩套規則各管自己的邊界。

Service 站在中間，把 DTO 換成 entity：用 `authorId` 查出 `Author`，再 `new Book(name, author)`。這個 `new` 是對的。`Book` 不該是 singleton bean。

回程這個 demo 沒有另寫一個 `BookResponse` 類別，直接回 `Map<String, Object>`。那份 map 扮演的是出去的 DTO：回應要帶的欄位在組 map 時決定。Entity 的 LAZY `author` 會不會被序列化進去，取決於 map 裡放了什麼。

### DAO 就是 Repository 這層

DAO（Data Access Object）是「只有這一層會下 SQL」的物件。早期 Spring 會寫：

```java
public class BookDao {
    public void insert(Book book) { /* JDBC */ }
    public Book find(Long id) { /* JDBC */ }
}
```

Spring Data 把這層的介面叫做 Repository，實作由 proxy 生成。`BookRepository` 做的就是 `BookDao` 的工作：`save`、`findById`、`existsByName`。專案裡再放一個 `BookDao`，會變成兩層做同一件事。

`@Repository` 這個註解還在。它標的是這一層的 bean，名字沿用 DAO 那個時代。介面繼承 `JpaRepository` 時，Spring Data 已經把它註冊成 bean，類別上通常不必再寫一次 `@Repository`。

```text
Controller → Service → DAO → JDBC          早期的名字
Controller → Service → Repository → JPA    這個專案的名字
```

兩行是同一條路。差在 Repository 的方法本體不用自己寫。

### 旁邊還會看到的名字

| 名字 | 它是什麼 | 在這個 demo |
| --- | --- | --- |
| POJO | 普通 Java 類別。沒有規定它要繼承框架的類別 | `Book`、`BookRequest` 都是 |
| Request DTO | 進來的資料形狀 | `BookRequest`、`AuthorRequest` |
| Response DTO | 出去的資料形狀 | 沒有專用類別，用 `Map<String, Object>` |
| Entity | 一列資料，有身份（`id`），交易裡會被追蹤 | `Book`、`Author` |
| Value Object | 沒有身份、靠內容判斷相不相等的值，例如一段地址、一筆金額 | 這裡沒有單獨的類別 |
| Projection | 只取出某幾個欄位的查詢結果，連整個 entity 都不組 | 這裡的查詢回傳整個 `Book` |
| Page | 一頁查詢結果，帶總筆數和頁碼 | `Page<Book>` |
| Mapper | 把 DTO 和 entity 互相轉換的那一段程式 | 寫在 `BookService` 裡，沒有另開類別 |

Value Object 和「畫面用的物件」常被縮寫成同一個 VO。前者沒有 `id`，後者只是 response DTO 的另一個叫法。看代碼時以類別實際做的事為準。

有的專案會再把「業務物件」和 entity 拆開：entity 只對表，業務規則放在另一個沒有 `@Entity` 的類別。這個 demo 的業務規則直接寫在 `BookService`，資料就用 `Book`。中間沒有第三個模型。

## 為什麼不用 new

`new BookService(...)` 可以得到一個物件。已經在跑的應用程式不會把請求交給它。

```text
啟動
└─ container 建立 BookService，注入 repository，包上 proxy
      └─ 放進 container
            └─ BookController 的建構子拿到這一個

請求
└─ DispatcherServlet 呼叫 container 裡的 BookController
      └─ controller 裡那個 bookService 欄位
            └─ 就是上面那一個
```

另外寫 `new BookService(repo, authorRepo, producer)` 時，要自己找齊每一個依賴。那些依賴若也是 `new` 的，交易 proxy、`@Cacheable`、repository proxy 都不會出現。HTTP 請求仍然打到 container 裡原來的 controller，打不到這個新物件。

`new` 適合請求內部的短期物件：一個 `Book`、一個 `BookRequest`、一個 `HashMap`。要被別的請求、別的 bean 呼叫的物件，交給 container。

只有這次請求用的資料，放方法參數。啟動後就固定、大家都讀的設定，放 `@ConfigurationProperties`。設定在啟動時綁進那顆 bean，執行中改 properties 檔不會自動重綁。

## Bean 的三條進場路

### 1. Classpath 掃描

`@SpringBootApplication` 包含 `@ComponentScan`。掃描範圍是應用程式類別所在的套件，以及它下面的套件。`com.example.demo.DemoApplication` 掃得到 `com.example.demo.service.BookService`。放在 `com.example.other` 的 `@Service` 不會被掃到，container 裡沒有這顆 bean。

這些註解都會被掃到。差別是額外的意思，不是另一種 container：

| 註解 | 額外的意思 |
| --- | --- |
| `@Component` | 這是一顆 bean |
| `@Service` | 同上，標在業務這層 |
| `@Repository` | 同上，再加上把持久層例外轉成 Spring 的 `DataAccessException` |
| `@RestController` | `@Controller` 加上 `@ResponseBody`。回傳值交給 Jackson，不找畫面模板 |
| `@Configuration` | 同上，而且裡面的 `@Bean` 方法會被 proxy 攔截 |

掃到類別之後，container 看建構子的參數型別，先把那些 bean 做出來，再呼叫建構子。

### 2. `@Bean` 方法

物件的建立步驟不適合寫在一個建構子裡時，用 `@Configuration` 類別裡的 `@Bean` 方法。方法回傳值放進 container，方法名稱就是 bean 的預設名稱。

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        // 組出 RoutingDataSource，回傳值成為 container 裡的 DataSource
        return routingDataSource;
    }
}
```

`@Primary` 解決「同一個介面有多顆 bean」。注入 `DataSource` 時，container 選有 `@Primary` 的那顆。沒有 `@Primary` 又沒有 `@Qualifier`，啟動失敗，錯誤是 `NoUniqueBeanDefinitionException`。

### 3. Auto-configuration

Starter 把類別放上 classpath，也宣告一組設定類別。啟動時 container 讀這些設定類別。類別上的條件成立，裡面的 `@Bean` 才執行。

條件看的是 classpath 和設定，例如「有 Tomcat 的類別才建立 web server」「有 `DataSource` 才建立交易管理器」。條件不成立就跳過，通常不報錯。

這三條路交出來的都是 container 裡的 bean。Controller 注入 `BookService` 時，拿到的是其中一條路註冊好的那顆。

## 注入發生在建立的時候

依賴在 bean 建立時填進建構子。請求進來時不再找一次。

```java
@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }
}
```

只有一個建構子時，不必寫 `@Autowired`。container 會用它。

建立順序是依賴的順序。`BookController` 要 `BookService`，`BookService` 要 `BookRepository`。Repository proxy 先做好，再呼叫 `BookService` 的建構子，最後才呼叫 `BookController` 的建構子。

建構子互相需要時，兩顆都無法先建立完。Spring Boot 預設不允許這種循環，啟動直接失敗。

欄位上的 `@Autowired` 是建構子跑完之後才填。物件可以先以「依賴還沒填」的狀態存在，循環依賴比較容易被藏起來。建構子注入在啟動時就把沒填滿的依賴露出來。

## `@SpringBootApplication` 是三件事

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

這個註解等於三個：

```text
@SpringBootApplication
├─ @Configuration              這個類別自己可以宣告 @Bean
├─ @EnableAutoConfiguration    讀 starter 裡的自動設定
└─ @ComponentScan              掃這個套件和子套件
```

`SpringApplication.run` 建立 `ApplicationContext`，跑完上面三件事，做出全部 singleton，然後啟動 Tomcat。任何一個必要 bean 建不出來，程式停在啟動，不會進入「請求來了再看看」。

`exclude` 是把某一個自動設定類別從這次啟動拿掉。這個 demo 拿掉的是自動建立 `DataSource` 和 `EntityManagerFactory` 的那兩個類別：

```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
```

拿掉之後，container 不會自己做連線和 `EntityManagerFactory`。`DataSourceConfig` 用 `@Bean` 把這兩樣建出來。後面的 repository proxy、`@Transactional` 仍然走同一條路，因為它們要的是 container 裡有一顆 `DataSource`、一顆 `EntityManagerFactory`、一顆 `PlatformTransactionManager`，不在乎這三顆來自自動設定還是自己的 `@Bean`。

## 同一個建立書籍

掃描進來的 service，和自己 `new` 的 service，方法本體可以長一樣。請求打得到的是前者。

```java
@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional
    public Book create(String name, Author author) {
        return bookRepository.save(new Book(name, author));
    }
}
```

```java
@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public Book create(@RequestBody BookRequest request) {
        return bookService.create(request.getName(), request.getAuthor());
    }
}
```

`POST /books` 進來時，DispatcherServlet 找到 `create`。Jackson 先把 body 做成 `BookRequest`。Controller 呼叫的是建構子收到的那顆 `bookService`。那顆是 proxy。Proxy 看到 `@Transactional`，開啟交易，再進真正的 `create`。`save` 打到 repository proxy，寫進目前這筆交易綁著的 `EntityManager`。方法正常返回，proxy 提交交易。Jackson 再把回傳的 `Book` 寫成 JSON。

自己 `new` 一個 `BookService` 再呼叫 `create`，`bookRepository.save` 若你有把 repository 傳進去，SQL 仍可能執行。`@Transactional` 沒有人讀。例外發生時也不會靠這層 proxy 回滾。

## Proxy：註解自己不做事

`@Transactional`、`@Cacheable`、`@Async` 都是給 proxy 讀的標記。Proxy 包住真正的物件。別的 bean 注入到的是 proxy，不是原始物件。

```text
BookController
└─ 欄位型別寫 BookService
      └─ 實際物件是 proxy          BookService$$SpringCGLIB$$0
            ├─ 讀方法上的 @Transactional、@Cacheable
            ├─ 開交易、查快取、或把工作丟到別的執行緒
            └─ 呼叫原始的 BookService 方法
```

在 debugger 裡看 `bookService.getClass()`，看到的是 CGLIB 子類別，不是 `BookService.class`。這表示注入到的是 proxy。

標記和 proxy 的分工，對這三個註解是同一件事：

| 註解 | proxy 在呼叫真正方法之前做的事 |
| --- | --- |
| `@Transactional` | 沒有交易就開一筆，有就依 propagation 加入或暫停 |
| `@Cacheable` | 用 key 查快取。命中就直接回傳，方法本體不跑 |
| `@Async` | 改到另一個執行緒跑，呼叫端立刻返回 |

### 自己呼叫自己

Proxy 只攔截從外面進來的呼叫。同一個類別裡面用 `this` 呼叫，是 Java 的普通呼叫，不會經過 proxy。

```java
@Service
public class BookService {

    @Transactional
    public Book create(String name, Author author) {
        return bookRepository.save(new Book(name, author));
    }

    public void importAll(List<BookRequest> rows) {
        for (BookRequest row : rows) {
            this.create(row.getName(), row.getAuthor());
        }
    }
}
```

外面呼叫 `importAll`，`importAll` 上沒有 `@Transactional`，整段沒有交易。`this.create(...)` 雖然寫著 `@Transactional`，註解不會被讀。`create` 從 controller 直接呼叫時，同一段程式才會被 proxy 攔到。

`@Cacheable` 和 `@Async` 一樣。`getBookById` 標了 `@Cacheable`，同一個類別裡寫 `this.getBookById(id)` 不會查那層快取。

要讓這段交易存在，把 `importAll` 自己標成 `@Transactional`，或把 `create` 留在另一顆 bean，從外面呼叫那顆 bean。

### 交易怎麼結束

Proxy 用 try/catch 包住真正的方法。

```text
proxy 呼叫 createBook
├─ 開始交易
├─ 跑方法本體
│     ├─ 正常返回          提交，回傳值交回 controller
│     ├─ RuntimeException  回滾，例外繼續往外丟
│     └─ checked exception 預設提交，例外繼續往外丟
└─ controller 沒接住的例外，交給 @RestControllerAdvice
```

回滾的預設範圍是 `RuntimeException` 和 `Error`。Checked exception 預設提交。要連 checked exception 一起回滾，要寫 `rollbackFor = Exception.class`。

`DuplicateBookNameException` 繼承 `RuntimeException`。`createBook` 在 `save` 之前把它丟出來時，proxy 回滾這筆交易，例外離開 service。Controller 沒有 try/catch。`GlobalExceptionHandler` 的 `@ExceptionHandler(DuplicateBookNameException.class)` 把它寫成 409。Advice 跑的時候，回滾已經做完了。Advice 決定的是回應，不是交易。

```java
@ExceptionHandler(DuplicateBookNameException.class)
public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateBookNameException e) {
    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("status", 409, "message", e.getMessage()));
}
```

`readOnly = true` 寫進這筆交易的定義。Proxy 開始交易時，交易管理器讀得到這個旗標。它表示這筆交易不打算寫入，Hibernate 不會為了髒檢查去 flush。它本身不會把連線切到另一台資料庫。

這個 demo 的切換做在自己的 `JpaTransactionManager` 裡。交易開始時，`doBegin` 把 `definition.isReadOnly()` 抄進 `DbContextHolder`。`RoutingDataSource` 再讀那個旗標，決定拿主庫還是從庫的連線。讀的人是這個覆寫過的交易管理器，不是 `@Transactional` 這四個字。

### `@Configuration` 也是一種 proxy

`@Configuration` 類別預設會被做成子類別 proxy。同一個設定類別裡，`@Bean` 方法互相呼叫時，proxy 交回 container 裡已經做好的那顆，不會再執行一次方法本體。

`DataSourceConfig.dataSource()` 裡面呼叫了 `primaryDataSource()`。那次呼叫拿到的是已經註冊的那顆 `HikariDataSource`。下面又呼叫一次 `primaryDataSource()` 當預設連線，還是同一顆。

```java
targetDataSources.put("primary", primaryDataSource());
routingDataSource.setDefaultTargetDataSource(primaryDataSource());
```

這是 `@Configuration` 的 proxy 在攔自己的方法。把類別改成 `proxyBeanMethods = false`，這兩次呼叫會真的各跑一遍方法本體，變成額外的連線池。

`@Transactional` 的 proxy 包的是 service。`@Configuration` 的 proxy 包的是設定類別。兩顆 proxy 都是「從外面進來的呼叫才被攔」。

## Repository 是一張指令

`BookRepository` 是介面，沒有建構子，不能 `new`。Spring Data 在啟動時做出 JDK proxy，當成 bean 注入 `BookService`。

```java
public interface BookRepository extends JpaRepository<Book, Long> {
    boolean existsByName(String name);

    Optional<Book> findByName(String name);
}
```

`existsByName` 這個名字會被解析成查詢。呼叫 `bookRepository.existsByName(name)` 時，進的是 proxy。Proxy 用目前執行緒上的 `EntityManager` 下 SQL，把結果交回。

介面裡沒有方法本體。名字寫錯、對不到欄位，錯誤出現在啟動或第一次呼叫，不會安靜地什麼都不查。

`@EntityGraph(attributePaths = "author")` 也是給這個 proxy 的指令。它要這次查詢把 `author` 一起載入。`Book.author` 標的是 `FetchType.LAZY`。沒有這張圖，`author` 要等到有人讀它才查。

## Entity 的四種狀態

`@Transactional` 打開的那段時間，這條執行緒綁著一個 persistence context。同一個 id 在這段裡只會有一個 Java 物件。

```text
new Book(name, author)          transient    資料庫還沒有這一列
└─ repository.save(book)
      └─ managed                 context 正在追蹤它
            ├─ 改 setter         交易結束時 flush 成 UPDATE，不必再 save
            ├─ 交易提交          變成 detached。setter 只改 Java 物件
            └─ repository.delete 變成 removed，提交時 DELETE
```

在交易裡從 `findById` 拿回來的 `Book` 是 managed。改 `setName` 之後直接返回，提交時 Hibernate 比對快照，做出 UPDATE。

交易結束後，那個物件是 detached。再 `setName`，資料庫不變。要寫回去，得在另一筆交易裡 `save`，把這份值合併回去。

`new Book(...)` 一直是 transient，直到 `save` 把它放進 context。沒有呼叫 `save` 就結束交易，資料庫沒有這一列。

## Lazy 和 open-in-view

`Book.author` 是 LAZY。交易裡沒有人讀 `author`，context 裡就沒有作者那一列。

Spring Boot 預設 `spring.jpa.open-in-view=true`。請求整段都綁著一個 `EntityManager`，交易卻在 service 方法返回時就提交了。

```text
service 方法返回
├─ @Transactional 的交易已經提交
└─ open-in-view 的 EntityManager 還開著
      └─ Jackson 序列化 Book，呼叫 getAuthor()
            ├─ author 已經在記憶體     不再下 SQL
            └─ author 還沒載入         再下一次 SELECT
                  這次 SELECT 在交易外面
```

額外的 SELECT 發生在寫 JSON 的時候，不在 service 裡。`open-in-view=false` 時，同一次 `getAuthor()` 丟 `LazyInitializationException`。例外發生在 service 已經返回之後，出在訊息轉換，不在業務方法。

這個 demo 的查詢方法用 `@EntityGraph` 把 `author` 在同一道 SQL 載入。序列化時 `author` 已經在物件裡，不靠 open-in-view 再補一查。

## 請求在進方法之前就停掉

Controller 方法本體不是每個打到這條 URL 的請求都會跑。

```text
找到對應的方法
├─ JSON 不合法
│     HttpMessageNotReadableException    方法不跑
├─ 少了必填的 query 或 path
│     缺參數的例外                         方法不跑
├─ JSON 合法，@Valid 失敗
│     MethodArgumentNotValidException    方法不跑
├─ interceptor 的 preHandle 回傳 false
│     方法不跑
└─ 參數都過了
      方法本體才跑
            └─ 方法再丟例外               advice 接，正常回傳值不寫出
```

`@Valid` 標在參數上。Validator 在呼叫方法之前跑。`BookController.createBook` 的 `@Valid @RequestBody BookRequest` 沒過，`bookService.createBook` 不會被呼叫。

這個 demo 的 `GlobalExceptionHandler` 另外接了 `MethodArgumentNotValidException`，把欄位錯誤寫成 400。沒有這個 handler 時，請求一樣進不了方法，只是回應變成 Spring MVC 預設的錯誤 body。

URL 沒有任何方法對得上時，DispatcherServlet 直接 404。Controller 的建構子不會因為這次請求再跑，方法本體也不跑。

## 請求外面還有三個插入點

進 controller 方法之前，還有三個地方可以接手。三個地方看得到的東西不一樣。方法本體跑起來之後，service 仍然是 container 注入的那顆。

```text
HTTP 進來
│
├─ 1. Filter
│     在 DispatcherServlet 之前
│     看得到原始 request、response
│
├─ 2. HandlerInterceptor
│     已經找到要呼叫的方法
│     preHandle 回傳 false，方法不跑
│
└─ 3. Bean proxy
      別人呼叫這顆 bean 的方法時
      @Transactional、@Cacheable、@Async 在這裡
            └─ 真正的方法
```

Filter 適合還不需要知道是哪個 controller 的事，例如編碼、請求紀錄。Interceptor 適合已經知道目標方法、又還沒呼叫它的事。交易和快取放在 bean proxy，因為它們跟的是方法，不是 URL。

Filter 裡 `new` 一個 service 來用，那個物件仍然沒有 proxy。要交易，就注入 container 裡的那顆 service，從 filter 呼叫它。那次呼叫從外面進來，proxy 會攔。

## Singleton：一顆物件，很多執行緒

預設 scope 是 singleton。`BookService` 全程式一顆。Tomcat 用不同執行緒同時呼叫它的方法。

```text
BookService          一顆
├─ 執行緒 A          POST /books
├─ 執行緒 B          GET /books/1
└─ 執行緒 C          DELETE /books/2
```

方法的區域變數各自在自己的堆疊上，執行緒之間不會混。寫進 `BookService` 欄位的東西，所有執行緒共用。把「這次請求的使用者」放進 service 欄位，另一個請求會把它蓋掉。

`DbContextHolder` 這種要跟著「這一筆交易」走的旗標，放在 `ThreadLocal`，不放在 singleton 欄位。交易結束要清掉。這個 demo 在 `doCleanupAfterCompletion` 呼叫 `DbContextHolder.clear()`。沒清的話，這條 Tomcat 執行緒下次被拿去處理別的請求，還看得到上一筆的旗標。

Prototype scope 每次跟 container 要，才做出新的一份。Singleton 的建構子只跑一次。把一顆 prototype 注入 singleton 時，singleton 建立的那一次拿到一份，之後所有請求共用那一份。它不會變成「每個請求一顆」。每個請求一顆要另外用 request scope，或在方法裡向 container 再要。

## 設定值什麼時候讀

`application.properties` 在啟動時讀。`@ConfigurationProperties` 把前綴下的欄位綁進一顆 bean。

```java
@ConfigurationProperties("app.datasource.primary")
public DataSourceProperties primaryDataSourceProperties() {
    return new DataSourceProperties();
}
```

`app.datasource.primary.*` 在這顆 `DataSourceProperties` 建立時寫進物件。後面 `primaryDataSource()` 用它組出 `HikariDataSource`。連線池用的是當時綁好的 url、帳號、密碼。

執行中改檔案，已經做出來的 bean 不會重讀。要新的值，得讓這顆 bean 重新建立，通常就是重啟。

## Starter 放進來什麼

Starter 是一組依賴，加上一份自動設定清單。清單在 jar 裡的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。`@EnableAutoConfiguration` 讀這份清單，再看每個設定類別的條件。

| Starter | classpath 上多了什麼 | 條件成立時多出來的東西 |
| --- | --- | --- |
| `spring-boot-starter-web` | Tomcat、Spring MVC、Jackson | 內嵌 Tomcat、`DispatcherServlet`、把物件轉 JSON 的 converter |
| `spring-boot-starter-validation` | Hibernate Validator | `@Valid` 才有實作可跑。沒有這顆 starter，註解在 classpath 上沒有對應的 validator |
| `spring-boot-starter-data-jpa` | Hibernate、Spring Data JPA | `EntityManagerFactory`、交易管理器、repository proxy。這個 demo 排除了前兩樣的自動設定，改由 `DataSourceConfig` 建立 |
| `spring-boot-starter-data-redis` | Redis 客戶端 | Redis 連線。快取要另外有 `CacheManager` 才會接上 `@Cacheable` |
| `spring-boot-starter-cache` | Spring Cache 抽象 | 快取的 proxy。真正存資料的後端由 Redis 或其他 `CacheManager` 提供 |
| `spring-kafka` | Kafka 客戶端 | 有設定時做出 `KafkaTemplate` 和 listener container。`@EnableKafka` 打開 listener 的後處理 |

沒有 `spring-boot-starter-web`，`SpringApplication.run` 仍然會建立 container，掃 bean、注入依賴。沒有 Tomcat，也沒有 `DispatcherServlet`。Bean 都在，沒有 HTTP 這條進入點。

Auto-configuration 是少寫那些 `@Bean`。它不是請求路徑上的另一層。條件成立時，它用 `@Bean` 把物件放進同一個 container。

## 這個 demo 實際走的路

請求路徑就是上面那條。`BookController` 用建構子收下 `BookService`。`BookService` 用建構子收下兩個 repository、`KafkaProducer` 和 `BookCache`。建立、更新、刪除標 `@Transactional`，查詢標 `@Transactional(readOnly = true)`。單筆查詢的快取由 `BookCache` 自己讀寫，方法上沒有 `@Cacheable`。`@Transactional` 靠包在 service 外面的 proxy 讀。

例外走出 service 之後，由 `GlobalExceptionHandler` 寫成 409、404、400。驗證失敗在進 `createBook` 之前就變成 `MethodArgumentNotValidException`。

和一份預設 JPA 程式相比，連線從哪來是這條主路上的分支：

```text
@Transactional proxy 開始交易
└─ DataSourceConfig 裡覆寫的 JpaTransactionManager.doBegin
      └─ 把 readOnly 寫進 DbContextHolder（ThreadLocal）
            └─ RoutingDataSource 選 primary 或 replica
                  └─ 選中的 HikariDataSource
```

`exclude` 拿掉的是自動建立連線和 `EntityManagerFactory`。`DataSourceConfig` 用 `@Bean` 補回這兩樣，再加上這顆覆寫過的交易管理器。Repository、controller、交易 proxy、advice，都還是這份筆記裡的那條路。

讀寫分離怎麼用 Docker 把主庫和從庫跑起來，寫在專案的 `TUTORIAL.md`。那份是這條連線上的分支。這份是分支接上去之前，請求和 bean 已經在走的路。Kafka 和 Redis 接在 service 方法裡面，是這條路旁邊的兩段，寫在下面。

## Kafka：書寫進資料庫之後的通知

這個 demo 的 Kafka 只做一件事。建立或刪除書籍的資料庫交易進行到 `save` / `deleteById` 之後，送一句文字到 topic `book-events`。查詢和更新不送。

```text
POST /books 或 DELETE /books/{id}
└─ BookService                    仍在 @Transactional 裡面
      ├─ repository.save / deleteById
      └─ KafkaProducer.send
            └─ KafkaTemplate
                  └─ localhost:9092          這一台 Kafka 行程，叫做 broker
                        └─ topic book-events
                              └─ KafkaConsumer.listen
                                    └─ 印出 [Kafka Consumer] 收到訊息
```

`compose.yaml` 先起 ZooKeeper（`2181`），再起 Kafka（`9092`）。應用程式只連 `9092`。`application.properties` 的 `spring.kafka.bootstrap-servers=localhost:9092` 就是這個位址。`DemoApplication` 上的 `@EnableKafka` 打開監聽。`KafkaConfig` 用這個位址做出兩組 bean：producer 把 Java 的 `String` 編成位元組送出，consumer 再解回 `String`。

`KafkaConsumer` 訂閱 `book-events`，群組名稱是 `book-group`。群組用來記住這組消費者讀到哪一則。Compose 開了自動建 topic，第一次送出時 Kafka 自己建立 `book-events`。

送出是非同步的。`kafkaTemplate.send` 把訊息交給客戶端後就返回，HTTP 回應不必等消費者印完。主控台那一行出現在回應之後。

### Broker、叢集、ZooKeeper

Broker 是一台正在跑的 Kafka 伺服器。這個 demo 的容器 `kafka`、對外 `9092`，就是這一台。訊息存在 broker 上。應用程式關掉再重開，只要這個容器還在，還沒被讀走的訊息還在。

叢集是多台 broker 合在一起，對外仍像一套 Kafka。同一份 topic 的資料拆到不同機器，一台掛了，其他台還能繼續收發。要同時滿足兩件事：broker 在不同機器（不同 IP），而且同一則訊息在至少兩台各存一份（replication factor 大於 1）。只有多台、複本數仍是 1 時，存那份資料的那台一掛，這段資料就暫停收發。同一台機器用不同 port 開好幾份，機器一斷電就一起停，那是練習用的排法。這個 demo 只有 `KAFKA_BROKER_ID: 1`，是單機。

上線時 `bootstrap-servers` 寫上好幾台的位址。那是入場名單。客戶端問叢集「這個 topic 現在由哪一台負責」，再連到那一台。名單裡某一台連不上，還能從另外幾台問到整個叢集。

ZooKeeper 在這份 Confluent 7.5.0 裡記住 broker 在哪、誰負責協調。應用程式不連它。ZooKeeper 沒起來，Kafka 無法註冊自己。新版 Kafka 改用內建的 KRaft，中介資料存在 Kafka 自己的 controller 裡。這個 Compose 仍是舊做法。對 Spring Boot 來說連線目標還是 `localhost:9092`。

### 交易和 500

`bookRepository.save(book)` 寫在 `kafkaProducer.send` 前面，不代表那一列已經提交。`createBook` 標了 `@Transactional`。PostgreSQL 要等這個方法正常返回，proxy 才送出 `COMMIT`。中間的 `save` 只是把新增放進還沒提交的交易，別的連線查不到。

`9092` 沒有人聽時，`send` 會在這條請求上卡住約 60 秒，然後丟出 runtime 例外。例外冒出方法，proxy 改送 `ROLLBACK`，`GlobalExceptionHandler` 回 500。使用者看到失敗時，書也不在資料庫裡。刪除是同一條路徑。怪的地方是：這句通知只會被印出來，Kafka 沒起來卻讓建立和刪除一起失敗。

另一種走法是 `send` 只把訊息放進背景佇列就返回。方法照常提交，介面回成功。Kafka 若在這之後才送失敗，書已經在資料庫裡，主控台沒有那行「新書上架」。

### 快取留在 Service，不改由消費者寫

`POST /books` 在送出 Kafka 之後呼叫 `bookCache.putAfterCommit(book)`。Redis 是這支程式自己的讀取快取，所有實例共用同一個 Redis，這一次寫入別的實例也看得到。`putAfterCommit` 等交易提交成功才寫。交易回滾時快取不會多出這一本。

消費者是事後才收到 `"新書上架: 書名"`。改由它寫快取的話，介面已經回成功、快取可能還沒寫入；消費者停著時資料庫有書、Redis 沒有。這句訊息裡也沒有作者和標籤，組不出 `BookSnapshot`。

Kafka 適合通知別的系統：寄信、更新搜尋索引、做統計。那些工作可以晚一點，失敗可以單獨重試。

### 業界拿它做什麼，削峰和 load balancer 各管一段

Kafka 在系統之間傳遞「已經發生的事」。訊息先落在磁碟上，之後還能依序再讀。一個服務寫完自己的資料就送出事件，其他服務各自訂閱。

常見的是這幾種：

- 訂單成立後，庫存、寄信、點數、搜尋索引各自訂閱同一個 topic。下單 API 只負責把訂單寫好並送出事件。
- 把資料庫變更、操作紀錄、伺服器日誌收成一條流，再送給數倉或即時計算。
- 消費者當機或程式改版後，從上次讀到的位置繼續，或從頭再處理同一批事件。`book-group` 記的就是這個位置。

削峰是把突然到來的工作先收進 topic，再讓後面的處理依照自己吃得下的速度做。一秒一萬筆下單時，網站做完必要檢查、把「有人要買」寫進 topic，就回應已收到。後面的消費者用固定數量慢慢讀，例如每秒五百筆。多出來的先排在 topic 裡，尖峰過去繼續做完。

Load balancer 把同一時間的請求分給多台機器一起做。十台機器同時去做，同一秒要完成的仍然是一萬筆。它不把工作留到尖峰過去。使用者要立刻知道結果的那一段（這筆訂單有沒有成立）留在 API 裡，前面用 load balancer。可以晚一點的寄信、索引、統計才進 Kafka。

這個 demo 的 `POST /books` 要等資料庫把書寫進去才回成功，這段留在 service。Kafka 上那句「新書上架」才是可以晚一點的通知。消費者目前只印 log，尖峰的工作量還沒有被移走。

## Redis：提交成功之後才放進快取

單筆書的快取名稱是 `books`，key 是書的 id，存活 10 分鐘（`RedisCacheConfig` 的 `entryTtl`）。值是 `BookSnapshot`：id、書名、作者、標籤，加上一個 `deleted` 旗標。

```text
建立或更新
└─ bookCache.putAfterCommit(book)
      └─ 登記在交易提交之後
            └─ cache.put(id, snapshot)     deleted = false

刪除
└─ bookCache.markDeletedAfterCommit(id)
      └─ 登記在交易提交之後
            └─ cache.put(id, deleted=true 的 snapshot)

GET /books/{id}
└─ bookCache.get(id)
      ├─ 有 snapshot，deleted = false    直接回，dataSource 是 cache
      ├─ 有 snapshot，deleted = true     ResourceNotFoundException
      └─ 沒有                            改查資料庫，再 putIfAbsent
```

建立書籍時寫進 Redis 的是這一本的新快照，不是把快取清掉。更新用同一支 `putAfterCommit`，蓋掉同一個 id。刪除留下 `deleted = true` 的那筆，key 還在。下次用這個 id 查書會直接當成找不到，不會去從庫把已經刪掉的書再查回來。

程式真正把 Redis 的 key 刪掉，只有讀取解不開舊格式的時候。`BookCache.get` 抓到序列化例外就 `cache.evict(id)`，然後當成沒有快取，改查資料庫。

列表查詢 `GET /books` 不讀這份快取，也不送 Kafka。

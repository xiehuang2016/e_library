# E-Library Service

## English

A Spring Boot service for browsing a digital catalog, borrowing and renewing titles, returning books, and managing overdue debt. Catalog, loan, and user data are stored through Spring Data JPA. The default profile uses an in-memory H2 database initialized by Flyway migrations.

### Requirements and run

- Java 21
- Maven 3.6.3 or newer

```sh
mvn spring-boot:run
mvn test
```

The application listens on `http://localhost:8080`. H2 data is reset on restart. Flyway applies pending migrations during startup and records them in `flyway_schema_history`.

### API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/books?q=java` | Browse/search by title, author, or ISBN; `q` is optional |
| `GET` | `/api/v1/books/{bookId}` | Read catalog details and copy availability |
| `POST` | `/api/v1/loans` | Borrow: `{"bookId":"book-1","userId":"reader-1"}` |
| `POST` | `/api/v1/loans/batch` | Borrow together: `{"userId":"reader-1","bookIds":["book-1","book-2"]}` |
| `POST` | `/api/v1/loans/{loanId}/renew` | Renew: `{"userId":"reader-1"}` |
| `POST` | `/api/v1/loans/{loanId}/return` | Return: `{"userId":"reader-1"}` |
| `POST` | `/api/v1/loans/return-batch` | Return together: `{"userId":"reader-1","loanIds":["loan-1","loan-2"]}` |
| `GET` | `/api/v1/users/{userId}/loans` | List active loans |
| `GET` | `/api/v1/users/{userId}/debt` | Read outstanding debt |
| `POST` | `/api/v1/users/{userId}/debt/payments` | Pay debt: `{"amount":2.00}` |

Loan term, maximum renewals, and daily fine rate are configured in `application.yml`. Defaults are a 14-day loan, at most 3 renewals, and a fine of 1.00 per started overdue day. The sample assumes one currency (CNY) and stores monetary amounts as `BigDecimal`. Renewal is rejected for returned or overdue loans. Batch borrow and return requests are all-or-nothing: the service validates the whole request before changing inventory, and the transaction rolls back if any operation fails. Borrowing is rejected while outstanding debt is greater than zero. When debt exists, payment is blocked until all active loans have been returned; this lets the final return calculate any additional fine before the balance is settled. Payments above the balance and non-positive amounts are rejected.

`userId` identifies the account associated with a loan; a `users` row is created on the first borrow. Returns are restricted to that user. Expected API failures have a consistent JSON body with `timestamp`, `status`, `message`, and `requestId`; optimistic concurrency conflicts return HTTP 409 so clients can reload and retry. Every response also returns the correlation value in the `X-Request-Id` header.

### Design

- `api`: HTTP endpoints, request validation, and centralized error mapping.
- `service`: `LibraryService` coordinates borrowing, renewal, return, and debt use cases through `BookService`; only `BookService` accesses `BookRepository`.
- `domain/model`: JPA entities (`Book`, `Loan`, `User`). The `users` table holds each user's outstanding debt and version.
- `domain/policy`: `FinePolicy` interface.
- `domain/policy/impl`: `DailyFinePolicy` implementation.
- `repository`: Spring Data JPA persistence interfaces.
- `config`: validated YAML-backed policy settings.
- `exception`: API/application exception types.
- `constants`: shared numeric constants.
- `logging`: `ScopeValue` MDC scopes and the HTTP request logging filter.

`Book`, `Loan`, and `User` each have a JPA `@Version` field. Hibernate includes the loaded version in update predicates and increments it on success. If another transaction has changed the row, the stale update fails and is translated to HTTP 409. This is optimistic locking, implemented as a compare-and-set database update.

`db/migration/V1__initial_schema.sql` defines tables, constraints, foreign keys, and indexes. `V2__seed_demo_data.sql` contains seed DML. Flyway applies these versioned migrations in order and records checksums; add a new migration such as `V3__...sql` for future changes instead of editing a migration that has already been applied. Hibernate runs with `ddl-auto: validate` so entity/schema drift is detected instead of silently changing the schema. Indexes follow the repository predicates: `(user_id, book_id, returned_at)` supports duplicate-active-loan checks, `(user_id, returned_at, borrowed_at)` supports active-loan listing, and `book_id` supports the loan-to-book foreign key. Normalized title/author/ISBN columns support the case-insensitive prefix search; `isbn` is unique. User and entity ID lookups use primary-key indexes.

Tests use real Spring Data repositories with H2 in-memory storage. They cover catalog search/detail, single and batch borrowing/returns, batch validation atomicity, stock conflicts, renewals, debt accrual, debt blocking, repayment ordering, and user ownership. Logging tests check nested MDC restoration, control-character sanitization, request-ID validation, and cleanup.

### Unified request logging

`RequestLoggingFilter` accepts a safe `X-Request-Id` value or generates a UUID, returns it in the response header, and writes a completion log with HTTP method, status, and duration. `ScopeValue` temporarily adds `requestId` and `userId` to SLF4J MDC so log lines share the same context; nested scopes restore the previous MDC values on exit. The console pattern in `application.yml` prints both values. The filter does not log request bodies or full URLs.

### Book cache

`BookService` uses Spring `@Cacheable` with local Caffeine caches for immutable book content and search results. Cache values use weak references, with a maximum size of 1,000 entries and a 10-minute access expiry. Available-copy counts are read from the repository on each request, so borrowing and returning never depend on cached inventory. If book-content write APIs are added, they must evict or refresh the related cache entries.

### Session decisions and requirement changes

This implementation was refined through the interview-design discussion in this session:

- The initial scope is catalog browsing/detail, borrowing, returning, active-loan lookup, and overdue debt handling. The implementation uses Spring Boot, JPA, Flyway, and H2-backed integration tests instead of hardcoded in-memory repositories.
- Responsibilities were organized into `api`, `service`, `domain`, `repository`, `config`, `constants`, `exception`, and `logging`; domain entities and policy abstractions are kept separate from persistence interfaces and service implementations.
- Fine calculation is isolated behind `FinePolicy` (with `DailyFinePolicy` as the current rule), keeping fee calculation replaceable while `LibraryService` coordinates when fees are applied.
- Java 21 and Spring Boot 3 were selected. `Book`, `Loan`, and `User` use JPA `@Version` for optimistic compare-and-set updates. The IntelliJ module language level is also set to Java 21 so Java records such as `BookContent` compile in the IDE.
- Loan duration, maximum renewals (default 3), and fine rate are YAML-backed settings. Borrowing is blocked by debt. If debt exists, all active loans must be returned before payment, allowing any last-return fine to be included in the amount due.
- Single and batch borrow/return APIs are supported. Batch operations validate the request as a whole and run in one transaction, so a failed item does not leave partial inventory or loan updates.
- Book content and search results use weak-value Caffeine caches behind `BookService`; copy availability remains a live database read. `ScopeValue` carries request and user context into structured logs.
- The README was requested in English and Traditional Chinese. The AI tool/model and token-usage record is included above; exact per-task token counts are unavailable and are not estimated.
- Code formatting was standardized with `.editorconfig`; follow-up review added tests for batch rollback behavior and fine rounding at sub-day boundaries.

The tests now include batch-operation atomicity and started-day fine rounding boundaries. Test execution remains blocked in this environment because Maven cannot download the Spring Boot parent POM from the configured Aliyun mirror; see the validation record above.

### AI-assisted code generation record

| Item | Record |
| --- | --- |
| Tool | OpenAI Codex desktop coding agent |
| Model | GPT-6 |
| Workflow | Requirements were translated into API, domain, service, repository, and configuration changes in the workspace. Changes were made iteratively from review feedback; integration tests use H2 rather than mocked repositories. |
| Token usage | Per-task input/output token counts are not exposed to this workspace. No numeric count is available; values are intentionally not estimated. |
| Validation | The Java 21 / Spring Boot 3.5 upgrade tests were not run in this environment. The installed toolchain is Java 8 and Maven 3.5, and Maven could not download the Boot 3 parent POM from the configured mirror. |

### Production follow-ups

Use authentication-derived user identity instead of trusting `userId` in request bodies, adopt persistent storage and versioned schema migrations, and confirm currency and fine rules with the business. Actual digital book/journal payload delivery is not implemented yet: the current book detail is catalog metadata, so a content storage/provider and an authorized reading API are still needed. The demo SQL catalog is intentionally small.

---

## 繁體中文

這是一個 Spring Boot 電子圖書館服務，提供數位館藏瀏覽、借閱、續借、歸還及欠費管理。館藏、借閱與使用者資料透過 Spring Data JPA 儲存；預設使用 H2 記憶體資料庫，並由 Flyway 遷移檔初始化。

### 環境需求與啟動

- Java 21
- Maven 3.6.3 以上

```sh
mvn spring-boot:run
mvn test
```

服務預設監聽 `http://localhost:8080`。H2 資料會在重新啟動後清除。Flyway 會在啟動時套用尚未執行的遷移，並記錄於 `flyway_schema_history`。

### API

| 方法 | 路徑 | 用途 |
| --- | --- | --- |
| `GET` | `/api/v1/books?q=java` | 依書名、作者或 ISBN 瀏覽／搜尋；`q` 可省略 |
| `GET` | `/api/v1/books/{bookId}` | 查詢館藏詳細資料與可借冊數 |
| `POST` | `/api/v1/loans` | 借書：`{"bookId":"book-1","userId":"reader-1"}` |
| `POST` | `/api/v1/loans/batch` | 批量借書：`{"userId":"reader-1","bookIds":["book-1","book-2"]}` |
| `POST` | `/api/v1/loans/{loanId}/renew` | 續借：`{"userId":"reader-1"}` |
| `POST` | `/api/v1/loans/{loanId}/return` | 還書：`{"userId":"reader-1"}` |
| `POST` | `/api/v1/loans/return-batch` | 批量還書：`{"userId":"reader-1","loanIds":["loan-1","loan-2"]}` |
| `GET` | `/api/v1/users/{userId}/loans` | 查詢尚未歸還的借閱 |
| `GET` | `/api/v1/users/{userId}/debt` | 查詢未繳欠費 |
| `POST` | `/api/v1/users/{userId}/debt/payments` | 繳納欠費：`{"amount":2.00}` |

借閱期限、最多續借次數及每日逾期費率皆設定於 `application.yml`。預設借期為 14 天，最多續借 3 次；每逾期一天（未滿一天也計一天）收取 1.00。範例假設使用單一幣別（人民幣 CNY），金額以 `BigDecimal` 儲存。已歸還或已逾期的借閱不可續借。批量借書與還書採全有或全無：先驗證整批資料，再於同一交易更新庫存與借閱，任一項失敗便回滾。讀者有未繳欠費時不可借書；若有欠費，必須先歸還所有在借書籍，才可繳款，確保最後還書所產生的逾期費也納入結清金額。繳款金額不可大於欠費，也不可為零或負數。

`userId` 是借閱所屬的使用者識別碼；首次借書時會建立對應的 `users` 資料列。只有該使用者可以歸還。預期中的 API 錯誤會使用一致的 JSON 格式，包含 `timestamp`、`status`、`message` 和 `requestId`；每個回應也會透過 `X-Request-Id` 標頭回傳追蹤識別碼。樂觀鎖發生版本衝突時回傳 HTTP 409，呼叫端可重新讀取資料後再試。

### 架構

- `api`：HTTP 端點、請求驗證與集中式錯誤處理。
- `service`：`LibraryService` 透過 `BookService` 協調借閱、續借、歸還及欠費使用案例；只有 `BookService` 直接存取 `BookRepository`。
- `domain/model`：JPA 領域實體（`Book`、`Loan`、`User`）。`users` 資料表儲存使用者欠費與版本。
- `domain/policy`：`FinePolicy` 策略介面。
- `domain/policy/impl`：`DailyFinePolicy` 策略實作。
- `repository`：Spring Data JPA 持久化介面。
- `config`：經驗證的 YAML 業務設定。
- `exception`：API／應用程式例外型別。
- `constants`：共用數值常數。
- `logging`：`ScopeValue` MDC 範圍與 HTTP 請求日誌篩選器。

`db/migration/V1__initial_schema.sql` 定義資料表、限制條件、外鍵與索引；`V2__seed_demo_data.sql` 只放示範資料的 DML。Flyway 會依版本順序執行遷移並記錄 checksum。之後請新增 `V3__...sql` 等新檔案，不要修改已套用的遷移。Hibernate 使用 `ddl-auto: validate` 檢查實體與結構是否一致，不會在啟動時靜默修改結構。索引依 Repository 查詢條件規劃：`(user_id, book_id, returned_at)` 支援檢查同一使用者是否已有未還書籍；`(user_id, returned_at, borrowed_at)` 支援列出未歸還借閱；`book_id` 支援借閱到館藏的外鍵查詢。標準化書名、作者及 ISBN 欄位支援不區分大小寫的前綴搜尋；`isbn` 唯一。使用者與實體 ID 查詢使用主鍵索引。

`Book`、`Loan` 與 `User` 都有 JPA `@Version` 欄位。Hibernate 會在更新條件中帶入讀取時的版本，更新成功後遞增版本。若資料已被其他交易修改，舊版本的更新會失敗，並轉成 HTTP 409。這是樂觀鎖，在資料庫更新時以 compare-and-set 條件實作。

測試使用 H2 記憶體資料庫搭配真正的 Spring Data Repository，不以 mock Repository 取代資料庫。測試涵蓋館藏搜尋／詳情、單筆與批量借還、批量驗證原子性、庫存衝突、續借、欠費計算、欠費阻擋借書、先還書再繳款及使用者權限。日誌測試會檢查巢狀 MDC 還原、控制字元清理、Request ID 驗證與範圍清理。

### 統一請求日誌

`RequestLoggingFilter` 會接受格式安全的 `X-Request-Id`，若請求未提供則產生 UUID，並將識別碼放入回應標頭；請求完成時記錄 HTTP 方法、狀態碼與耗時。`ScopeValue` 透過 SLF4J MDC 暫存 `requestId`、`userId`，讓同一範圍內的日誌帶有一致欄位；巢狀範圍結束時會還原原本的 MDC 值。`application.yml` 的主控台格式會輸出這兩個欄位。篩選器不記錄請求本文或完整 URL。

### 書籍快取

`BookService` 使用 Spring `@Cacheable` 與本機 Caffeine，快取不可變的書籍內容和搜尋結果。快取值使用弱引用，最多 1,000 筆，最後存取 10 分鐘後過期。可借冊數每次都從 Repository 讀取，因此借還書不會受到快取庫存影響。若日後新增書籍內容更新 API，必須同步清除或更新相關快取。

### 本次會話的需求演進與設計決策

本實作依照本次面試題討論逐步調整：

- 初始範圍包括瀏覽與查詢館藏、借書、還書、查詢未歸還借閱及逾期欠費。資料存取改用 Spring Boot、JPA、Flyway 與 H2 整合測試，不使用硬編碼的記憶體 Repository。
- 責任整理至 `api`、`service`、`domain`、`repository`、`config`、`constants`、`exception` 與 `logging`；領域實體、策略介面、持久化介面及服務實作分層管理。
- 逾期費計算抽象為 `FinePolicy`（目前由 `DailyFinePolicy` 實作），讓費率規則可替換，並由 `LibraryService` 協調何時套用罰款。
- 採用 Java 21 與 Spring Boot 3。`Book`、`Loan`、`User` 以 JPA `@Version` 樂觀鎖支援 compare-and-set 更新；IntelliJ 模組語言級別也同步設為 Java 21，讓 `BookContent` 等 record 可正常辨識。
- 借閱期限、最多續借次數（預設 3 次）及逾期費率由 YAML 設定。有欠費時禁止借書；若仍有欠費，必須先還清所有在借書籍才能繳款，讓最後一次還書所產生的罰款也納入欠費。
- 支援單筆及批量借還 API。批量請求會先整體驗證，並在同一交易執行；任一項失敗不會留下部分庫存或借閱異動。
- `BookService` 使用弱引用 Caffeine 快取書籍內容與搜尋結果；可借冊數維持即時查詢資料庫。`ScopeValue` 將 request/user 上下文帶入統一日誌。
- README 依需求提供英文與繁體中文。上方列有 AI 工具、模型與 token 用量紀錄；工作區無法取得精確 token 數，因此不估算。
- 以 `.editorconfig` 統一程式碼格式；後續檢視補上批量失敗回滾與不足一天逾期進位的測試。

測試已加入批量操作原子性及逾期天數進位邊界案例。此環境的 Maven 無法從設定的 Aliyun 鏡像下載 Spring Boot 父 POM，因此目前無法執行測試；詳見上方驗證紀錄。

### AI 輔助程式碼產生紀錄

| 項目 | 紀錄 |
| --- | --- |
| 工具 | OpenAI Codex 桌面版程式碼代理 |
| 模型 | GPT-6 |
| 流程 | 將需求拆解為 API、領域模型、服務、Repository 與設定，再於工作區逐步修改；依照檢視回饋迭代，並以 H2 整合測試驗證資料存取，不以 mock Repository 代替。 |
| Token 用量 | 此工作區無法讀取本任務的輸入／輸出 token 精確數量，因此不提供數字，也不以估算值代替。 |
| 驗證狀態 | 本環境尚未執行 Java 21／Spring Boot 3.5 升級後的測試。現有工具鏈只有 Java 8 與 Maven 3.5；Maven 無法從目前設定的鏡像下載 Spring Boot 3 父 POM。 |

### 正式環境後續事項

應改由登入驗證取得使用者身分，不要信任請求內容中的 `userId`；正式部署時需使用持久化資料庫與版本化資料庫遷移工具，並與業務確認幣別及逾期費率。目前尚未提供電子書／期刊的實際內容傳遞：書籍詳情只有館藏中繼資料，因此仍需內容儲存／提供者及具借閱授權檢查的閱讀 API。SQL 中的館藏資料僅供示範。

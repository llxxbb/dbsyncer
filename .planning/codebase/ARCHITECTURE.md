<!-- refreshed: 2026-07-02 -->
# Architecture

**Analysis Date:** 2026-07-02

## System Overview

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                         Web / REST Layer                                   │
│            `dbsyncer-web` — Spring Boot controllers, UI, config           │
├──────────────┬───────────────┬──────────────┬────────────────────────────┤
│ IndexCtrl    │ DataSourceCtrl│ MonitorCtrl  │ OpenApiCtrl                │
│ ConfigCtrl   │ MappingCtrl   │ SystemCtrl   │ LicenseCtrl                │
│ `web/ctrl/`  │ `web/ctrl/`   │ `web/ctrl/`  │ `web/ctrl/`                │
└──────┬───────┴───────┬───────┴──────┬───────┴──────────────┬─────────────┘
       │               │              │                      │
       ▼               ▼              ▼                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         Business Service Layer                             │
│          `dbsyncer-biz` — orchestration, validators, permission           │
├──────────────────┬───────────────┬─────────────────┬──────────────────────┤
│ MappingService   │ DataSyncSvc   │ MonitorService  │ ConnectorService     │
│ ConvertService   │ TableGroupSvc │ SystemConfigSvc │ UserConfigService    │
│ `biz/impl/`      │ `biz/impl/`   │ `biz/impl/`     │ `biz/impl/`          │
└────────┬─────────┴───────┬───────┴────────┬────────┴───────────┬──────────┘
         │                 │                │                    │
         ▼                 ▼                ▼                    ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         Core Engine Layer                                  │
│          `dbsyncer-parser` — parsing, buffer actuators, consumers         │
├────────────────┬───────────────┬────────────────┬─────────────────────────┤
│ParserComponent │MessageService │CacheService    │BufferActuatorRouter     │
│DDLParser       │ProfileComponent│ProfileComponent│TableGroupContext        │
│`parser/impl/`  │`parser/impl/` │`parser/`       │`parser/flush/`          │
└────────┬───────┴───────┬───────┴────────┬───────┴───────────┬─────────────┘
         │               │                │                   │
         ▼               ▼                ▼                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                      Manager / Puller Layer                                │
│              `dbsyncer-manager` — sync strategies, lifecycle               │
├─────────────────┬─────────────────┬──────────────────────────────────────┤
│ ManagerFactory  │ FullPuller      │ IncrementPuller / FullIncrementPuller│
│ Puller SPI      │ Deployment      │ PreloadTemplate                      │
│ `manager/`      │ `manager/impl/` │ `manager/impl/`                      │
└────────┬────────┴────────┬────────┴──────────────────────────┬───────────┘
         │                 │                                   │
         ▼                 ▼                                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                           SDK / Plugin Layer                               │
│  `dbsyncer-sdk` — SPI contracts, connector base, SQL templates, models    │
├──────────────┬───────────────┬──────────────┬────────────────────────────┤
│ ConnectorSvc │ Listener      │ PluginContext│ StorageService             │
│ SqlTemplate  │ SchemaResolver│ SyncRow      │ NotifyService              │
│ `sdk/spi/`   │ `sdk/listen/` │ `sdk/plugin/│ `sdk/storage/`             │
└──────┬───────┴───────┬───────┴──────┬───────┴──────────────┬─────────────┘
       │               │              │                      │
       ▼               ▼              ▼                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                       Connector Implementation Layer                       │
│  `dbsyncer-connector-*` — MySQL, PostgreSQL, Oracle, SQLServer, ES, etc.  │
├────────────┬───────────┬──────────┬──────────┬──────────┬────────────────┤
│ MySQL      │PostgreSQL │ Oracle   │SqlServer │Kafka     │ ElasticSearch  │
│ Connector  │Connector  │Connector │Connector │Connector │ Connector      │
│`conn/mysql│`conn/pg/`  │`conn/ora│`conn/mss │`conn/kaf │ `conn/es/`     │
└──────┬─────┴───────┬───┴────┬─────┴────┬─────┴────┬─────┴───────┬───────┘
       │             │        │          │          │             │
       ▼             ▼        ▼          ▼          ▼             ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                          Common / Storage Layer                            │
│          `dbsyncer-common` + `dbsyncer-storage` — shared utilities        │
├──────────────┬───────────────┬───────────────┬───────────────────────────┤
│ RetryPolicy  │ DispatchTask  │ AESUtil       │ Lucene Index / Disk        │
│ RetryInter.  │ BufferConfig  │ NetworkUtil   │ Binlog Protocol Buffers    │
│ `common/`    │ `common/`     │ `common/util/│ `storage/`                 │
└──────────────┴───────────────┴───────────────┴───────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Web Controllers | HTTP endpoints, session, Thymeleaf views | `dbsyncer-web/src/main/java/org/dbsyncer/web/controller/` |
| Business Services | Domain orchestration, permission checking, validation | `dbsyncer-biz/src/main/java/org/dbsyncer/biz/` |
| Parser Engine | SQL parsing, field conversion, buffer actuator, consumer | `dbsyncer-parser/src/main/java/org/dbsyncer/parser/` |
| Manager | Sync lifecycle (full/increment/full+increment pullers) | `dbsyncer-manager/src/main/java/org/dbsyncer/manager/` |
| SDK | SPI contracts, connector base, SQL templates, model objects | `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/` |
| Connectors | Per-database connector implementations (registered via SPI) | `dbsyncer-connector/dbsyncer-connector-*/` |
| Common | Shared utilities, dispatch, retry, configuration | `dbsyncer-common/src/main/java/org/dbsyncer/common/` |
| Storage | Lucene index, disk/MySQL persistence, binlog protocol | `dbsyncer-storage/src/main/java/org/dbsyncer/storage/` |

## Pattern Overview

**Overall:** Layered monolith with SPI-based plugin connectors

**Key Characteristics:**
- **Java SPI ServiceLoader** for connector registration — each connector jar registers itself in `META-INF/services/org.dbsyncer.sdk.spi.ConnectorService`
- **Buffer Actuator pattern** — producer/consumer queues smooth data flow between reader/writer with configurable batch sizes and intervals
- **Strategy/Template pattern** — `FullPuller`, `IncrementPuller`, `FullIncrementPuller` define different sync strategies dispatched by `ManagerFactory`
- **Command pattern** — `Preload`/`Persistence` command interfaces abstract model save/load operations
- **Factory pattern** — `ConnectorFactory` loads connectors via ServiceLoader and caches instances in a `ConcurrentHashMap`

## Layers

### 1. Web Layer (`dbsyncer-web`)
- **Purpose:** HTTP request handling, view rendering, static resources, security
- **Location:** `dbsyncer-web/src/main/java/org/dbsyncer/web/`
- **Contains:** Spring Boot `@Controller` classes, `@Configuration` (security, web, SSL redirect), Thymeleaf HTML templates, static JS/CSS
- **Depends on:** `dbsyncer-biz`, `dbsyncer-common`
- **Used by:** Browser clients, REST/OpenAPI consumers

### 2. Business Layer (`dbsyncer-biz`)
- **Purpose:** Orchestrate business operations, enforce permissions, validate inputs
- **Location:** `dbsyncer-biz/src/main/java/org/dbsyncer/biz/`
- **Contains:** Service interfaces and `impl/` implementations for Mapping, Connector, TableGroup, Monitor, DataSync, User, Permission
- **Depends on:** `dbsyncer-parser`, `dbsyncer-sdk`, `dbsyncer-manager`
- **Used by:** Web controllers

### 3. Parser / Engine Layer (`dbsyncer-parser`)
- **Purpose:** Core sync engine — parse source data, convert fields, apply transforms, buffer and route rows
- **Location:** `dbsyncer-parser/src/main/java/org/dbsyncer/parser/`
- **Contains:** `ParserComponentImpl` (orchestrates TableGroup execution), `ProfileComponentImpl` (config CRUD), `MessageService` (notifications), `BufferActuatorRouter` (routes to storage/meta actors), `ParserConsumer` (per-table consumer), DDL parser (`ddl/`), field convert utilities (`util/ConvertUtil.java`), notification providers (`notify/`)
- **Depends on:** `dbsyncer-sdk`, `dbsyncer-storage`, `dbsyncer-common`
- **Used by:** Business layer, Manager layer

### 4. Manager Layer (`dbsyncer-manager`)
- **Purpose:** Sync task lifecycle — start/stop mappings, select puller strategy
- **Location:** `dbsyncer-manager/src/main/java/org/dbsyncer/manager/`
- **Contains:** `ManagerFactory` (dispatcher), `Puller` interface, `FullPuller`/`IncrementPuller`/`FullIncrementPuller` implementations, `PreloadTemplate` (startup preload), `StandaloneProvider` (deployment strategy)
- **Depends on:** `dbsyncer-parser`, `dbsyncer-sdk`
- **Used by:** Business layer's `DataSyncService`

### 5. SDK Layer (`dbsyncer-sdk`)
- **Purpose:** Defines all SPI contracts, base classes, and domain models shared by all connectors
- **Location:** `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/`
- **Contains:**
  - SPI: `ConnectorService`, `LogService`, `DeploymentService`, `LicenseService`
  - Models: `Mapping`, `Meta`, `TableGroup`, `ConnectorConfig`, `ChangedEvent`
  - Connector infrastructure: `ConnectorInstance`, `AbstractConnector`, `DatabaseTemplate`, `SqlTemplate`
  - Listener framework: `Listener`, `ChangedEvent`, `QuartzFilter`
  - Plugin context: `PluginContext`, `ReaderContext`, `SyncRow`
  - Schema: `SchemaResolver`, `ValueMapper`
  - Storage SPI: `StorageService`, `Strategy` (Config/Data/Log)
- **Depends on:** `dbsyncer-common`
- **Used by:** All other layers

### 6. Connector Layer (`dbsyncer-connector`)
- **Purpose:** Database-specific connector implementations loaded dynamically
- **Location:** `dbsyncer-connector/dbsyncer-connector-{type}/`
- **Supports:** MySQL (CDC/binlog), PostgreSQL, Oracle, SQLServer (CDC), SQLite, ElasticSearch, Kafka, File
- **Depends on:** `dbsyncer-sdk`, `dbsyncer-common`, database drivers
- **Used by:** `ConnectorFactory` via SPI

### 7. Common Layer (`dbsyncer-common`)
- **Purpose:** Foundation utilities, shared config, dispatch, retry
- **Location:** `dbsyncer-common/src/main/java/org/dbsyncer/common/`
- **Contains:** `RetryPolicy`/`RetryInterceptor`, `DispatchTaskService`, `BufferActuatorConfig`, `AppConfig`, utility classes (`AESUtil`, `JsonUtil`, `NetworkUtil`, `UUIDUtil`)
- **Depends on:** Spring framework
- **Used by:** Every other module

### 8. Storage Layer (`dbsyncer-storage`)
- **Purpose:** Persistent storage abstraction — Lucene index on disk or MySQL backend
- **Location:** `dbsyncer-storage/src/main/java/org/dbsyncer/storage/`
- **Contains:** `DiskStorageService`, Lucene `Shard`/`Option`, `BinlogMessage` protobuf, `DocumentUtil`, `BinlogMessageUtil`
- **Depends on:** Apache Lucene, Protobuf, `dbsyncer-common`
- **Used by:** Parser layer (via `StorageService` SPI)

## Data Flow

### Full Sync Path

1. **User triggers sync** via UI or OpenAPI — `dbsyncer-web/.../MappingController.java` → `DataSyncService.start()` (`dbsyncer-biz/impl/DataSyncServiceImpl.java`)
2. **ManagerFactory.start()** selects the appropriate `Puller` based on `Mapping.model` — `dbsyncer-manager/ManagerFactory.java:50`
3. **FullPuller.start()** invokes `ParserComponent.executeTableGroup()` for each `TableGroup` — `dbsyncer-manager/impl/FullPuller.java`
4. **ParserComponentImpl** reads source via connector, applies field transforms, writes via connector in batches — `dbsyncer-parser/impl/ParserComponentImpl.java`
5. `ConnectorFactory.reader()` paginates source data → `ConnectorFactory.writeBatch()` flushes to target — `dbsyncer-connector/dbsyncer-connector-base/.../ConnectorFactory.java:200`
6. **Buffer actuators** (`BufferActuatorRouter` → `StorageBufferActuator` / `MetaBufferActuator`) buffer async writes — `dbsyncer-parser/flush/impl/`

### Incremental / CDC Sync Path

1. **Listener started** — Connector-specific `Listener` (e.g., `MySQLListener`) monitors source changes (binlog, timestamp polling) — `dbsyncer-connector/dbsyncer-connector-mysql/.../MySQLListener.java`
2. **ChangedEvent fired** — Listener produces `RowChangedEvent`/`DDLChangedEvent` — `dbsyncer-sdk/.../listener/event/`
3. **ParserConsumer processes events** — Converts source rows to target format, applies field mapping — `dbsyncer-parser/consumer/ParserConsumer.java`
4. **Batch writer** — Rows accumulate in queue, `StorageBufferActuator` flushes periodically — `dbsyncer-parser/flush/impl/StorageBufferActuator.java`
5. **ConnectorFactory.writeBatch()** writes to target connector

### Configuration Persistence Path

1. **User updates config** → Controller → Biz Service → `ProfileComponent.editConfigModel()`
2. `ProfileComponentImpl` serializes to JSON → `Persistence` command queued
3. `MetaBufferActuator` flushes to storage (disk Lucene or MySQL) — `dbsyncer-parser/flush/impl/MetaBufferActuator.java`

**State Management:**
- All config objects extend `ConfigModel` / `AbstractConfigModel` (serialized to JSON)
- Runtime state (Meta: begin time, status, error) stored in `Meta` objects with `MetaEnum` status tracking
- Connector instances cached in `ConnectorFactory.pool` (ConcurrentHashMap), keyed by cache key from `ConnectorService.getConnectorInstanceCacheKey()`
- Table group runtime context held in `TableGroupContext` / `TableGroupContextImpl`

## Key Abstractions

### ConnectorService (SPI Contract)
- **Purpose:** Defines the contract every data connector must implement
- **Location:** `dbsyncer-sdk/.../spi/ConnectorService.java`
- **Pattern:** Java SPI (`ServiceLoader`) — each connector jar provides `META-INF/services/org.dbsyncer.sdk.spi.ConnectorService`
- **Key Methods:** `connect()`, `disconnect()`, `getTable()`, `getMetaInfo()`, `reader()`, `writer()`, `insert()`, `update()`, `delete()`, `upsert()`, `getListener()`, `writerDDL()`
- **Implementations:** `MySQLConnector`, `PostgreSQLConnector`, `OracleConnector`, `SqlServerConnector`, `KafkaConnector`, `ElasticSearchConnector`, `SQLiteConnector`, `FileConnector`

### Puller (Sync Strategy)
- **Purpose:** Encapsulates a sync execution strategy
- **Location:** `dbsyncer-manager/Puller.java`
- **Pattern:** Strategy — dispatched by `ManagerFactory.getPuller()` using bean name convention (`{model}Puller`)
- **Implementations:** `FullPuller` (full table sync), `IncrementPuller` (CDC/incremental), `FullIncrementPuller` (full first, then incremental)

### ConnectorInstance
- **Purpose:** Represents a validated, cached connection to a data source
- **Location:** `dbsyncer-sdk/.../connector/ConnectorInstance.java`
- **Pattern:** Value object with lifecycle — created by `ConnectorService.connect()`, cached by `ConnectorFactory`, cloned for thread-safe use

### Buffer Actuator
- **Purpose:** Producer/consumer batch processing queue for data and metadata
- **Location:** `dbsyncer-parser/flush/impl/`
- **Pattern:** Buffered queue with periodic flush — `metaBufferActuator`, `storageBufferActuator`, routed by `BufferActuatorRouter`
- **Configuration:** `BufferActuatorConfig` (thread count, queue capacity, batch size, pull count, flush interval) — `dbsyncer-common/.../config/BufferActuatorConfig.java`

### Mapping / TableGroup / Meta
- **Purpose:** Core domain models representing a sync job
- **Location:** `dbsyncer-sdk/.../model/`
- **Mapping:** Defines source→target connector pair, sync model (full/increment), status
- **TableGroup:** Maps individual source table → target table with field mappings
- **Meta:** Runtime state — begin time, status (`RUNNING`/`ERROR`/`STOP`), error message

### Listener (CDC / Change Detection)
- **Purpose:** Abstract interface for detecting source changes
- **Location:** `dbsyncer-sdk/.../listener/Listener.java`, `AbstractListener.java`, `AbstractDatabaseListener.java`
- **Pattern:** Event-driven — `Listener` registers a `Watcher`, fires `ChangedEvent`s (`RowChangedEvent`, `DDLChangedEvent`, `ScanChangedEvent`, `CommonChangedEvent`)
- **Types:** Binlog (MySQL CDC), CDC timestamp polling (SQLServer), Quartz scheduled scan (all databases)

## Entry Points

### Web Application Entry
- **Location:** `dbsyncer-web/src/main/java/org/dbsyncer/web/Application.java`
- **Triggers:** JVM startup (`java -jar dbsyncer-web.jar`)
- **Responsibilities:** Boot Spring context, load build properties, configure management endpoints at `/app`, activate async/scheduling/caching

### REST Controllers
- **Location:** `dbsyncer-web/.../controller/`
- **Triggers:** HTTP requests from browser or API clients
- **Key Controllers:**
  - `IndexController` — dashboard, project groups, table groups
  - `ConnectorController` — CRUD for data source connectors
  - `MappingController` — CRUD for sync mappings (source→target pairs)
  - `OpenApiController` — programmatic API access
  - `MonitorController` — system metrics (CPU, memory)
  - `DataSourceController` — datasource browser
  - `ConfigController` / `ParamController` — system parameters
  - `SystemController` / `LicenseController` / `UserController` / `HelpDocController`

### Sync Execution Entry
- **Location:** `dbsyncer-manager/ManagerFactory.java:50`
- **Triggers:** User clicks "start" in UI, or OpenAPI call
- **Responsibilities:** Select Puller strategy by `Mapping.model`, delegate to `FullPuller.start()`, `IncrementPuller.start()`, or `FullIncrementPuller.start()`

### Connector SPI Registration
- **Location:** `dbsyncer-connector/*/src/main/resources/META-INF/services/org.dbsyncer.sdk.spi.ConnectorService`
- **Triggers:** JVM classpath scan via `ServiceLoader.load()` in `ConnectorFactory.init()` (`@PostConstruct`)
- **Responsibilities:** Register `ConnectorService` implementations into the connector pool

## Architectural Constraints

- **Threading:** Single Spring Boot JVM. Uses `@EnableAsync` for async methods, `@EnableScheduling` for Quartz tasks, and custom `BufferActuator` thread pools (`dbsyncer-common/.../dispatch/DispatchTaskService.java`) with configurable sizes in `application.properties`. Buffer actuators run dedicated threads consuming from queue.
- **Global state:** `ConnectorFactory.pool` (`ConcurrentHashMap<String, ConnectorInstance>`) — cached connections shared across requests. `ProfileComponentImpl` holds in-memory config cache. `BufferActuatorRouter` maintains singleton actuator instances.
- **Circular imports:** Module dependency is strictly DAG: `web` → `biz` → `{parser, manager}`, `{parser, manager}` → `sdk` → `common`. Connector modules depend only on `sdk` and `common`. No circular module dependencies.
- **SPI classloading:** Connectors are loaded via `Thread.currentThread().getContextClassLoader()` — classpath order matters for SPI resolution.
- **Serialization:** All `ConfigModel` subclasses serialize to JSON for persistence — field type changes must remain JSON-compatible.

## Error Handling

**Strategy:** Layered exception hierarchy with custom exception per module

**Patterns:**
- Each module defines its own exception: `ConnectorException` (`connector-base/`), `ManagerException` (`manager/`), `BizException` (`biz/`), `StorageException` (`storage/`), `SdkException` (`sdk/`), `CommonException` (`common/`), `ParserException` (`parser/`)
- All extend `RuntimeException` — bubble up through Spring's `@ExceptionHandler`
- `ManagerFactory.start()` catches puller exceptions, saves error state to `Meta` via `meta.saveState(MetaEnum.ERROR, ...)`, sends notification, then rethrows as `ManagerException` — `dbsyncer-manager/ManagerFactory.java:69-88`
- Connector write failures captured in `Result.addFailData()` — partial failures tracked per row — `dbsyncer-connector/dbsyncer-connector-base/.../ConnectorFactory.java:237-274`
- Retry mechanism: `RetryInterceptor` + `RetryPolicy` with configurable max attempts, exponential backoff, keyword-based matching — `dbsyncer-common/.../retry/`

## Cross-Cutting Concerns

- **Logging:** SLF4J with Log4j2 backend (`dbsyncer-web/src/main/resources/log4j2.xml`). Configured per-class: `LoggerFactory.getLogger(getClass())`. Log levels configurable in `application.properties` under `logging.level.*`.
- **Validation:** Spring `Assert` for parameter null/empty checks. `ConfigValidator` SPI per connector. `ConditionService` + `ConvertService` for web input validation.
- **Authentication:** Spring Security with `CustomPermissionEvaluator` (`dbsyncer-web/.../config/CustomPermissionEvaluator.java`), `AccessDeniedHandlerImpl`. Credentials stored in DB (configurable via `dbsyncer.storage.type`). Admin password reset via `dbsyncer.web.security.reset-pwd` flag.
- **Caching:** Spring `@EnableCaching` + `CacheService` / `CacheServiceImpl` (`dbsyncer-parser/impl/CacheServiceImpl.java`) wrapping `TableGroupContext` for fast runtime lookups.
- **Notifications:** Composite notification service (`dbsyncer-parser/notify/`) supporting Mail (`MailNotifyServiceProvider`) and DingTalk (`DingTalkNotifyServiceProvider`).

---

*Architecture analysis: 2026-07-02*

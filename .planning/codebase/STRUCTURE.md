# Codebase Structure

**Analysis Date:** 2026-07-02

## Directory Layout

```
dbsyncer/
├── pom.xml                           # Parent POM — all modules, dependency management
├── build.sh / build.cmd              # Build scripts (Maven clean package)
├── install.sh / install.cmd          # Install/start scripts
├── version.sh / version.cmd          # Version management scripts
│
├── dbsyncer-web/                     # Spring Boot web application (MAIN ENTRY)
│   ├── src/main/java/org/dbsyncer/web/
│   │   ├── Application.java          # @SpringBootApplication entry point
│   │   ├── Version.java              # Version constants
│   │   ├── config/                   # Spring configs (security, web, SSL)
│   │   └── controller/               # HTTP controllers (REST + Thymeleaf)
│   ├── src/main/resources/
│   │   ├── application.properties    # Central configuration
│   │   ├── log4j2.xml                # Logging config
│   │   ├── dbsyncer.p12              # SSL certificate keystore
│   │   ├── public/                   # Thymeleaf HTML templates
│   │   └── static/                   # Static assets (JS, CSS, images, plugins)
│   └── src/test/                     # Integration tests
│
├── dbsyncer-biz/                     # Business service layer
│   └── src/main/java/org/dbsyncer/biz/
│       ├── *Service.java             # Service interfaces
│       ├── impl/                     # Service implementations
│       ├── checker/                  # Business validators
│       ├── enums/                    # Business enums
│       ├── model/                    # View models / VOs
│       ├── task/                     # Background tasks (count tasks)
│       ├── vo/                       # Value objects
│       └── util/                     # Business utilities
│
├── dbsyncer-parser/                  # Core sync engine
│   └── src/main/java/org/dbsyncer/parser/
│       ├── ParserComponent.java      # Interface for sync execution
│       ├── impl/                     # Core implementations (Profile, Cache, Parser)
│       ├── command/                  # Preload/Persistence command pattern
│       ├── consumer/                 # ParserConsumer — per-table event consumer
│       ├── ddl/                      # DDL parsing and alteration strategies
│       ├── flush/                    # Buffer actuators (batch flush)
│       ├── notify/                   # Mail/DingTalk notification providers
│       ├── service/                  # Model persistence services
│       └── util/                     # Convert, template caching utilities
│
├── dbsyncer-manager/                 # Sync lifecycle management
│   └── src/main/java/org/dbsyncer/manager/
│       ├── ManagerFactory.java       # Puller dispatcher
│       ├── Puller.java               # Puller SPI interface
│       ├── impl/                     # FullPuller, IncrementPuller, FullIncrementPuller
│       └── deployment/               # Deployment strategies (StandaloneProvider)
│
├── dbsyncer-sdk/                     # SDK / Plugin framework
│   └── src/main/java/org/dbsyncer/sdk/
│       ├── spi/                      # SPI contracts (ConnectorService, LogService)
│       ├── model/                    # Domain models (Mapping, Meta, TableGroup, ...)
│       ├── connector/                # Connector infrastructure (Database, SqlTemplate)
│       ├── listener/                 # CDC/Change detection framework
│       ├── plugin/                   # Plugin context (PluginContext, SyncRow, ReaderContext)
│       ├── schema/                   # Schema resolver, ValueMapper
│       ├── storage/                  # Storage SPI (StorageService, Strategy)
│       ├── enums/                    # Core enums (ModelEnum, MetaEnum, DataTypeEnum)
│       ├── parser/                   # DDL parser utilities (IR converters)
│       ├── filter/                   # Event filters (timestamp, date)
│       ├── client/                   # Client helpers
│       ├── config/                   # SDK configs (CommandConfig, DDLConfig)
│       ├── notify/                   # Notification types
│       └── util/                     # SDK utilities
│
├── dbsyncer-connector/               # Connector implementations (parent pom)
│   ├── pom.xml                       # Connector aggregator
│   ├── dbsyncer-connector-base/      # ConnectorFactory + base abstractions
│   ├── dbsyncer-connector-mysql/     # MySQL connector (CDC binlog + polling)
│   ├── dbsyncer-connector-postgresql/ # PostgreSQL connector
│   ├── dbsyncer-connector-oracle/    # Oracle connector
│   ├── dbsyncer-connector-sqlserver/ # SQL Server connector (CDC)
│   ├── dbsyncer-connector-sqlite/    # SQLite connector
│   ├── dbsyncer-connector-elasticsearch/ # ElasticSearch connector
│   ├── dbsyncer-connector-kafka/     # Kafka connector
│   └── dbsyncer-connector-file/      # File connector (CSV/JSON)
│
├── dbsyncer-common/                  # Shared foundation
│   └── src/main/java/org/dbsyncer/common/
│       ├── config/                   # Shared configs (AppConfig, BufferActuatorConfig)
│       ├── dispatch/                 # Thread pool dispatcher
│       ├── retry/                    # Retry policy framework
│       ├── scheduled/                # Scheduled task support
│       ├── metric/                   # Metrics collection
│       ├── column/                   # Column utilities
│       ├── model/                    # Common models (Result)
│       ├── enums/                    # Common enums
│       └── util/                     # Utilities (AES, Json, Network, UUID, ...)
│
├── dbsyncer-storage/                 # Storage abstraction
│   └── src/main/java/org/dbsyncer/storage/
│       ├── impl/                     # DiskStorageService, SnowflakeIdWorker
│       ├── lucene/                   # Lucene index (Shard, Option)
│       ├── binlog/                   # Binlog protocol buffer messages
│       ├── enums/                    # Storage enums
│       └── util/                     # Document/Binlog utilities
│
├── doc/                              # Project documentation
│   ├── adr/                          # Architecture Decision Records
│   ├── design/                       # Design documents
│   ├── data-structure/               # Data structure docs
│   ├── implementation/               # Implementation docs
│   ├── sql/                          # SQL schema docs
│   ├── tests/                        # Test docs
│   └── *.md                          # Various design/release docs
│
├── docs/                             # External-facing documentation
├── helpdocs/                         # User help documentation
├── plugins/                          # External plugin directory (runtime)
└── .planning/                        # GSD planning artifacts
```

## Directory Purposes

### `dbsyncer-web/`
- **Purpose:** Spring Boot entry point, HTTP layer, all UI rendering
- **Contains:** Controllers, Spring configs, Thymeleaf templates, static JS/CSS, application.properties
- **Key files:**
  - `src/main/java/org/dbsyncer/web/Application.java` — main method
  - `src/main/java/org/dbsyncer/web/controller/` — all REST controllers
  - `src/main/resources/application.properties` — central configuration

### `dbsyncer-biz/`
- **Purpose:** Business logic orchestration — between web controllers and core engine
- **Contains:** Service interfaces, implementations, business validators, VOs, background tasks
- **Key files:**
  - `src/main/java/org/dbsyncer/biz/DataSyncService.java` — sync start/stop orchestration
  - `src/main/java/org/dbsyncer/biz/MappingService.java` — mapping CRUD
  - `src/main/java/org/dbsyncer/biz/ConnectorService.java` — connector CRUD
  - `src/main/java/org/dbsyncer/biz/impl/` — all implementations

### `dbsyncer-parser/`
- **Purpose:** Core sync processing engine — parsing, transformation, buffering, DDL handling
- **Contains:** Parser component, buffer actuators, consumer, DDL strategies, notification, caching, model persistence
- **Key files:**
  - `src/main/java/org/dbsyncer/parser/ParserComponent.java` — interface
  - `src/main/java/org/dbsyncer/parser/impl/ParserComponentImpl.java` — core orchestrator
  - `src/main/java/org/dbsyncer/parser/impl/ProfileComponentImpl.java` — config CRUD
  - `src/main/java/org/dbsyncer/parser/flush/impl/BufferActuatorRouter.java` — routes to flush actors
  - `src/main/java/org/dbsyncer/parser/consumer/ParserConsumer.java` — per-table consumer
  - `src/main/java/org/dbsyncer/parser/ddl/DDLParser.java` — DDL change parsing

### `dbsyncer-manager/`
- **Purpose:** Sync task lifecycle — strategy selection and execution
- **Contains:** Puller interface, 3 strategy implementations, deployment provider
- **Key files:**
  - `src/main/java/org/dbsyncer/manager/ManagerFactory.java` — central dispatcher
  - `src/main/java/org/dbsyncer/manager/impl/FullPuller.java` — full table sync
  - `src/main/java/org/dbsyncer/manager/impl/IncrementPuller.java` — CDC/incremental sync
  - `src/main/java/org/dbsyncer/manager/impl/FullIncrementPuller.java` — full then incremental

### `dbsyncer-sdk/`
- **Purpose:** SDK framework — all SPI contracts, domain models, shared infrastructure for connectors
- **Contains:** SPI interfaces, domain models, connector base classes, listener framework, storage SPI, schema resolvers
- **Key files:**
  - `src/main/java/org/dbsyncer/sdk/spi/ConnectorService.java` — SPI contract
  - `src/main/java/org/dbsyncer/sdk/model/` — Mapping, Meta, TableGroup, ConnectorConfig, etc.
  - `src/main/java/org/dbsyncer/sdk/connector/database/` — database connector base
  - `src/main/java/org/dbsyncer/sdk/listener/` — CDC listener framework

### `dbsyncer-connector/`
- **Purpose:** Database-specific connector plugins
- **Contains:** One sub-module per supported database/technology, each with:
  - Connector implementation (extends `AbstractConnector` / implements `ConnectorService`)
  - CDC/Listener implementation (if incremental sync supported)
  - Schema resolver (for type mapping)
  - `META-INF/services/org.dbsyncer.sdk.spi.ConnectorService` — SPI registration
- **Key sub-modules:**
  - `dbsyncer-connector-mysql/` — MySQL (binlog CDC + polling)
  - `dbsyncer-connector-postgresql/` — PostgreSQL
  - `dbsyncer-connector-oracle/` — Oracle
  - `dbsyncer-connector-sqlserver/` — SQL Server CDC
  - `dbsyncer-connector-elasticsearch/` — ElasticSearch
  - `dbsyncer-connector-kafka/` — Kafka
  - `dbsyncer-connector-base/` — `ConnectorFactory` (runtime lookup)

### `dbsyncer-common/`
- **Purpose:** Foundation shared by all modules
- **Contains:** Utilities, retry framework, dispatch service, common configs, exception base
- **Key files:**
  - `src/main/java/org/dbsyncer/common/util/` — AESUtil, JsonUtil, NetworkUtil, SpringContextUtil, etc.
  - `src/main/java/org/dbsyncer/common/retry/` — RetryPolicy, RetryInterceptor
  - `src/main/java/org/dbsyncer/common/dispatch/` — DispatchTaskService (thread pool)
  - `src/main/java/org/dbsyncer/common/config/` — AppConfig, BufferActuatorConfig, RetryConfig, StorageConfig

### `dbsyncer-storage/`
- **Purpose:** Data persistence abstraction — Lucene-based index on disk, or MySQL backend
- **Contains:** Disk storage, Lucene shard management, Binlog protocol buffers, document utilities
- **Key files:**
  - `src/main/java/org/dbsyncer/storage/impl/DiskStorageService.java` — disk-backed storage
  - `src/main/java/org/dbsyncer/storage/lucene/Shard.java` — Lucene shard
  - `src/main/java/org/dbsyncer/storage/binlog/` — protobuf-generated binlog messages

## Key File Locations

### Entry Points
- `dbsyncer-web/src/main/java/org/dbsyncer/web/Application.java`: Spring Boot main class
- `dbsyncer-web/src/main/resources/application.properties`: All runtime configuration
- `dbsyncer-manager/src/main/java/org/dbsyncer/manager/ManagerFactory.java`: Sync execution dispatcher

### Configuration
- `dbsyncer-web/src/main/resources/application.properties`: Server port (18686), SSL, dispatch threads, buffer queues, storage type, retry, notifications
- `dbsyncer-web/src/main/resources/log4j2.xml`: Log4j2 configuration
- `dbsyncer-web/src/main/java/org/dbsyncer/web/config/`: Security, web, SSL redirect configs

### Core Logic
- `dbsyncer-parser/src/main/java/org/dbsyncer/parser/impl/ParserComponentImpl.java`: Sync processing orchestrator
- `dbsyncer-connector/dbsyncer-connector-base/src/main/java/org/dbsyncer/connector/base/ConnectorFactory.java`: Connector pool and write dispatcher
- `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/spi/ConnectorService.java`: SPI contract all connectors implement

### Testing
- `dbsyncer-web/src/test/java/`: Web integration tests
- Individual modules have `src/test/java/` directories (may be sparse)

### Build Scripts
- `build.sh` / `build.cmd`: Maven build scripts
- `install.sh` / `install.cmd`: Deployment scripts
- `pom.xml` (root): Maven multi-module parent POM

## Naming Conventions

### Files
- **Module prefix:** Every Maven module starts with `dbsyncer-` (e.g., `dbsyncer-web`, `dbsyncer-biz`)
- **Connector sub-module prefix:** `dbsyncer-connector-{technology}` (e.g., `dbsyncer-connector-mysql`)
- **Java class naming:**
  - Services: `{Domain}Service` (interface) + `{Domain}ServiceImpl` (impl) — e.g., `MappingService` / `MappingServiceImpl`
  - Controllers: `{Domain}Controller` — e.g., `MappingController`, `ConnectorController`
  - Pullers: `{Strategy}Puller` — e.g., `FullPuller`, `IncrementPuller`
  - Connectors: `{Database}Connector` — e.g., `MySQLConnector`, `PostgreSQLConnector`
  - Listeners: `{Database}Listener` — e.g., `MySQLListener`
  - Exceptions: `*Exception` — e.g., `ConnectorException`, `ManagerException`, `BizException`
  - Enums: `{Domain}Enum` — e.g., `ModelEnum`, `MetaEnum`, `DataTypeEnum`
  - Utilities: `{Function}Util` — e.g., `JsonUtil`, `UUIDUtil`, `ConvertUtil`

### Directories
- **Java packages:** `org.dbsyncer.{module}` (e.g., `org.dbsyncer.web`, `org.dbsyncer.biz`, `org.dbsyncer.parser`)
- **Sub-packages:** `impl/` (implementations), `config/` (Spring configs), `util/` (utilities), `enums/` (enums), `model/` (domain/VOs), `command/` (command pattern)
- **Connector structure:** Each connector has `validator/`, `cdc/` (listeners), `converter/` (DDL IR converters), `schema/` (type mappers), `deserializer/` (binlog), `storage/` (connector-specific storage)

## Where to Add New Code

### New Sync Mapping Feature or Business Logic
- **Interface/Service:** `dbsyncer-biz/src/main/java/org/dbsyncer/biz/`
- **Implementation:** `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/`
- **Controller:** `dbsyncer-web/src/main/java/org/dbsyncer/web/controller/`
- **Tests:** Corresponding `src/test/java/` in the module

### New Connector (e.g., new database support)
- **Create module:** `dbsyncer-connector/dbsyncer-connector-{name}/`
- **Register SPI:** Add to `META-INF/services/org.dbsyncer.sdk.spi.ConnectorService` with implementation class name
- **Parent POM:** Add `<module>` to `dbsyncer-connector/pom.xml`
- **Root POM:** Add dependency in relevant module POMs
- **Standard structure within module:**
  - `{Database}Connector.java` implementing `ConnectorService`
  - `{Database}Listener.java` extending `AbstractDatabaseListener` (if CDC supported)
  - `{Database}ConfigValidator.java` implementing `ConfigValidator`
  - `schema/{Database}SchemaResolver.java`
  - `converter/{Database}ToIRConverter.java` + `IRTo{Database}Converter.java`
- **Reference:** See `dbsyncer-connector-mysql/` as the most complete example

### New Sync Strategy / Puller
- **Interface:** Implement `Puller` from `dbsyncer-manager/Puller.java`
- **Location:** `dbsyncer-manager/src/main/java/org/dbsyncer/manager/impl/`
- **Naming:** Bean name must match `{model}Puller` convention (e.g., `FullPuller` bean = `fullPuller`)

### New Utility or Common Functionality
- **Location:** `dbsyncer-common/src/main/java/org/dbsyncer/common/util/`
- **Naming:** `{Function}Util.java`

### New DDL / Schema Change Handling
- **Location:** `dbsyncer-parser/src/main/java/org/dbsyncer/parser/ddl/`
- **Pattern:** Strategy pattern — add new `AlterStrategy` implementation in `ddl/alter/`

### New Notification Channel
- **Location:** `dbsyncer-parser/src/main/java/org/dbsyncer/parser/notify/`
- **Pattern:** `CompositeNotifyService` aggregates providers — add new provider

### New Domain Model
- **Location:** `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/model/`
- **Extends:** `AbstractConfigModel` if it needs JSON persistence

### New Field Type / Schema Resolution
- **Location:** `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/schema/` or connector-specific `schema/`
- **Pattern:** Add `ValueMapper` implementation, register in connector's `SchemaResolver`

## Special Directories

### `doc/`
- **Purpose:** Project documentation — ADRs, design docs, implementation details, release notes
- **Committed:** Yes
- **Contents:** Markdown files documenting key decisions, designs, SQL schemas, test plans

### `docs/`
- **Purpose:** External-facing documentation site
- **Committed:** Yes

### `helpdocs/`
- **Purpose:** User help documentation (served via `HelpDocController`)
- **Committed:** Yes (subset in `dbsyncer-web/helpdocs/`)

### `plugins/`
- **Purpose:** Runtime plugin directory for external plugins
- **Committed:** No (empty directory)
- **Generated:** Populated at deployment time

### `logs/`
- **Purpose:** Application logs directory
- **Committed:** No (gitignored in practice)
- **Generated:** Created at runtime by Log4j2

### `dbsyncer-web/data/`
- **Purpose:** Storage directory for disk-based persistence (when `dbsyncer.storage.type=disk`)
- **Committed:** No
- **Generated:** Created at runtime

### `dbsyncer-web/src/main/resources/static/`
- **Purpose:** Static web assets (JS, CSS, images, third-party plugins)
- **Committed:** Yes
- **Contains:** Bootstrap, jQuery, ECharts, iCheck, and project-specific JS per feature module

### `dbsyncer-web/src/main/resources/public/`
- **Purpose:** Thymeleaf HTML view templates
- **Committed:** Yes
- **Structure:** Mirrors controller feature domains — `index/`, `connector/`, `mapping/`, `datasource/`, `monitor/`, `system/`, `user/`

---

*Structure analysis: 2026-07-02*

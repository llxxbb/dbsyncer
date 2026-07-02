# External Integrations

**Analysis Date:** 2026-07-02

## Integrations Overview

DBSyncer is a data synchronization/migration platform. Its primary integrations are the **source and target data systems** it connects to via pluggable connectors. Secondary integrations cover **notifications** and **application metadata storage**.

## Data Sources & Targets (Connectors)

Each connector lives under `dbsyncer-connector/dbsyncer-connector-<name>/`. The `dbsyncer-connector-base` module aggregates them into a single aggregator dependency.

### Relational Databases

**MySQL:**
- Purpose: Source and/or target for data sync; also used as application metadata store
- Driver: `mysql:mysql-connector-java:8.0.21`
- CDC (change-data-capture): `com.zendesk:mysql-binlog-connector-java:0.30.1` for binlog streaming
- Storage implementation: `dbsyncer-connector/dbsyncer-connector-mysql/src/main/java/org/dbsyncer/connector/mysql/storage/MySQLStorageService.java`
- Spatial types: `com.vividsolutions:jts:1.13` for geometry conversion
- Config properties: `dbsyncer.storage.mysql.url`, `dbsyncer.storage.mysql.username`, `dbsyncer.storage.mysql.password` (`dbsyncer-web/src/main/resources/application.properties:79-81`)

**PostgreSQL:**
- Purpose: Source and/or target
- Driver: `org.postgresql:postgresql:42.3.3`
- Spatial: `net.postgis:postgis-jdbc:2.5.1` + JTS for PostGIS geometry
- Location: `dbsyncer-connector/dbsyncer-connector-postgresql/`

**Oracle:**
- Purpose: Source and/or target
- Driver: `com.oracle.database.jdbc:ojdbc8:21.6.0.0` + `orai18n` for i18n
- Spatial: JTS geometry conversion
- Location: `dbsyncer-connector/dbsyncer-connector-oracle/`

**SQL Server:**
- Purpose: Source and/or target (supports both polling and Change Tracking via `sqlserver.ct.poll.interval.ms`)
- Driver: `com.microsoft.sqlserver:mssql-jdbc:9.4.1.jre8`
- Hashing: `commons-codec:1.15` for MD5 (change tracking)
- Location: `dbsyncer-connector/dbsyncer-connector-sqlserver/`

**SQLite:**
- Purpose: Source and/or target
- Driver: `org.xerial:sqlite-jdbc`
- Location: `dbsyncer-connector/dbsyncer-connector-sqlite/`

### NoSQL / Search

**Elasticsearch:**
- Purpose: Target (index writes via bulk API)
- Client: `org.elasticsearch.client:elasticsearch-rest-high-level-client` (version managed by Spring Boot BOM)
- Custom REST converter: `dbsyncer-connector/dbsyncer-connector-elasticsearch/src/main/java/org/dbsyncer/connector/elasticsearch/api/RequestConverters.java`
- Lightweight high-level client wrapper: `dbsyncer-connector/dbsyncer-connector-elasticsearch/src/main/java/org/dbsyncer/connector/elasticsearch/api/EasyRestHighLevelClient.java`
- Health check integration: disabled by default (`management.health.elasticsearch.enabled=false` in `Application.java:48`)

### Messaging

**Apache Kafka:**
- Purpose: Source and/or target
- Client: `org.apache.kafka:kafka-clients:0.9.0.0`
- Producer: `dbsyncer-connector/dbsyncer-connector-kafka/src/main/java/org/dbsyncer/connector/kafka/KafkaClient.java`
- Custom serializers: `MapToJsonSerializer`, `JsonToMapDeserializer`
- Location: `dbsyncer-connector/dbsyncer-connector-kafka/`

### File

**File System:**
- Purpose: Source and/or target for flat-file data exchange
- No external dependencies (uses `java.io` APIs)
- Location: `dbsyncer-connector/dbsyncer-connector-file/`

## Data Storage (Application Metadata)

**Disk Storage (default):**
- Path: `data/` directory relative to DBSYNCER_HOME (gitignored)
- Implementation: `dbsyncer-storage/src/main/java/org/dbsyncer/storage/impl/DiskStorageService.java`
- Backend: Lucene with SmartCN Chinese analyzer for full-text search
- Binlog serialization: Protobuf (`dbsyncer-storage/src/main/java/org/dbsyncer/storage/binlog/`)

**MySQL Storage (recommended for production):**
- Config: `dbsyncer.storage.type=mysql`
- Connection via `dbsyncer.storage.mysql.*` properties
- Implementation: `dbsyncer-connector/dbsyncer-connector-mysql/src/main/java/org/dbsyncer/connector/mysql/storage/MySQLStorageService.java`
- Reuses the MySQL JDBC driver

**Caching:**
- Provider: Caffeine (in-memory)
- Configured via `spring-boot-starter-cache` + `@EnableCaching` (`Application.java:24`)

## Authentication & Identity

**Auth Provider:** Custom in-application (no external IdP)
- Implementation: `dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java` (extends `WebSecurityConfigurerAdapter`)
- Mechanism: Form-based login with Spring Security
- Password hashing: SHA-1 (`SHA1Util.b64_sha1`)
- Session management: `HttpSession` with `maximum-sessions=1` per user (session fixation via `migrateSession`)
- Cookie: `JSESSIONID`, deleted on logout
- Custom permission evaluator: `dbsyncer-web/src/main/java/org/dbsyncer/web/config/CustomPermissionEvaluator.java`
- Access denied handler: `dbsyncer-web/src/main/java/org/dbsyncer/web/config/AccessDeniedHandlerImpl.java`
- Password reset: admin flag `dbsyncer.web.security.reset-pwd=true` (dangerous; must disable after use)

## Notifications

### Email (SMTP)

- Implementation: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/notify/MailNotifyServiceProvider.java`
- Transport: `javax.mail` (JavaMail API) via `spring-boot-starter-mail`
- SMTP host: hardcoded to `smtp.qq.com` (`MailNotifyServiceProvider.java:60`)
- SSL: `MailSSLSocketFactory` with trust-all (line 63-64)
- Config: `dbsyncer.plugin.notify.mail.enabled`, `dbsyncer.plugin.notify.mail.username`, `dbsyncer.plugin.notify.mail.password` (`application.properties:101-105`)
- Trigger conditions: failed data sync or manual driver stop; default interval 10 min

### DingTalk (webhook)

- Implementation: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/notify/DingTalkNotifyServiceProvider.java`
- HTTP client: OkHttp 3.14.9 (`DingTalkNotifyServiceProvider.java:26-30`)
- Payload: Markdown message via DingTalk robot webhook API
- Config: `NotifyConfig.DingTalkConfig` nested in `dbsyncer-parser/src/main/java/org/dbsyncer/parser/notify/NotifyConfig.java` (webhookUrl, notifyTypes)
- Composed together with mail in: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/notify/CompositeNotifyService.java`

## Monitoring & Observability

**Metrics/Health:**
- Framework: Spring Boot Actuator
- Base path: `/app` (`Application.java:48`)
- Exposure: all endpoints (`management.endpoints.web.exposure.include=*`, `Application.java:49`)
- Health details: always shown (`management.endpoint.health.show-details=always`)

**Logging:**
- Framework: Log4j2 v2.17.1 (Logback excluded)
- Config: `dbsyncer-web/src/main/resources/log4j2.xml`
- Appenders: Console + rolling file (info/warn/error) with size + time policies
- Log directory: `logs/` (or `${LOG_HOME}` env var)
- Rotation: 128 MB (info), 64 MB (warn/error); retains up to 24 months
- Pattern: `%d{HH:mm:ss:SSS} [%thread] [%p] - %c{1.} %M:%L - %m%n`

**Heap dumps / Crash logs:**
- `-XX:+HeapDumpOnOutOfMemoryError`
- Dump path: `logs/` and `logs/hs_err_pid_%p.log` (`startup.sh:50`)

## CI/CD & Deployment

**Hosting:** Self-hosted standalone JVM process or Docker container; no cloud provider integration

**CI Pipeline:**
- No `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile`, or other CI config detected
- Build is manual via `build.sh` / `build.cmd` (Maven clean + package; tests skipped)
- Versioning script: `version.sh` uses `mvn versions:set`
- No automated testing gate

**Docker:**
- Dockerfile: `dbsyncer-web/Dockerfile` (FROM `java:8`, exposes `18686`)
- Startup script for container: `dbsyncer-web/src/main/assembly/bin/startup-docker.sh`
- Volumes: `/app/dbsyncer/conf`, `/app/dbsyncer/data`, `/app/dbsyncer/plugins`

**Distribution artifact:** `dbsyncer-<version>-bin.zip` produced by Maven Assembly plugin; contains `bin/`, `conf/`, `lib/`, `plugins/`

## Webhooks & External Callbacks

**Outgoing:**
- DingTalk robot webhook (Markdown payload; no signature/secret documented in code) — `DingTalkNotifyServiceProvider.java`

**Incoming:**
- None detected (no webhook receiver endpoints in the codebase)

## Environment Configuration

**Required configuration (non-secrets):**
- `server.port` — HTTP port (default `18686`)
- `dbsyncer.storage.type` — `disk` or `mysql`
- `dbsyncer.web.worker.id` — Unique node ID when clustered
- `dbsyncer.common.dispatch.thread-core-size` / max / queue — Scheduler thread pool tuning
- `dbsyncer.parser.general.*` / `dbsyncer.parser.table.group.*` — Parser buffer tunables
- `dbsyncer.storage.thread-core-size` and friends — Storage writer pool

**Secrets location:**
- Configured in `application.properties` (committed in repo — see values at lines 80-81, 103-105)
- SSL keystore password: `dbsyncer-web/src/main/resources/dbsyncer.p12` (password `dbsyncer` referenced via `server.ssl.key-store-password` in `application.properties:10`)
- **Note:** Production deployments override via external `conf/application.properties` (excluded from JAR per `maven-jar-plugin` config and placed in separate volume in Docker)

## Native Agent (Optional)

- Purpose: Java agent for native encryption (licensed feature)
- Platform-specific binaries distributed in `bin/`:
  - `libDBSyncer.so` (Linux x86_64)
  - `libDBSyncer_aarch64.so` (Linux arm64)
  - `libDBSyncer.dylib` (macOS)
- Loaded via `-agentpath:` if present (`startup.sh:39-41`)

## Integration Summary Table

| Integration | Type | Module | Status |
|-------------|------|--------|--------|
| MySQL | DB driver + CDC + storage | `dbsyncer-connector-mysql` | Primary |
| PostgreSQL | DB driver | `dbsyncer-connector-postgresql` | Active |
| Oracle | DB driver | `dbsyncer-connector-oracle` | Active |
| SQL Server | DB driver + Change Tracking | `dbsyncer-connector-sqlserver` | Active |
| SQLite | DB driver | `dbsyncer-connector-sqlite` | Active |
| Elasticsearch | REST client (v7.x) | `dbsyncer-connector-elasticsearch` | Active |
| Apache Kafka | Client | `dbsyncer-connector-kafka` | Active |
| File | Local FS | `dbsyncer-connector-file` | Active |
| Email (SMTP, QQ Mail) | Notification | `dbsyncer-parser` | Optional |
| DingTalk | Webhook notification | `dbsyncer-parser` | Optional |
| Spring Security | In-app auth | `dbsyncer-web` | Enabled |
| Spring Boot Actuator | Health/metrics | `dbsyncer-web` | Enabled |
| Log4j2 | Logging | all modules | Active |
| Caffeine | In-memory cache | `dbsyncer-common` | Active |
| Lucene + SmartCN | Disk storage FTS | `dbsyncer-storage` | Active |
| Protobuf | Binlog serialization | `dbsyncer-storage` | Active |

---

*Integration audit: 2026-07-02*

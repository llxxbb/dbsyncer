# Technology Stack

**Analysis Date:** 2026-07-02

## Languages

**Primary:**
- Java 1.8 (JDK 8) - Core application language across all modules (`pom.xml:29-34`)

**Secondary:**
- SQL - Used by database connectors (`dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/`)
- HTML/JavaScript (ES5) - Frontend view templates (`dbsyncer-web/src/main/resources/public/`, `dbsyncer-web/src/main/resources/static/`)
- Bash/Shell - Build and startup scripts (`build.sh`, `version.sh`)
- Batch - Windows build scripts (`build.cmd`, `version.cmd`)

## Runtime

**Environment:**
- JVM 1.8+ (HotSpot)
- Heap settings: `-Xms3800m -Xmx3800m -Xmn1500m -Xss512k` (`dbsyncer-web/src/main/assembly/bin/startup.sh:23`)
- GC: CMS (`-XX:+UseConcMarkSweepGC -XX:+UseParNewGC`) (`dbsyncer-web/src/main/assembly/bin/startup.sh:47-50`)
- Containerized as Docker image (optional): `dbsyncer-web/Dockerfile` based on `java:8`

**Package Manager:**
- Apache Maven 3.x
  - Multi-module POM (`pom.xml:14-23`)
  - Build skips tests by default (`build.sh:4`)
- No `mvnw` (Maven Wrapper) used.
- Lockfile: No explicit lockfile; Maven `pom.xml` dependency declarations resolved at build time.

## Frameworks

**Core:**
- Spring Boot 2.5.14 — Web application foundation (`pom.xml:35`; `dbsyncer-web/src/main/java/org/dbsyncer/web/Application.java:25`)
  - `@SpringBootApplication(scanBasePackages = "org.dbsyncer")`
  - `@EnableAsync`, `@EnableScheduling`, `@EnableCaching` (Application.java:22-24)

**Testing:**
- JUnit 4 (4.13.1) — Legacy tests (`pom.xml:51`)
- JUnit 5 Jupiter (5.7.2 BOM) — Modern tests (`pom.xml:52`); via `junit-vintage-engine` for JUnit 4 compat
- Spring Boot Test (`dbsyncer-web/pom.xml:71`)

**Build/Dev:**
- Maven Compiler Plugin 3.8.0 — Java 1.8 source/target (`dbsyncer-web/pom.xml:116-122`)
- Maven Assembly Plugin 3.3.0 — ZIP distribution packaging (`dbsyncer-web/pom.xml:138-156`)
- Maven Jar Plugin 3.3.0 — Executable JAR with excluded config (`dbsyncer-web/pom.xml:125-135`)
- Spring Boot Maven Plugin 2.7.5 — Build info generation (`dbsyncer-web/pom.xml:158-169`)
- Maven Surefire Plugin 2.22.2 — Test runner (`dbsyncer-common/pom.xml:82-89`)

## Key Dependencies

**Critical:**
- `spring-boot-starter-web` — Embedded Tomcat, Spring MVC REST controllers (`dbsyncer-web/pom.xml:23`)
- `spring-boot-starter-security` — Authentication & authorization (`dbsyncer-web/pom.xml:58`)
- `spring-boot-starter-thymeleaf` — Server-side HTML rendering (`dbsyncer-web/pom.xml:45`)
- `spring-boot-starter-cache` + Caffeine — Session/data caching (`dbsyncer-common/pom.xml:36-47`)
- `spring-boot-starter-actuator` — Metrics, health checks, endpoints at `/app/*` (`Application.java:48-51`)
- `spring-boot-starter-mail` — Email notification (SMTP) (`dbsyncer-parser/pom.xml:37`)
- `com.squareup.okhttp3:okhttp:3.14.9` — DingTalk webhook client (`dbsyncer-parser/pom.xml:49-53`)
- `com.github.jsqlparser:jsqlparser:4.9` — SQL statement parsing (`dbsyncer-sdk/pom.xml:49-51`)
- `org.antlr:antlr4-runtime:4.7.2` — Grammar parsing for custom SQL dialects (`pom.xml:45`)
- `com.google.protobuf:protobuf-java:3.21.1` — Binlog data serialization (`dbsyncer-storage/pom.xml:22-25`)

**Infrastructure:**
- `org.apache.lucene:lucene-analyzers-smartcn:8.8.0` — Chinese full-text indexing for disk storage (`pom.xml:38`; `dbsyncer-storage/pom.xml:28-31`)
- `mysql:mysql-connector-java:8.0.21` — MySQL driver (`pom.xml:41`)
- `com.oracle.database.jdbc:ojdbc8:21.6.0.0` — Oracle driver (`pom.xml:39`)
- `com.microsoft.sqlserver:mssql-jdbc:9.4.1.jre8` — SQL Server driver (`pom.xml:44`)
- `org.postgresql:postgresql:42.3.3` — PostgreSQL driver (`pom.xml:46`)
- `net.postgis:postgis-jdbc:2.5.1` — PostGIS spatial types (`pom.xml:47`)
- `org.xerial:sqlite-jdbc` (from Spring Boot BOM) — SQLite driver (`dbsyncer-connector/dbsyncer-connector-sqlite/pom.xml:25-27`)
- `com.zendesk:mysql-binlog-connector-java:0.30.1` — MySQL CDC binlog streaming (`pom.xml:43`)
- `org.elasticsearch.client:elasticsearch-rest-high-level-client` (from Spring Boot BOM) — ES REST client (`dbsyncer-connector/dbsyncer-connector-elasticsearch/pom.xml:25-33`)
- `org.apache.kafka:kafka-clients:0.9.0.0` — Kafka producer/consumer (`pom.xml:48`)
- `com.vividsolutions:jts:1.13` — Geometry/spatial type conversions for multiple connectors (`dbsyncer-connector/dbsyncer-connector-mysql/pom.xml:34-36`)
- `commons-fileupload:1.4` + `commons-io:2.7` + `commons-lang3` — Apache utility libraries (`pom.xml:36-37`; `dbsyncer-common/pom.xml:51-57`)
- Log4j2 2.17.1 — Logging backend (replaces Logback) (`pom.xml:50`)
- Jackson (`jackson-databind`, `jackson-annotations`) — JSON serialization (replacing fastjson2) (`dbsyncer-common/pom.xml:59-69`)

**Frontend (static resources):**
- jQuery 1.11.3 (`dbsyncer-web/src/main/resources/static/plugins/js/jquery/jquery-1.11.3.min.js`)
- Bootstrap 3.x (CSS/JS) (`dbsyncer-web/src/main/resources/static/plugins/js/bootstrap/`)
- ECharts (Apache ECharts) — Charts in monitoring UI (`dbsyncer-web/src/main/resources/static/plugins/js/echarts/`)
- Bootstrap plugins: `bootstrap-dialog`, `bootstrap-select`, `bootstrap-switch`, `bootstrap-tagsinput`, `bootstrap-fileinput`, `bootstrap-growl`
- Misc: `icheck`, `qrcodejs`, `formValidate`, `tablednd`, `loading-plus`, `sql-formatter`

## Configuration

**Environment:**
- `dbsyncer-web/src/main/resources/application.properties` — Primary config file (port, storage, retry, plugin settings)
- `dbsyncer-web/src/main/resources/dbsyncer.p12` — SSL keystore (PKCS12)
- `dbsyncer-web/src/main/resources/log4j2.xml` — Log4j2 XML config
- Config injected at runtime via `-Dspring.config.location` in startup scripts
- `.env` files: Not present (no dotenv convention in this project)

**Key configurable parameters (`application.properties`):**
- `server.port` = `18686` — HTTP listen port
- `dbsyncer.storage.type` = `disk|mysql` — Storage engine choice
- `dbsyncer.storage.mysql.url/username/password` — Production storage MySQL connection
- `dbsyncer.retry.global.*` — Retry policy knobs (max attempts, backoff, keyword matching)
- `dbsyncer.plugin.notify.mail.enabled` + `dbsyncer.plugin.notify.mail.username/password` — Email notifications
- `sqlserver.ct.poll.interval.ms` — SQL Server Change Tracking poll interval
- `helpdoc.path=./helpdocs` — Help documentation storage path

**Build:**
- `pom.xml` — Root Maven POM (parent for all modules)
- `dbsyncer-web/src/main/assembly/assembly-zip.xml` — Distribution ZIP layout
- `build.sh` / `build.cmd` — Standard build scripts (Maven clean + package, skip tests)
- `version.sh` / `version.cmd` — Version bump script using `mvn versions:set`
- `install.sh` — `mvn install -Dmaven.test.skip=true`

**Maven repositories:**
- Maven Central (default)
- Alibaba Cloud mirror (`https://maven.aliyun.com/repository/google`)
- DataNucleus repository (`http://www.datanucleus.org/downloads/maven2/`)
- Atlassian packages (`https://packages.atlassian.com/mvn/maven-external/`)

## Platform Requirements

**Development:**
- JDK 8+ (OpenJDK or Oracle)
- Apache Maven 3.6+
- Access to configured Maven repositories (Alibaba mirrors for China-based builds)
- OS: Linux, macOS, Windows (`startup.sh` references native JNI libs per OS)

**Production:**
- JDK 8+ with tuned JVM options (3.8G heap; CMS GC)
- OS: Linux (x86_64 or aarch64) or macOS — JNI agent `libDBSyncer.so` for native encryption; Windows via `startup.bat`
- Optional: Docker (`docker build --build-arg DBSYNCER_VERSION=...`)
- Network access to configured target databases and notification endpoints
- Disk or MySQL for application metadata storage

**Deployment mode:**
- Standalone JAR deployment via startup script: `java [opts] org.dbsyncer.web.Application`
- Optional Docker: `dbsyncer-web/Dockerfile` (port `18686`)
- Volumes `/app/dbsyncer/conf`, `/app/dbsyncer/data`, `/app/dbsyncer/plugins`

**Optional native agent:**
- `libDBSyncer.so` (Linux x64), `libDBSyncer_aarch64.so` (Linux aarch64), `libDBSyncer.dylib` (macOS) — Native JNI encryption agent (`startup.sh:28-41`)

---

*Stack analysis: 2026-07-02*

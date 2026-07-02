# Coding Conventions

**Analysis Date:** 2026-07-02

## Project-Level Rules

**Source:** `.qoder/rules/coding.md`, `.lingma/rules/my_rule.md`, `.openclaw/conventions.md`

- **业务架构优先于技术架构**（Domain-driven design over technical architecture）
- **奥卡姆剃刀原则**：非必要勿增实体（Occam's Razor: don't add entities unnecessarily）
- **保障功能完备的前提下，尽可能缩小影响范围**（Minimize impact scope while ensuring feature completeness）
- **逻辑闭环**（Logic closure — every path must terminate correctly）
- **严谨的生命周期管理**（Rigorous lifecycle management）
- **代码文件不要版权及作者相关注释**（No copyright/author annotations in new code files）
- **大小写比较规范**：所有数据库标识符比较必须使用封装方法

### Mandatory Case-Insensitive Comparison Methods

| Scenario | Required Method | Notes |
|----------|----------------|-------|
| Field object name comparison | `Field.matchesName(String)` | ADR-0008, null-safe, Locale-safe |
| Plain string comparison | `StringUtil.equalsIgnoreCase(CharSequence, CharSequence)` | Apache Commons wrapper, null-safe |
| Primary key string comparison | `StringUtil.equalsIgnoreCase()` | For `targetTablePK` etc. |
| Building Map indexes | `FieldComparisonUtil.buildFieldMap()` | Lowercase keys uniformly |

**Forbidden patterns:**
```java
// ❌ NEVER use direct toLowerCase()/toUpperCase() for DB identifiers
if (fieldName.toLowerCase().equals(targetName.toLowerCase()))

// ❌ NEVER use raw equalsIgnoreCase() for DB identifiers
if (fieldName.equalsIgnoreCase(targetName))
```

## Naming Patterns

**Files:**
- Class name matches filename exactly: `TableGroupServiceImpl.java` → `TableGroupServiceImpl`
- PascalCase for all class names

**Packages:**
- `org.dbsyncer.{module}` — Maven module name becomes package segment
- `org.dbsyncer.common.config`, `org.dbsyncer.biz.impl`, `org.dbsyncer.web.controller.index`

**Interfaces:**
- Simple name, NO `I`-prefix: `TableGroupService`, `MappingService`, `ConnectorService`
- Interface lives in parent package, implementation in `.impl` subpackage

**Implementations:**
- `Impl` suffix: `TableGroupService` → `TableGroupServiceImpl`
- Base classes: `BaseServiceImpl`, `AbstractDatabaseConnector`, `AbstractBufferActuator`

**Exceptions:**
- Pattern: `{ModuleName}Exception extends RuntimeException`
- Location: same package as the module's core classes
- Examples: `CommonException` (`dbsyncer-common`), `BizException` (`dbsyncer-biz`), `SdkException` (`dbsyncer-sdk`), `ConnectorException` (`dbsyncer-connector-base`), `MySQLException`, `SqlServerException`, `OracleException`, `PostgreSQLException`
- Required constructors: `(String)`, `(String, Throwable)`, `(Throwable)`
- Always include `private static final long serialVersionUID = 1L;`

**Enums:**
- PascalCase value names, `Enum` suffix on class: `DispatchTaskEnum`, `ModelEnum`
- Optional `name` field for Chinese display name

**Config classes:**
- Pattern: `{Feature}Config` with `@Configuration` + `@ConfigurationProperties(prefix = "dbsyncer.xxx")`
- Location: `org.dbsyncer.common.config` or module-level config package

**Utility classes:**
- `abstract class` + static methods: `StringUtil`, `CollectionUtils`, `AESUtil`, `JsonUtil`, `UUIDUtil`
- Suffix varies: `*Util` or `*Utils`

**Constants:**
- `public static final` with UPPER_SNAKE_CASE: `EMPTY`, `COMMA`, `BREAK_LINE`
- Defined in utility classes (e.g., `StringUtil.COMMA`)

**Methods:**
- camelCase: `getTableGroup()`, `refreshFields()`, `isKeywordMatch()`
- Boolean getters: `isDisable()`, `isRunning()`, `isPk()`
- Section separators in tests: `// ==================== Section Name ====================`

**Variables:**
- camelCase for local variables and fields
- Logger: `private final Logger logger = LoggerFactory.getLogger(getClass());` (instance) or `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);` (static)

## Code Style

**Java Version:** Java 8 (source + target 1.8)

**Framework:** Spring Boot 2.5.14

**Formatting:**
- No automated formatter detected (no `.editorconfig`, no Checkstyle, no PMD, no SpotBugs, no `.prettierrc`)
- IDE settings in `.idea/` (IntelliJ IDEA)
- 4-space indentation (standard Java)
- Max line length ~120 chars (soft limit)

**No Lombok:** The project does NOT use Lombok. All getters, setters, constructors are written explicitly.

**Copyright headers (legacy):**
Some older files have a copyright header:
```java
/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
```
New files should NOT include copyright/author annotations (per project rule).

## Dependency Injection

**Annotation:** Use `@Resource` (javax.annotation), NOT `@Autowired`

```java
// ✅ Project standard
@Resource
private TableGroupService tableGroupService;

// ❌ Not used in this project
// @Autowired
// private TableGroupService tableGroupService;
```

**Stereotype annotations:**
- `@Service` for business services
- `@Component` for infrastructure components
- `@Configuration` for config classes
- `@Controller` for web controllers (NOT `@RestController` — uses separate `@ResponseBody` per method)

## Import Organization

**Order (conventional, not enforced by tooling):**
1. Project packages: `org.dbsyncer.*`
2. Spring/javax: `org.springframework.*`, `javax.annotation.*`, `javax.servlet.*`
3. Third-party: `com.fasterxml.jackson.*`, `org.slf4j.*`, `org.apache.commons.*`
4. JDK: `java.util.*`, `java.io.*`, `java.sql.*`

**Wildcard imports** are used occasionally (e.g., `import org.dbsyncer.parser.model.*;`, `import java.util.*;`)

**Static imports:**
```java
import static org.junit.Assert.*;
```

## Controller Pattern

All controllers extend `BaseController` and follow this pattern:

```java
@Controller
@RequestMapping("/tableGroup")
public class TableGroupController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private TableGroupService tableGroupService;

    @PostMapping(value = "/add")
    @ResponseBody
    public RestResult add(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);
            return RestResult.restSuccess(tableGroupService.add(params));
        } catch (SdkException e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage(), 400);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }
}
```

**Key points:**
- NEVER throw from controllers — always catch and return `RestResult`
- Specific exceptions before generic `Exception` in catch chains
- Use `getParams(request)` from `BaseController` to extract form params as `Map<String, String>`
- HTTP status codes in `RestResult.restFail(message, statusCode)` (400 for client errors, 500 for server)
- Validation with Spring `Assert`: `Assert.hasText()`, `Assert.notNull()`, `Assert.notEmpty()`

## Service Layer Pattern

**Interface:** Public API defined as interface in `org.dbsyncer.biz`
```java
public interface TableGroupService {
    String add(Map<String, String> params) throws Exception;
    String edit(Map<String, String> params) throws Exception;
    boolean remove(String mappingId, String ids) throws Exception;
}
```

**Implementation:** `@Service` class extending `BaseServiceImpl`
```java
@Service
public class TableGroupServiceImpl extends BaseServiceImpl implements TableGroupService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    // ...
}
```

**BaseServiceImpl** provides shared functionality: `ProfileComponent`, `LogService`, `MessageService`, logging helpers, and `sendNotifyMessage()`.

**Parameter passing:** Services accept `Map<String, String> params` from controllers — a common pattern for flexible CRUD operations.

## Exception Handling

**Hierarchy (all extend RuntimeException):**
```
RuntimeException
├── CommonException (dbsyncer-common)
│   ├── ParserException
│   └── QueueOverflowException
├── BizException (dbsyncer-biz)
│   ├── PrimaryKeyDifferenceException
│   ├── PrimaryKeyRequiredException
│   ├── RepeatedTableGroupException
│   └── TargetTableNotExistsException
├── SdkException (dbsyncer-sdk)
│   └── NullExecutorException
├── ConnectorException (dbsyncer-connector-base)
│   ├── MySQLException
│   ├── SqlServerException
│   ├── OracleException
│   ├── PostgreSQLException
│   ├── SQLiteException
│   └── FileException
├── StorageException (dbsyncer-storage)
└── ManagerException (dbsyncer-manager)
```

**Controller exception handling pattern:**
1. Catch module-specific exceptions first (with specific HTTP status codes)
2. Catch generic `Exception` last (generic failure message)
3. Always log: `logger.error(e.getLocalizedMessage(), e)` — both message AND exception object

**Service exception handling:**
- Throw module-specific exceptions (e.g., `BizException`, `SdkException`)
- Retry logic via `RetryInterceptor` with configurable `RetryPolicy`

**Three-part principle (三段式原则):** Used in retry/validation logic:
```java
// 验证 (Validate) → 处理 (Process) → 返回 (Return)
public boolean isKeywordMatch(String message) {
    if (!useKeyword) { return true; }          // 验证
    if (message == null) { return false; }     // 验证
    return getMatcher().matches(message);      // 处理+返回
}
```

## Logging

**Framework:** SLF4J API + Log4j2 backend

**Logger declaration:**
```java
// Instance logger (preferred in service/controller classes)
private final Logger logger = LoggerFactory.getLogger(getClass());

// Static logger (used in test base classes and utility classes)
protected static final Logger logger = LoggerFactory.getLogger(BaseDDLIntegrationTest.class);
```

**Log patterns:**
```java
// Chinese messages with {} placeholders
logger.info("TableGroup 创建成功：{}", tableGroupId);
logger.warn("触发重试 | mappingId={} | 第{}/{}次 | 间隔={}ms", id, attempt, max, interval);
logger.error("创建表失败: {}", result.error);

// Error logging: always pass exception as last argument
logger.error(e.getLocalizedMessage(), e);
logger.error("重置测试数据库表结构失败", e);
```

**Log level usage:**
- `debug`: Development details (e.g., target table not found, using optimized path)
- `info`: Business events (e.g., entity created, config loaded, sync completed)
- `warn`: Recoverable issues (e.g., retry triggered, fallback used)
- `error`: Failures (e.g., operation failed, unexpected exception)

**Config (`application.properties`):**
```properties
logging.level.root=debug
logging.level.org.springframework=off
logging.level.org.dbsyncer.parser.impl.OperationTemplate=warn
```

## Comments

**Language:** Primarily Chinese (中文)

**When to comment:**
- All public methods get Javadoc comments
- Complex business logic gets inline Chinese comments
- "Section separator" comments in long files: `// ==================== Section Name ====================`
- Enum values: inline `/* 说明 */` comments

**Javadoc style:**
```java
/**
 * 批量获取多个表映射关系的字段差异
 * <p>
 * 用于批量检查...适用于...
 * <p>
 * 支持可选的刷新字段功能...
 *
 * @param ids                表映射关系ID列表
 * @param refreshBeforeCheck 是否刷新
 * @return Map<ID, 是否有差异>
 */
```

**Design documentation in code:**
- `<p>` blocks explaining design decisions
- `<ul>/<li>` for structured lists
- `<pre>` for code/config examples

## Function Design

**Method size:** Varies widely — service methods can be 50+ lines when handling complex business flows (e.g., `createTargetTable` in `TableGroupController.java` is ~200 lines). No automated enforcement.

**Parameters:**
- Services accept `Map<String, String> params` for CRUD operations
- Individual parameters extracted via `@RequestParam` in controllers
- Complex operations take multiple specific params

**Return values:**
- Controllers return `RestResult` (success/fail wrapper)
- Services return domain objects or IDs (`String` for new entity ID)
- Boolean for success/failure operations

## Module Design

**Exports:**
- Each module exposes interfaces in parent package, implementations in `.impl`
- No barrel files — classes imported individually

**Module dependency flow:**
```
dbsyncer-web → dbsyncer-biz → dbsyncer-parser → dbsyncer-sdk → dbsyncer-common
                                                          ↗
dbsyncer-web → dbsyncer-connector/* ─────────────────────
                                                          ↗
dbsyncer-web → dbsyncer-storage ────────────────────────
                                                          ↗
dbsyncer-web → dbsyncer-manager ────────────────────────
```

**Spring Boot application:** Single entry point `dbsyncer-web/src/main/java/org/dbsyncer/web/Application.java` with `@SpringBootApplication(scanBasePackages = "org.dbsyncer")`

## Configuration Pattern

**Spring configuration:** `@ConfigurationProperties` for binding YAML/properties to Java objects:
```java
@Configuration
@ConfigurationProperties(prefix = "dbsyncer.retry")
public class RetryConfig {
    private RetryPolicy global = new RetryPolicy();
    private Map<String, RetryPolicy> task = new HashMap<>();
}
```

**Property prefix convention:** `dbsyncer.{feature}.{option}`

**Application properties:** `dbsyncer-web/src/main/resources/application.properties`
**Test properties:** `dbsyncer-web/src/test/resources/application-test.properties`, `test.properties`

## Thread Safety

- Lock objects: `private final Object LOCK = new Object()` for synchronized blocks
- `ConcurrentHashMap` for concurrent maps (e.g., `Map<String, Object> locks`)
- `volatile` fields for double-checked locking and visibility
- `AtomicInteger` for thread-safe counters

---

*Convention analysis: 2026-07-02*

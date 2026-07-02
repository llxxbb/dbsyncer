# Testing Patterns

**Analysis Date:** 2026-07-02

## Test Framework

**Runner:**
- JUnit 4.13.1 (primary) — `junit:junit`
- JUnit 5 Jupiter 5.7.2 (BOM imported) — `org.junit:junit-bom`
- JUnit Vintage Engine (bridge: runs JUnit 4 tests on JUnit 5 platform)
- Config: `maven-surefire-plugin` 2.22.2

**Assertion Library:**
- `org.junit.Assert` (static import)
- Methods: `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`, `assertSame`, `assertNotSame`, `fail`

**Mocking:**
- No Mockito dependency detected
- Hand-rolled mock classes (inner static classes implementing interfaces)
- Example: `MockSchemaResolver` in `MySQLTemplateTest`

**Run Commands:**
```bash
mvn test                                    # Run all tests
mvn test -pl dbsyncer-common                # Run tests for specific module
mvn test -Dtest=RetryPolicyTest             # Run specific test class
mvn test -Dtest=RetryPolicyTest#testDisable # Run specific test method
mvn test -DskipTests=false                  # Force run (override skip)
```

## Test File Organization

**Location:**
- `src/test/java/` mirrors `src/main/java/` package structure
- Some older tests use flat package structure (e.g., `LuceneFactoryTest.java`, `FileWatchTest.java`)

**Naming:**
- Unit tests: `{ClassName}Test.java` — e.g., `RetryPolicyTest.java`, `KeywordMatcherTest.java`
- Integration tests: `{Scenario}IntegrationTest.java` — e.g., `DMLMysqlIntegrationTest.java`, `DDLMysqlIntegrationTest.java`
- Base test classes: `Base{Type}IntegrationTest.java` — e.g., `BaseDDLIntegrationTest.java`

**Statistics:**
- 37 test files across all modules
- 713 source files (≈5% test-to-source ratio)

## Test Structure

**Two categories of tests:**

### 1. Unit Tests (no Spring context)

Used for: utility classes, retry logic, SQL templates, parsing logic.

```java
package org.dbsyncer.common.retry;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class RetryPolicyTest {

    @Test
    public void testDisable() {
        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(true);
        assertTrue(policy.isDisable());
    }

    @Test
    public void testCalculateInterval() {
        RetryPolicy policy = new RetryPolicy();
        policy.setInitialIntervalMs(100L);
        policy.setMultiplier(2.0);
        policy.setMaxIntervalMs(1000L);

        assertEquals(100L, policy.calculateInterval(0));
        assertEquals(200L, policy.calculateInterval(1));
        assertEquals(1000L, policy.calculateInterval(4)); // capped
    }
}
```

**Key characteristics:**
- Plain JUnit 4, no Spring context
- Instantiate class under test directly
- Direct assertions on method return values

### 2. Spring Integration Tests (full context)

Used for: end-to-end database sync scenarios, controller flows, multi-module interactions.

```java
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class DMLMysqlIntegrationTest extends BaseDDLIntegrationTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Load config, create TestDatabaseManager, initialize test DB
        loadTestConfigStatic();
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);
        String initSql = loadSqlScriptByDatabaseTypeStatic("reset-test-table", "mysql", DMLMysqlIntegrationTest.class);
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);
    }

    @AfterClass
    public static void tearDownClass() {
        // Cleanup: drop test tables, remove test connectors
        String cleanupSql = loadSqlScriptByDatabaseTypeStatic("cleanup-test-data", "mysql", DMLMysqlIntegrationTest.class);
        testDatabaseManager.cleanupTestEnvironment(cleanupSql, cleanupSql);
    }

    @Before
    public void setUp() throws Exception {
        cleanupResidualTestMappings();
        sourceConnectorId = createConnector(...);
        targetConnectorId = createConnector(...);
        mappingId = createMapping();
    }

    @After
    public void tearDown() throws Exception {
        mappingService.stop(mappingId);
        mappingService.remove(mappingId);
        connectorService.remove(sourceConnectorId);
        connectorService.remove(targetConnectorId);
    }

    @Test
    public void testUpsert() throws Exception {
        // Arrange, Act, Assert
        // Execute DML on source, verify sync to target
    }
}
```

**Key characteristics:**
- `@RunWith(SpringRunner.class)` for Spring context loading
- `@SpringBootTest(classes = Application.class)` — full application context
- `@ActiveProfiles("test")` — uses `application-test.properties`
- `@BeforeClass`/`@AfterClass` for expensive setup (DB init/cleanup)
- `@Before`/`@After` for per-test isolation (connector/mapping creation)
- SQL scripts in `src/test/resources/ddl/` directory

### 3. Standalone Tests (partial Spring context)

Used for: tests that need some Spring DI but not full application.

```java
public class DDLSyncCoordinationTest {
    private TableGroup testTableGroup;

    @Before
    public void setUp() {
        testTableGroup = new TableGroup();
        testTableGroup.setId("coordination-test-tablegroup-id");
        // ... manual setup without Spring
    }

    @Test
    public void testEventRoutingMechanism() {
        // ... assertions with JUnit Assert
    }
}
```

## Test Naming Convention

```java
// Pattern 1: test + MethodName + _ Scenario
@Test
public void testConvertEnumType_WithValidValues() { ... }
@Test
public void testConvertEnumType_WithSingleQuoteInValue() { ... }
@Test
public void testConvertEnumType_WithNullValues() { ... }

// Pattern 2: test + FeatureName (for simple cases)
@Test
public void testDisable() { ... }
@Test
public void testSuccessOnFirstAttempt() { ... }
@Test
public void testRetryThenSuccess() { ... }
@Test
public void testExhaustRetries() { ... }

// Pattern 3: test + FeatureDescription
@Test
public void testKeywordMatchExact() { ... }
@Test
public void testKeywordMatchContainsIgnoreCase() { ... }
```

## Mocking

**Framework:** No Mockito — hand-rolled mocks only.

**Patterns:**

### Inner Class Mock

```java
// In MySQLTemplateTest.java
private static class MockSchemaResolver implements SchemaResolver {
    @Override
    public Object merge(Object val, Field field) {
        return val;
    }

    @Override
    public Object convert(Object val, Field field) {
        return val;
    }

    @Override
    public Field fromStandardType(Field field) {
        return field;
    }

    @Override
    public Field toStandardType(Field field) {
        return field;
    }
}
```

### AtomicInteger for Call Counting

```java
// In RetryInterceptorTest.java
AtomicInteger counter = new AtomicInteger(0);
ThrowingSupplier<String> op = () -> {
    if (counter.incrementAndGet() < 3) {
        throw new RuntimeException("transient error");
    }
    return "success";
};

RetryPolicy policy = new RetryPolicy();
String result = interceptor.execute(op, policy);
assertEquals(3, counter.get());  // Verify retry count
```

### Abstract Test Base with Template Methods

```java
// In BaseDDLIntegrationTest.java
public abstract class BaseDDLIntegrationTest {
    protected abstract Class<?> getTestClass();
    protected abstract String getSourceConnectorName();
    protected abstract String getTargetConnectorName();
    protected abstract String getMappingName();
    protected abstract String getSourceTableName();
    protected abstract String getTargetTableName();
    protected abstract List<String> getInitialFieldMappings();
    protected abstract String getConnectorType(DatabaseConfig config, boolean isSource);
    protected abstract String getIncrementStrategy();
    protected abstract String getDatabaseType(boolean isSource);
}
```

## Fixtures and Factories

**Test data builders (helper methods in test classes):**

```java
// Helper methods in MySQLTemplateTest.java
private Field createEnumField(String name, List<String> enumValues) {
    Field field = new Field(name, "ENUM", 12);
    field.setEnumValues(enumValues);
    return field;
}

private Field createSetField(String name, List<String> setValues) {
    Field field = new Field(name, "SET", 12);
    field.setEnumValues(setValues);
    return field;
}
```

**Test configuration factories (in BaseDDLIntegrationTest):**

```java
protected static DatabaseConfig createDefaultMySQLConfig() {
    DatabaseConfig config = new DatabaseConfig();
    config.setUrl("jdbc:mysql://127.0.0.1:3306/test?...");
    config.setUsername("root");
    config.setPassword("123");
    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
    return config;
}

protected static DatabaseConfig createDefaultSQLServerConfig() {
    DatabaseConfig config = new DatabaseConfig();
    config.setUrl("jdbc:sqlserver://127.0.0.1:1433;...");
    config.setUsername("sa");
    config.setPassword("123");
    config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    return config;
}
```

**SQL script loading:**

```java
// Load DDL script from test resources
protected String loadSqlScriptByDatabaseType(String scriptBaseName, boolean isSource) {
    String dbType = getDatabaseType(isSource);
    String resourcePath = String.format("ddl/%s-%s.sql", scriptBaseName, dbType);
    return loadSqlScript(resourcePath, getTestClass());
}
```

**Resource locations:**
- `dbsyncer-web/src/test/resources/test.properties` — DB connection config
- `dbsyncer-web/src/test/resources/application-test.properties` — Spring test profile
- `dbsyncer-web/src/test/resources/ddl/reset-test-table-mysql.sql` — MySQL reset scripts
- `dbsyncer-web/src/test/resources/ddl/reset-test-table-sqlserver.sql` — SQL Server reset scripts
- `dbsyncer-web/src/test/resources/ddl/cleanup-test-data-mysql.sql` — MySQL cleanup scripts
- `dbsyncer-web/src/test/resources/ddl/cleanup-test-data-sqlserver.sql` — SQL Server cleanup scripts

## Coverage

**Requirements:** No enforced coverage target — no JaCoCo/Cobertura configuration detected.

**View Coverage:**
```bash
# No coverage plugin configured; would need:
mvn test -P coverage  # If JaCoCo profile were added
```

## Test Types

**Unit Tests:**
- Located in individual module `src/test/java/` directories
- Test single classes in isolation
- No Spring context, no database required
- Examples: `RetryPolicyTest`, `KeywordMatcherTest`, `RetryInterceptorTest`, `MySQLTemplateTest`, `ConvertUtilTest`, `TemplateParserTest`
- Modules with unit tests: `dbsyncer-common`, `dbsyncer-parser`, `dbsyncer-sdk`, `dbsyncer-biz`

**Integration Tests:**
- Located in `dbsyncer-web/src/test/java/org/dbsyncer/web/integration/`
- Full Spring context with `@SpringBootTest`
- Require running MySQL and/or SQL Server instances
- Test full sync flows: DDL sync, DML sync, upsert, field mapping
- Base class: `BaseDDLIntegrationTest`
- Examples: `DMLMysqlIntegrationTest`, `DDLMysqlIntegrationTest`, `MySQLToSqlServerDMLIntegrationTest`, `CompositeKeyMysqlUpsertIntegrationTest`

**E2E Tests:**
- Integration tests serve as E2E tests (full sync pipeline)
- Verify source database changes propagate to target database
- Use polling for async verification: `waitForDataSync()` with timeout

## Common Patterns

**Async Testing:**
```java
// Poll-based waiting for async data sync
protected void waitForDataSync(Map<String, Object> insertedData, String tableName,
        String primaryKeyColumn, DatabaseConfig targetConfig, long timeoutMs) throws Exception {
    long startTime = System.currentTimeMillis();
    long checkInterval = 300; // 300ms polling

    while (System.currentTimeMillis() - startTime < timeoutMs) {
        Map<String, Object> targetData = queryTableData(targetSql, targetConfig);
        if (!targetData.isEmpty()) {
            Thread.sleep(200); // small buffer
            return;
        }
        Thread.sleep(checkInterval);
    }
    logger.warn("等待数据同步完成超时（{}ms）", timeoutMs);
}
```

**Error Testing:**
```java
// Pattern 1: Try-catch with fail()
@Test
public void testExhaustRetries() {
    try {
        interceptor.execute(op, policy);
        fail("should throw");
    } catch (RuntimeException e) {
        assertEquals(3, counter.get());
    } catch (Exception e) {
        fail("unexpected: " + e.getMessage());
    }
}

// Pattern 2: @Test(expected = ...)
@Test(expected = IllegalArgumentException.class)
public void testConvertEnumType_WithNullValues() {
    Field field = new Field("status", "ENUM", 12);
    field.setEnumValues(null);
    mySQLTemplate.convertToDatabaseType(field);
}
```

**Test isolation for integration tests:**
```java
@Before
public void setUp() throws Exception {
    // 1. Clean residual state from previous runs
    cleanupResidualTestMappings();
    // 2. Create fresh test fixtures
    sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
    targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);
    mappingId = createMapping();
}

@After
public void tearDown() throws Exception {
    // 1. Stop running sync
    mappingService.stop(mappingId);
    // 2. Remove entities
    mappingService.remove(mappingId);
    connectorService.remove(sourceConnectorId);
    connectorService.remove(targetConnectorId);
    // 3. Reset DB schema for next test
    resetDatabaseTableStructure();
}
```

**Thread safety testing:**
```java
// SimpleDateFormatRaceTest.java — reproduces concurrency bugs
private static final SimpleDateFormat BUGGY_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

@Test
public void testBuggySerializer_ThrowsUnderContention() throws Exception {
    int threadCount = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch endGate = new CountDownLatch(threadCount);
    AtomicInteger errorCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            startGate.countDown();
            try {
                // concurrent serialization...
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                endGate.countDown();
            }
        });
    }
    assertTrue(endGate.await(10, TimeUnit.SECONDS));
}
```

## Adding New Tests

**For a unit test:**
1. Create `{ClassName}Test.java` in `src/test/java/{same/package/as/class}`
2. Use JUnit 4 `@Test` annotation
3. Direct instantiation, no Spring context
4. Import `static org.junit.Assert.*`

**For an integration test:**
1. Create `{Scenario}IntegrationTest.java` in `dbsyncer-web/src/test/java/org/dbsyncer/web/integration/`
2. Extend `BaseDDLIntegrationTest`
3. Implement all abstract methods
4. Add `@RunWith(SpringRunner.class)`, `@SpringBootTest`, `@ActiveProfiles("test")`
5. Create SQL scripts in `src/test/resources/ddl/` named `reset-test-table-{dbtype}.sql` and `cleanup-test-data-{dbtype}.sql`
6. Use `@BeforeClass` for DB init, `@AfterClass` for DB cleanup

**For a Spring-aware test without full context:**
1. Use `@RunWith(SpringRunner.class)` with specific `@ContextConfiguration`
2. Use `@Resource` for DI
3. Use `@MockBean` if Mockito were available (but prefer hand-rolled mocks)

## Test Configuration

**`application-test.properties`:**
```properties
dbsyncer.storage.type=disk
management.endpoints.web.base-path=/app
management.health.elasticsearch.enabled=false
logging.level.root=debug
logging.level.org.springframework=off
```

**`test.properties`:**
```properties
test.db.mysql.url=jdbc:mysql://127.0.0.1:3306/test
test.db.mysql.username=root
test.db.mysql.password=123456
test.db.mysql.driver=com.mysql.cj.jdbc.Driver
```

**Maven surefire configuration (per module):**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.22.2</version>
    <configuration>
        <skipTests>false</skipTests>
    </configuration>
</plugin>
```

---

*Testing analysis: 2026-07-02*

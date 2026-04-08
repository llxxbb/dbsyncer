# 集成测试环境规范

## 目的

本文档定义 DBSyncer 集成测试的通用环境要求和操作规范，确保所有集成测试的一致性和可重复性。

**适用范围**：所有集成测试（`dbsyncer-web/src/test/java/org/dbsyncer/web/integration/`）

---

## 测试数据库配置

### 统一配置文件

**位置**：`src/test/resources/test.properties`

```properties
# MySQL 数据库配置
test.db.mysql.url=jdbc:mysql://192.168.172.24:6008/dp_test?rewriteBatchedStatements=true&useUnicode=true&characterEncoding=UTF8&serverTimezone=Asia/Shanghai&useSSL=false&verifyServerCertificate=false&autoReconnect=true&failOverReadOnly=false&tinyInt1isBit=false
test.db.mysql.username=syncer_user
test.db.mysql.password=syncer_pwd
test.db.mysql.driver=com.mysql.cj.jdbc.Driver

# SQL Server 数据库配置
test.db.sqlserver.url=jdbc:sqlserver://192.168.192.240:1433;DatabaseName=a_yunwei_test_one;encrypt=false;trustServerCertificate=true
test.db.sqlserver.username=test_user
test.db.sqlserver.password=test_password
test.db.sqlserver.driver=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

### 数据库访问规范

| 数据库 | 主机 | 端口 | 用途 |
|--------|------|------|------|
| MySQL | 192.168.172.24 | 6008 | MySQL 相关集成测试 |
| SQL Server | 192.168.192.240 | 1433 | SQL Server 相关集成测试 |

**注意**：
- 这些是**共享测试环境**，不是个人独占
- 测试表必须使用特定前缀（如 `test_`、`ddlTest_`），避免与业务表混淆
- 测试完成后必须清理数据

---

## 测试命名规范

### 测试类命名

```
[功能]IntegrationTest.java
```

**示例**：
- `DDLMysqlIntegrationTest.java` - MySQL DDL 测试
- `DDLSqlServerCTIntegrationTest.java` - SQL Server CT DDL 测试
- `SqlServerCTBigTransactionIntegrationTest.java` - SQL Server CT 大事务测试
- `CustomFieldIntegrationTest.java` - 自定义字段测试

### 测试表命名

| 测试类型 | 表名前缀 | 示例 |
|---------|----------|------|
| DDL 测试 | `ddlTest_` | `ddlTest_source`, `ddlTest_target` |
| DML 测试 | `dmlTest_` | `dmlTest_source`, `dmlTest_target` |
| 大事务测试 | `test_bigtx_` | `test_bigtx_source`, `test_bigtx_target` |
| 自定义字段 | `test_custom_` | `test_custom_field` |

**原则**：
- 统一前缀，便于识别和清理
- 源表和目标表成对命名（`_source` / `_target`）

---

## 测试基类

### BaseDDLIntegrationTest

**位置**：`org.dbsyncer.web.integration.BaseDDLIntegrationTest`

**提供的公共能力**：
- 加载测试配置（`loadTestConfig()`）
- 加载 SQL 脚本（`loadSqlScriptByDatabaseType()`）
- 创建连接器和 Mapping
- 管理测试数据库（`TestDatabaseManager`）

**子类需要实现的抽象方法**：
```java
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
```

### TestDatabaseManager

**位置**：`org.dbsyncer.web.integration.TestDatabaseManager`

**提供的能力**：
- 初始化测试环境（创建表、插入初始数据）
- 清理测试环境（删除表、清理 CDC 日志）
- 跨数据库类型的一致性管理

---

## 测试生命周期

### 标准流程

```
@BeforeClass
    ↓
加载测试配置 (loadTestConfigStatic)
    ↓
创建 TestDatabaseManager
    ↓
初始化测试环境 (initializeTestEnvironment)
    ↓
@Before
    ↓
创建连接器、Mapping、Meta
    ↓
准备测试数据
    ↓
@Test
    ↓
执行测试逻辑
    ↓
@After
    ↓
删除连接器、Mapping、Meta
    ↓
@AfterClass
    ↓
清理测试环境 (cleanupTestEnvironment)
```

### 注解使用规范

| 注解 | 用途 | 示例 |
|------|------|------|
| `@BeforeClass` | 一次性初始化（数据库连接、表结构） | `setUpClass()` |
| `@AfterClass` | 一次性清理（删除表、清理 CDC） | `tearDownClass()` |
| `@Before` | 每个测试前的准备（创建连接器） | `setUp()` |
| `@After` | 每个测试后的清理（删除配置） | `tearDown()` |
| `@Ignore` | 跳过测试（需要手动执行时） | 大事务测试 |

---

## SQL 脚本规范

### 脚本位置

```
src/test/resources/ddl/
├── reset-test-table-mysql.sql
├── reset-test-table-sqlserver.sql
├── cleanup-test-data-mysql.sql
├── cleanup-test-data-sqlserver.sql
└── README.md
```

### 脚本命名规则

```
[用途]-[内容]-[数据库类型].sql
```

**示例**：
- `reset-test-table-mysql.sql` - MySQL 测试表初始化
- `cleanup-test-data-sqlserver.sql` - SQL Server 测试数据清理

### 脚本内容规范

**初始化脚本**：
```sql
-- 1. 删除已存在的测试表
IF OBJECT_ID('dbo.test_bigtx_source', 'U') IS NOT NULL
    DROP TABLE [dbo].[test_bigtx_source];

-- 2. 创建测试表
CREATE TABLE [dbo].[test_bigtx_source] (
    [id] INT IDENTITY(1,1) PRIMARY KEY,
    [name] NVARCHAR(50),
    ...
);

-- 3. 启用 CDC（如需要）
EXEC sys.sp_cdc_enable_table 
    @source_schema = N'dbo', 
    @source_name = N'test_bigtx_source', 
    @role_name = NULL, 
    @supports_net_changes = 0;
```

**清理脚本**：
```sql
-- 1. 禁用 CDC
EXEC sys.sp_cdc_disable_table 
    @source_schema = N'dbo', 
    @source_name = N'test_bigtx_source', 
    @capture_instance = 'all';

-- 2. 删除测试表
DROP TABLE [dbo].[test_bigtx_source];
```

---

## 数据库特定规范

### SQL Server CDC 测试

**前置条件**：

1. **SQL Server Agent 必须运行**
   ```sql
   EXEC master.dbo.xp_servicecontrol N'QUERYSTATE', N'SQLSERVERAGENT'
   -- 期望结果：Running.
   ```

2. **CDC 功能必须启用**
   ```sql
   -- 检查数据库 CDC
   SELECT is_cdc_enabled FROM sys.databases WHERE name = 'source_db'
   -- 期望结果：1 (已启用)
   ```

3. **时区支持**（SQL Server 2016+）
   - 用于 LSN 时间映射
   - `AT TIME ZONE 'UTC'`

**注意事项**：
- 不要手动修改 LSN
- 等待 CDC 捕获（数据变更后需要等待几秒）
- 测试后清理 CDC 日志

### MySQL 测试

**前置条件**：

1. **Binlog 必须启用**
   ```sql
   SHOW VARIABLES LIKE 'log_bin';
   -- 期望结果：ON
   ```

2. **Binlog 格式**
   ```sql
   SHOW VARIABLES LIKE 'binlog_format';
   -- 期望结果：ROW
   ```

**注意事项**：
- 使用 `rewriteBatchedStatements=true` 优化批量插入
- 设置 `serverTimezone=Asia/Shanghai` 避免时区问题

---

## 运行测试

### Maven 命令行

```bash
cd /projects/github/dbsyncer

# 运行单个测试类
mvn test -Dtest=DDLMysqlIntegrationTest -pl dbsyncer-web

# 运行多个测试类
mvn test -Dtest=DDLMysqlIntegrationTest,DDLSqlServerCTIntegrationTest -pl dbsyncer-web

# 运行所有集成测试
mvn test -Dtest="**/integration/*Test" -pl dbsyncer-web
```

### IDE 中运行

1. 打开测试类
2. 右键 → Run As → JUnit Test
3. 查看控制台输出

### 跳过测试

**临时跳过**：
```java
@Ignore("原因说明")
public class SomeIntegrationTest { ... }
```

**Maven 参数**：
```bash
mvn test -DskipTests
```

---

## 监控与调试

### 日志级别

**配置文件**：`src/test/resources/logback-test.xml`

```xml
<configuration>
    <logger name="org.dbsyncer" level="DEBUG"/>
    <logger name="org.springframework" level="WARN"/>
    <logger name="com.microsoft.sqlserver" level="INFO"/>
</configuration>
```

### 关键日志

| 日志关键字 | 说明 | 期望 |
|-----------|------|------|
| `测试环境初始化完成` | 环境准备就绪 | 每个测试前出现 |
| `测试环境清理完成` | 环境清理完成 | 每个测试后出现 |
| `检测到停止信号` | 优雅停止触发 | 大事务测试 |
| `增量持久化` | 进度持久化 | 大事务测试 |

### 数据库查询

**检查 CDC 变更数量**：
```sql
SELECT 
    capture_instance,
    COUNT(*) as change_count
FROM cdc.fn_cdc_get_all_changes_dbo_test_bigtx_source(
    sys.fn_cdc_get_min_lsn('dbo_test_bigtx_source'),
    sys.fn_cdc_get_max_lsn(),
    'all update old'
)
GROUP BY capture_instance;
```

**检查测试表**：
```sql
-- SQL Server
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_NAME LIKE 'test_%' OR TABLE_NAME LIKE 'ddlTest_%';

-- MySQL
SHOW TABLES LIKE 'test_%';
SHOW TABLES LIKE 'ddlTest_%';
```

---

## 常见问题

### Q1: 数据库连接失败

**错误**：`Communications link failure`

**解决**：
```bash
# 检查数据库是否可访问
ping 192.168.172.24
ping 192.168.192.240

# 检查端口是否开放
telnet 192.168.172.24 6008
telnet 192.168.192.240 1433
```

### Q2: SQL Server Agent 未运行

**错误**：`The agent server is not running`

**解决**：
```bash
# Windows 服务管理
net start SQLSERVERAGENT
```

### Q3: CDC 未启用

**错误**：`The database is not enabled for change data capture`

**解决**：
```sql
EXEC sys.sp_cdc_enable_db;
EXEC sys.sp_cdc_enable_table 
    @source_schema = N'dbo', 
    @source_name = N'表名', 
    @role_name = NULL, 
    @supports_net_changes = 0;
```

### Q4: 测试超时

**问题**：大事务处理时间过长

**解决**：
```xml
<!-- 修改 pom.xml 中的测试超时配置 -->
<configuration>
    <forkedProcessTimeoutInSeconds>600</forkedProcessTimeoutInSeconds>
</configuration>
```

### Q5: 测试数据残留

**问题**：测试表未清理

**解决**：
```sql
-- 查找所有测试表
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_NAME LIKE 'test_%' OR TABLE_NAME LIKE 'ddlTest_%';

-- 手动删除
DROP TABLE dbo.test_bigtx_source;
```

---

## 测试设计原则

### 1. 表名隔离

所有测试表都使用特定前缀，避免与业务表冲突：
- `test_` - 通用测试表
- `ddlTest_` - DDL 测试表
- `dmlTest_` - DML 测试表

### 2. 统一环境管理

使用 `TestDatabaseManager` 管理测试环境：
- 测试前自动创建表结构和初始数据
- 测试后自动清理数据，保持环境干净
- 跨数据库类型的一致性管理

### 3. 完整的生命周期管理

使用 `@BeforeClass` 和 `@AfterClass` 注解：
- 确保测试环境正确初始化
- 确保测试环境正确清理
- 即使测试失败也能清理环境

### 4. 可配置测试参数

通过修改测试类中的常量来调整测试参数：
```java
private static final int TEST_RECORD_COUNT = 100000;  // 测试数据量
private static final int SNAPSHOT_INTERVAL = 10000;   // 持久化间隔
private static final int MAX_RETRY = 3;               // 最大重试次数
```

### 5. 统一断言风格

所有测试类都使用 `Assert` 关键字进行断言：
```java
Assert.assertEquals("预期值", actualValue);
Assert.assertTrue("条件描述", condition);
Assert.assertNotNull("对象不应为 null", object);
```

---

## 新增集成测试指南

### 步骤 1：创建测试类

```java
package org.dbsyncer.web.integration;

import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class MyIntegrationTest extends BaseDDLIntegrationTest {
    // ...
}
```

### 步骤 2：实现抽象方法

```java
@Override
protected Class<?> getTestClass() {
    return MyIntegrationTest.class;
}

@Override
protected String getSourceConnectorName() {
    return "MySQL-Source-MyTest";
}

// ... 其他方法
```

### 步骤 3：准备 SQL 脚本

在 `src/test/resources/ddl/` 下创建：
- `reset-test-table-mysql.sql`（或 sqlserver）
- `cleanup-test-data-mysql.sql`（或 sqlserver）

### 步骤 4：编写测试方法

```java
@Test
public void testMyFeature() throws Exception {
    // 测试逻辑
    Assert.assertEquals(expected, actual);
}
```

### 步骤 5：运行测试

```bash
mvn test -Dtest=MyIntegrationTest -pl dbsyncer-web
```

---

*最后更新：2026-04-01*
*维护者：DBSyncer 开发团队*

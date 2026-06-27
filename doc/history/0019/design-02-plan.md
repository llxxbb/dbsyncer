# ADR-0019 实施方案

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | plan |
| 作者 | @lixuebin |
| 日期 | 2026-06-27 |
| 关联 ADR | [ADR-0019](../../adr/0019-Model层贫血化重构.md) |

---

## 总体目标

**Step 1 目标**：SyncRow 持有 `mapping` 和 `tableGroup` 引用，PluginContext 精简为运行时状态容器。

**验收标准**：
1. SyncRow 可直接访问 Mapping 和 TableGroup
2. PluginContext 属性从 16 个精简到 9 个
3. Connector 方法签名简化为 `insert(instance, rows)`
4. 所有 Connector 实现适配完毕
5. Integration Test 全部通过

---

## 实施步骤

### Phase 1: SyncRow 结构改造（2h）

#### Step 1.1: 扩展 SyncRow 字段

**文件**：`dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/plugin/SyncRow.java`

```java
package org.dbsyncer.sdk.plugin;

import java.util.List;
import java.util.Map;

public class SyncRow {
    private Map<String, Object> data;
    private PluginContext context;
    
    // 新增字段（类型用 Object，运行时转换）
    private Object mapping;        // Mapping 实例
    private Object tableGroup;     // TableGroup 实例
    
    public SyncRow() {}
    
    public SyncRow(Map<String, Object> data, Object mapping, Object tableGroup, PluginContext context) {
        this.data = data;
        this.mapping = mapping;
        this.tableGroup = tableGroup;
        this.context = context;
    }
    
    // getter/setter 自动生成
    
    // 便捷方法
    @SuppressWarnings("unchecked")
    public <T> T getMapping() {
        return (T) mapping;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getTableGroup() {
        return (T) tableGroup;
    }
}
```

**注意**：先用 Object 类型，避免跨模块依赖。运行时强制转换为具体类型。

#### Step 1.2: 精简 PluginContext

**文件**：`dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/plugin/PluginContext.java`

**删除属性**：
- `batchSize` → 改由 `mapping.getBatchNum()` 提供
- `forceUpdate` → 改由 `mapping.isForceUpdate()` 提供
- `sourceTableName` → 改由 `tableGroup.getSourceTable().getName()` 提供
- `targetTableName` → 改由 `tableGroup.getTargetTable().getName()` 提供
- `targetFields` → 改由 `tableGroup.getTargetTable().getColumn()` 提供

**保留属性**（运行时状态）：
- `event`：INSERT/UPDATE/DELETE
- `traceId`：运行时 trace
- `pkFields`：预计算的主键 Field 列表
- `targetConnectorInstance`：目标连接器
- `enableSchemaResolver`：系统配置
- `enablePrintTraceInfo`：调试开关
- `sourceList`：源数据（失败重试用）

```java
public interface PluginContext {
    // 运行时状态（保留）
    String getEvent();
    void setEvent(String event);
    String getTraceId();
    void setTraceId(String traceId);
    
    // 预计算（保留）
    List<Field> getPkFields();
    void setPkFields(List<Field> pkFields);
    
    // 运行时对象（保留）
    ConnectorInstance getTargetConnectorInstance();
    void setTargetConnectorInstance(ConnectorInstance instance);
    
    // 系统开关（保留）
    boolean isEnableSchemaResolver();
    void setEnableSchemaResolver(boolean enable);
    boolean isEnablePrintTraceInfo();
    void setEnablePrintTraceInfo(boolean enable);
    
    // 失败重试用（保留）
    List<Map> getSourceList();
    void setSourceList(List<Map> source);
    
    // 以下属性移到 Mapping/TableGroup
    // ❌ int getBatchSize();
    // ❌ boolean isForceUpdate();
    // ❌ String getSourceTableName();
    // ❌ String getTargetTableName();
    // ❌ List<Field> getTargetFields();
    // ❌ void setTargetFields(List<Field> fields);
}
```

#### Step 1.3: 编译验证

```bash
mvn clean compile -DskipTests
```

**验证点**：
- 所有模块编译通过
- 无运行时类型转换错误

---

### Phase 2: MySQL Connector 改造（1.5h）

**目标**：先用 1 个 Connector 验证方案，避免批量修改后才发现设计问题。

#### Step 2.1: 改造 AbstractConnector

**文件**：`dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/AbstractConnector.java`

修改 `convertProcessBeforeWriter` 方法：

```java
public void convertProcessBeforeWriter(List<SyncRow> rows, SchemaResolver resolver) {
    if (rows == null || rows.isEmpty()) return;
    
    SyncRow first = rows.get(0);
    TableGroup tableGroup = first.getTableGroup();
    PluginContext context = first.getContext();
    
    List<Field> targetFields = tableGroup.getTargetTable().getColumn();
    
    rows.forEach(row -> {
        Map<String, Object> data = row.getData();
        targetFields.forEach(field -> {
            data.computeIfPresent(field.getName(), (k, v) -> resolver.convert(v, field));
        });
    });
}
```

#### Step 2.2: 改造 AbstractDatabaseConnector

**文件**：`dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/AbstractDatabaseConnector.java`

修改所有 insert/update/delete/upsert 方法签名：

```java
// 当前
public Result insert(PluginContext context, List<SyncRow> rows) {
    List<Field> targetFields = context.getTargetFields();
    int batchSize = context.getBatchSize();
}

// 重构后
public Result insert(List<SyncRow> rows) {
    SyncRow first = rows.get(0);
    PluginContext context = first.getContext();
    TableGroup tableGroup = first.getTableGroup();
    Mapping mapping = first.getMapping();
    
    List<Field> targetFields = tableGroup.getTargetTable().getColumn();
    int batchSize = mapping.getBatchNum();
}
```

#### Step 2.3: 改造 MySQLConnector

**文件**：`dbsyncer-connector/dbsyncer-connector-mysql/src/main/java/MySQLConnector.java`

```java
@Override
public Result insert(DatabaseConnectorInstance instance, List<SyncRow> rows) {
    SyncRow first = rows.get(0);
    TableGroup tg = first.getTableGroup();
    
    // 从 tableGroup 获取配置
    List<Field> targetFields = tg.getTargetTable().getColumn();
    Map<String, String> command = tg.getCommand();
    
    // 从 context 获取运行时状态
    PluginContext context = first.getContext();
    String event = context.getEvent();
    
    // 执行写入逻辑...
}
```

#### Step 2.4: 单测验证

```bash
# 运行 MySQL Connector 相关测试
mvn test -Dtest=*MySQL*
```

**验证点**：
- 单元测试全部通过
- 功能逻辑正确

---

### Phase 3: 批量 Connector 改造（2.5h）

**目标**：基于 Phase 2 的成功经验，批量修改其他 Connector。

#### 改造顺序

1. ✅ AbstractDatabaseConnector（已完成）
2. ✓ DatabaseConnector 子类（PostgreSQL/SQLServer/Oracle/SQLite）
3. ✓ KafkaConnector
4. ✓ ElasticsearchConnector
5. ✓ FileConnector

#### 改造模式

所有 Connector 遵循统一模式：

```java
@Override
public Result insert(I instance, List<SyncRow> rows) {
    SyncRow first = rows.get(0);
    
    // 1. 从 Mapping 获取批次配置
    Mapping mapping = first.getMapping();
    int batchSize = mapping.getBatchNum();
    boolean forceUpdate = mapping.isForceUpdate();
    
    // 2. 从 TableGroup 获取表元数据
    TableGroup tg = first.getTableGroup();
    List<Field> targetFields = tg.getTargetTable().getColumn();
    String tableName = tg.getTargetTable().getName();
    Map<String, String> command = tg.getCommand();
    
    // 3. 从 PluginContext 获取运行时状态
    PluginContext context = first.getContext();
    String event = context.getEvent();
    String traceId = context.getTraceId();
    
    // 4. 执行业务逻辑
    return execute(instance, rows, context, targetFields, command);
}
```

#### 编译验证

```bash
# 全模块编译
mvn clean compile -DskipTests
```

---

### Phase 4: 调用方适配（1.5h）

#### Step 4.1: SyncRow 构建

**文件**：`dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/WriterBuffer.java`

```java
public void write(List<Map> data) {
    // 构建 SyncRow 列表
    List<SyncRow> rows = data.stream()
        .map(this::toSyncRow)
        .toList();
    
    // 调用 Connector
    connectorService.insert(connectorInstance, rows);
}

private SyncRow toSyncRow(Map<String, Object> data) {
    return new SyncRow(data, mapping, tableGroup, pluginContext);
}
```

#### Step 4.2: WriterBuffer 改造

**核心问题**：WriterBuffer 当前通过 `profileComponent` 获取配置，需要改为接收外部传入的 mapping/tableGroup。

```java
// 当前构造
public WriterBuffer(ProfileComponent profileComponent, String mappingId) {
    this.mapping = profileComponent.getMapping(mappingId);
    this.tableGroup = profileComponent.getTableGroup(mappingId);
}

// 重构后（不变，仍然持有 profileComponent 用于获取配置）
public WriterBuffer(ProfileComponent profileComponent, String mappingId) {
    this.mapping = profileComponent.getMapping(mappingId);
    this.tableGroup = profileComponent.getTableGroup(mappingId);
}

// 构建 SyncRow 时传入
private SyncRow buildSyncRow(Map<String, Object> data) {
    return new SyncRow(data, mapping, tableGroup, pluginContext);
}
```

---

### Phase 5: Integration Test（3h）

#### 测试清单

| 测试场景 | 验证点 |
|---------|--------|
| MySQL → PostgreSQL 迁移 | 同步数据正确性 |
| Kafka → MySQL 消息消费 | 消费速率、数据完整性 |
| File → ES 文档导入 | 批量导入性能 |
| 多表组迁移 | 并发同步正确性 |
| 失败重试 | 重试后数据一致性 |
| CDC 实时同步 | 增删改事件捕获 |

#### 测试执行

```bash
# 单元测试
mvn test -DskipITs

# Integration Test
mvn verify
```

#### 性能 baseline

| 指标 | 改造前 | 改造后 | 预期 |
|------|--------|--------|------|
| TPS | 1000 | 950~1050 | 持平或略升（减少对象创建） |
| 内存 | 100MB | 90~100MB | 持平 |
| GC | 50ms/次 | 40~60ms/次 | 持平 |

---

## 风险应对

### 风险 1：运行时类型转换失败

**场景**：SyncRow 持有 Object 类型的 mapping/tableGroup，运行时转换为具体类型失败。

**应对**：
- 增加类型检查：`instanceof Mapping` / `instanceof TableGroup`
- 失败时抛出有意义的异常：`ClassCastException: expected Mapping/actual Object`

### 风险 2：Connector 实现遗漏

**场景**：批量修改 Connector 时，某个 Connector 忘记适配。

**应对**：
- 修改 ConnectorService 接口，强制编译期检查
- `Result insert(I instance, List<SyncRow> rows)` 签名变更会强制编译失败

### 风险 3：PluginContext 生命周期混乱

**场景**：PluginContext 仍然被误用为配置容器。

**应对**：
- 删除 PluginContext 的多余属性
- 代码审查时检查是否有新的配置属性加入

---

## 回滚方案

**回滚触发条件**：
- 单元测试失败 > 20%
- Integration Test 全量失败
- 性能劣化 > 30%

**回滚步骤**：
1. `git revert` 撤销所有改动
2. 验证编译通过
3. 重新测试确认回滚成功

---

## 下一步（Step 2 预研）

Step 1 完成后，如果团队认可方案，可继续推进：

**Step 2：Model 完全贫血化**

**改造点**：
- 移除 Model 中的 ProfileComponent 依赖
- 提取持久化逻辑到 Service 层
- Model 可完全下移到 sdk 层

**预估**：~12 个文件，3-5 小时

---

## 验收标准

| 维度 | 验收条件 |
|------|---------|
| 功能 | Integration Test 全部通过 |
| 性能 | TPS 下降 ≤ 10% |
| 代码 | 代码量减少 ≥ 5%（精简 PluginContext） |
| 文档 | ADR 已创建，设计稿已完善 |

> **详细验证方案**见 [verification-plan.md](verification-plan.md) - 包含 24 个调用点的逐项承接验证标准。

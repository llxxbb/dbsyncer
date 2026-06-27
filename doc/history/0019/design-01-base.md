# ADR-0019 设计稿（初始版本）

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | base |
| 作者 | @lixuebin |
| 日期 | 2026-06-27 |
| 关联 ADR | [ADR-0019](../../adr/0019-Model层贫血化重构.md) |

## 设计目标

**Step 1（本期实施）**：通过接口解耦，消除 Model 间的直接依赖，让 SyncRow 成为数据流的"配置载体"，PluginContext 退化为"运行时控制器"。

**Step 2（后续实施）**：Model 完全贫血化，剥离 ProfileComponent 依赖，持久化逻辑上移到 Service 层。

---

## 技术方案

### Step 1: 接口解耦

#### 架构图

```
┌─────────────────────────────────────────────────────┐
│ sdk 模块                                          │
├─────────────────────────────────────────────────────┤
│                                                   │
│  ┌─────────────┐      ┌───────────────────────┐   │
│  │ SyncRow     │─────>│ TableGroupConfig      │   │
│  │ - data      │      │ - getTargetFields()   │   │
│  │ - mapping   │      │ - getPrimaryKeys()    │   │
│  │ - tableGroup│      │ - getCommand()        │   │
│  │             │      └───────────────────────┘   │
│  └─────────────┘               ▲                 │
│        │                       │ 实现            │
│        ▼                       │                 │
│  ┌─────────────┐      ┌───────────────────────┐   │
│  │ PluginContext│      │ Mapping               │   │
│  │ (运行时状态) │      │ TableGroup            │   │
│  │ - event     │      └───────────────────────┘   │
│  │ - traceId   │                                   │
│  │ - pkFields  │                                   │
│  └─────────────┘                                   │
└─────────────────────────────────────────────────────┘
```

#### 核心组件

**SyncRow 改造**

```java
public class SyncRow {
    private Map<String, Object> data;
    private Mapping mapping;           // 新增
    private TableGroup tableGroup;     // 新增
    private PluginContext context;     // 保留（运行时状态）
    
    // 便捷方法
    public List<Field> getTargetFields() {
        return tableGroup.getTargetTable().getColumn();
    }
    
    public List<String> getPrimaryKeys() {
        return tableGroup.getSourceTable().getPrimaryKeys();
    }
}
```

**PluginContext 精简**

| 属性 | 现状 | 改造 |
|------|------|------|
| batchSize | `context.getBatchSize()` | `mapping.getBatchNum()` |
| forceUpdate | `context.isForceUpdate()` | `mapping.isForceUpdate()` |
| sourceTableName | `context.getSourceTableName()` | `tableGroup.getSourceTable().getName()` |
| targetTableName | `context.getTargetTableName()` | `tableGroup.getTargetTable().getName()` |
| targetFields | `context.getTargetFields()` | `tableGroup.getTargetTable().getColumn()` |
| **保留** | | |
| event | `context.getEvent()` | 运行时状态（INSERT/UPDATE/DELETE） |
| pkFields | `context.getPkFields()` | 运行时预计算 |
| traceId | `context.getTraceId()` | 运行时 trace |

#### 接口签名（sdk 层）

```java
// Connector 接口改造
public interface ConnectorService<I, C> {
    // 之前
    Result insert(I instance, PluginContext context, List<SyncRow> rows);
    
    // 之后（context 从 rows[0].getContext() 获取）
    Result insert(I instance, List<SyncRow> rows);
}
```

**Connector 内部访问方式**

```java
public Result insert(DatabaseConnectorInstance instance, List<SyncRow> rows) {
    SyncRow row = rows.get(0);
    PluginContext context = row.getContext();      // 运行时状态
    TableGroup tableGroup = row.getTableGroup();    // 配置信息
    
    List<Field> targetFields = tableGroup.getTargetTable().getColumn();
    int batchSize = row.getMapping().getBatchNum();
    
    // 业务逻辑...
}
```

---

## 影响范围

### 必须修改的模块

| 模块 | 文件数 | 改动类型 |
|------|--------|---------|
| dbsyncer-sdk | 3 | 接口定义、SyncRow 改造 |
| dbsyncer-connector-* | ~10 | Connector 实现适配 |
| dbsyncer-parser | ~5 | 调用方适配 |
| **总计** | **~18** | - |

### 改造示例（AbstractDatabaseConnector）

```java
// 当前代码
public Result insert(PluginContext context, List<SyncRow> rows) {
    List<Field> targetFields = context.getTargetFields();  // ❌ 从 context 获取
    int batchSize = context.getBatchSize();
}

// 重构后
public Result insert(List<SyncRow> rows) {
    SyncRow row = rows.get(0);
    List<Field> targetFields = row.getTableGroup().getTargetTable().getColumn();  // ✅ 从 tableGroup 获取
    int batchSize = row.getMapping().getBatchNum();
}
```

---

## 风险点

### 已知风险

1. **接口签名变动影响面大**：所有 Connector 实现都需要调整
   - **应对**：先改 1 个 Connector（如 MySQL）验证，再批量修改

2. **PluginContext 生命周期混乱**：
   - **应对**：明确 PluginContext 只存储运行时状态（event/traceId）

3. **静态工厂方法改造**：
   - `TableGroup.create(...)` 等静态方法需要调整参数
   - **应对**：保留静态方法，但参数改为直接传入需要的值

---

## 实施策略

### 阶段划分

| 阶段 | 任务 | 工作量 | 验证点 |
|------|------|--------|--------|
| Phase 1 | SyncRow 改造 | 2h | 编译通过 |
| Phase 2 | 1 个 Connector 改造（验证方案） | 1.5h | MySQL Connector 单测通过 |
| Phase 3 | 批量 Connector 改造 | 2.5h | 全量编译通过 |
| Phase 4 | Integration Test | 3h | 端到端测试通过 |
| **总计** | | **9h** | - |

---

## 下一步

1. ✅ 确认方案可行
2. ⏳ 实施 Phase 1（SyncRow 改造）
3. ⏳ 实施 Phase 2（MySQL Connector 验证）

## 相关文档

- [验证方案](verification-plan.md) - 24 个调用点的详细验证标准
- [实施方案](design-02-plan.md) - 分阶段实施细节

# ADR-0019 设计稿（修正版）

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | v2（修正版） |
| 作者 | @lixuebin |
| 日期 | 2026-06-27 |
| 关联 ADR | [ADR-0019](../../adr/0019-Model层贫血化重构.md) |

## 设计目标

**Step 1（已完成）**：Model 贫血化，剥离 `ProfileComponent`/`ParserComponent` 依赖，持久化逻辑上移到 Service 层。

**Step 2（本期实施）**：Model 下移 sdk 模块，消除跨模块依赖障碍。

**Step 3（后续实施）**：SyncRow 改持 Mapping/TableGroup，PluginContext 精简为纯 runtime。

> **注：** 原方案中的"接口解耦（TableGroupConfig）"步骤已被废弃。因为 Model 贫血化完成后，可以直接将 Model 类下移到 sdk，SyncRow 直接持有 Mapping/TableGroup 对象本身，无需额外的接口抽象层。

---

## 技术方案

### 总体架构（最终目标）

```
┌─────────────────────────────────────────────────────────┐
│ sdk 模块                                              │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ SyncRow                                        │   │
│  │  - data: Map<String, Object>                   │   │
│  │  - mapping: Mapping         ← sdk.model       │   │
│  │  - tableGroup: TableGroup   ← sdk.model       │   │
│  │  - context: PluginContext   ← 精简为 runtime   │   │
│  └─────────────────────────────────────────────────┘   │
│          │                                               │
│          │ 持有                                         │
│          ▼                                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ sdk.model  （纯数据载体，无持久化依赖）            │   │
│  │  - ConfigModel                                   │   │
│  │  - AbstractConfigModel                           │   │
│  │  - Mapping                                       │   │
│  │  - TableGroup                                    │   │
│  │  - Meta                                         │   │
│  │  - FieldMapping                                  │   │
│  │  - Convert                                      │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ PluginContext（精简后）                          │   │
│  │  - event              运行时操作类型             │   │
│  │  - traceId            运行时 trace              │   │
│  │  - pkFields           运行时预计算主键           │   │
│  │  - sourceConnectorInstance                      │   │
│  │  - targetConnectorInstance                      │   │
│  │  (batchSize/forceUpdate/targetFields 等已移除)   │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Step 2: Model 下移 SDK

#### 迁移依赖图

```
parser.model → sdk.model 迁移依赖关系：

  FieldMapping (无依赖)
  MetaEnum (无依赖)
  SyncPhaseEnum (无依赖)
  ConfigModel (仅依赖 ConfigConstant)
       ↑
  AbstractConfigModel (依赖 ConfigModel + Convert + Filter)
       ↑
  Mapping (依赖 AbstractConfigModel)
  TableGroup (依赖 AbstractConfigModel + FieldMapping)
  Meta (依赖 ConfigModel + MetaEnum + SyncPhaseEnum)
  
  同步迁移:
  Convert → ConvertEnum + ParseResult → 全部移到 sdk
  ParserException → 移到 common
```

#### 具体迁移策略

| 优先级 | 类 | 迁移方式 |
|--------|-----|---------|
| P0 | `FieldMapping` | 直接复制到 sdk.model，无外部依赖 |
| P0 | `MetaEnum`、`SyncPhaseEnum` | 直接复制到 sdk.enums |
| P0 | `ConfigModel` | 直接复制到 sdk.model |
| P0 | `ParserException` | 移到 common（供多方引用） |
| P1 | `AbstractConfigModel` | 需 `Convert`→ 先移 Convert |
| P1 | `Convert` + `ConvertEnum` + `ParseResult` | 同步迁移 |
| P2 | `Mapping`、`TableGroup`、`Meta` | 依赖前置完成后迁移 |

**注意：** parser 层的原有路径保留为"桥接类"（空的子类或 import 重定向），或在迁移完成后全局替换 import。

### Step 3: SyncRow 改造 + PluginContext 精简

#### SyncRow 改造

```java
public class SyncRow {
    private Map<String, Object> data;
    private Mapping mapping;           // 新增（sdk.model）
    private TableGroup tableGroup;     // 新增（sdk.model）
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

#### PluginContext 精简对照表

| 属性 | 现状 | 改造后来源 | 
|------|------|-----------|
| batchSize | `context.getBatchSize()` | **删除**，改用 `mapping.getBatchNum()` |
| forceUpdate | `context.isForceUpdate()` | **删除**，改用 `mapping.isForceUpdate()` |
| sourceTableName | `context.getSourceTableName()` | **删除**，改用 `tableGroup.getSourceTable().getName()` |
| targetTableName | `context.getTargetTableName()` | **删除**，改用 `tableGroup.getTargetTable().getName()` |
| targetFields | `context.getTargetFields()` | **删除**，改用 `tableGroup.getTargetTable().getColumn()` |
| sourceList | `context.getSourceList()` | **删除**，数据由 `syncRow.getData()` 承载 |
| targetList | `context.getTargetList()` | **删除**，同 sourceList |
| **保留** | | |
| event | `context.getEvent()` | 运行时状态（INSERT/UPDATE/DELETE） |
| pkFields | `context.getPkFields()` | 运行时预计算的主键字段列表 |
| traceId | `context.getTraceId()` | 运行时 trace 标识 |
| sourceConnectorInstance | `context.getSourceConnectorInstance()` | 运行时源连接实例 |
| targetConnectorInstance | `context.getTargetConnectorInstance()` | 运行时目标连接实例 |

#### Connector 内部访问方式（改造后）

```java
public Result insert(DatabaseConnectorInstance instance, List<SyncRow> rows) {
    SyncRow row = rows.get(0);
    // 运行时状态
    PluginContext context = row.getContext();
    String event = context.getEvent();
    
    // 配置信息（直接从 Model 获取）
    TableGroup tableGroup = row.getTableGroup();
    List<Field> targetFields = tableGroup.getTargetTable().getColumn();
    List<Field> pkFields = context.getPkFields();
    int batchSize = row.getMapping().getBatchNum();
    
    // 业务逻辑...
}
```

---

## 影响范围

### 各阶段影响

| 阶段 | 模块 | 文件数 | 改动类型 |
|------|------|--------|---------|
| Step 1 ✅ | dbsyncer-parser | ~15 | Model 贫血化（已完成） |
| Step 2 | dbsyncer-sdk + parser + common | ~20 | Model 类文件迁移 |
| Step 3 | dbsyncer-sdk + connector 模块 | ~30 | SyncRow/PluginContext 改造 |
| **总计** | | **~65** | |

---

## 风险点

### Step 2 风险

1. **Convert 迁移链过长**：`Convert` 依赖 `ConvertEnum`，`ConvertEnum` 依赖多个 Converter 实现
   - **应对**：仅迁移 `ConvertEnum` 枚举定义，不迁移 Converter 实现（留在 parser）

2. **import 冲突**：parser 中大量代码 import 了 `org.dbsyncer.parser.model.*`
   - **应对**：分两步——先复制类到 sdk，再全局替换 import（用 sed/grep 批量）

3. **SnowflakeIdWorker**：`Meta.create()` 中使用了 storage 模块的类型
   - **应对**：将 `SnowflakeIdWorker` 移到 common 层，或改为调用方传入 ID

### Step 3 风险

1. **Connector 实现众多**：所有 Connector 都需要适配新的访问方式
   - **应对**：先改 1 个验证（如 MySQL），再批量修改

2. **PluginContext 引用广泛**：许多地方直接使用 `context.getBatchSize()` 等
   - **应对**：grep 找出所有使用点，逐个替换

---

## 实施策略

### 阶段划分

| 阶段 | 任务 | 工作量 | 验证点 |
|------|------|--------|--------|
| Step 1 | Model 贫血化 | 已完成 | `mvn compile -DskipTests` 通过 |
| Step 2-1 | 无依赖类迁移（FieldMapping/ConfigModel/Enum） | 1h | 编译通过 |
| Step 2-2 | Convert 依赖链迁移 | 1.5h | 编译通过 |
| Step 2-3 | Mapping/TableGroup/Meta 迁移 | 2h | 编译通过 |
| Step 2-4 | import 全局替换 + 旧路径清理 | 1h | 全量编译通过 |
| Step 3-1 | SyncRow 改造 | 1h | 编译通过 |
| Step 3-2 | PluginContext 精简 | 1h | 编译通过 |
| Step 3-3 | Connector 适配 | 2h | 端到端测试通过 |
| **总计** | | **~9.5h** | |

---

## 下一步

1. ✅ Step 1 已完成
2. ⏳ Step 2-1：无依赖类迁移（FieldMapping/ConfigModel/Enum 移到 sdk）
3. ⏳ Step 2-2：Convert 依赖链迁移
4. ⏳ Step 2-3：Mapping/TableGroup/Meta 迁移
5. ⏳ Step 2-4：import 全局替换
6. ⏳ Step 3：SyncRow 改造 + PluginContext 精简 + Connector 适配

## 相关文档

- [ADR-0019 正文](../../adr/0019-Model层贫血化重构.md)
- [验证方案](verification-plan.md)

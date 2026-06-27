# ADR-0019: Model 层贫血化重构

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Accepted |
| 决策者 | @lixuebin |
| 参与者 | @lixuebin, opencode |
| 创建日期 | 2026-06-27 |
| 最后更新 | 2026-06-27 |
| 关联文档 | [过程文档](../history/0019/) |

## 背景

当前 Model 层（Mapping、TableGroup、Meta）直接依赖 ProfileComponent，导致模型对象与持久化逻辑紧密耦合。这带来以下问题：

1. **无法下移 sdk 模块**：Mapping/TableGroup 等核心 Model 位于 parser 层，因为依赖 ProfileComponent，无法移到 sdk 层供各 connector 直接访问
2. **违反领域驱动设计原则**：Model 承担了持久化职责，破坏了贫血模型设计
3. **单元测试困难**：需要 mock ProfileComponent 才能测试 Model 的业务逻辑
4. **代码复用受限**：其他模块无法独立使用 Model，必须依赖完整的 parser 上下文

目标：Model 层贫血化，剥离 ProfileComponent 依赖，让 Model 成为纯数据载体。

## 决策

采用**三步走方案**：

### ✅ Step 1（已完成）：Model 贫血化

移除 Model 中的 ProfileComponent 依赖，提取持久化/查询逻辑到 Service 层。

**改造内容：**

| Model | 改造 |
|-------|------|
| Meta | 移除 `ProfileComponent` 字段，方法不再持久化，由 `MetaService` 封装 |
| Mapping | 移除 `ProfileComponent` 字段，方法迁移到 `MappingModelService` |
| TableGroup | 移除 `ProfileComponent`/`ParserComponent` 字段，方法迁移到 `TableGroupModelService` |

**新建 Service：**
- `MetaService`（dbsyncer-parser）
- `MappingModelService`（dbsyncer-parser）
- `TableGroupModelService`（dbsyncer-parser）
- `ModelPersistenceService`（dbsyncer-sdk 接口 + dbsyncer-parser 实现）

**验证：** `mvn compile -DskipTests` 全模块通过，3 个集成测试通过。

### Step 2: Model 下移 SDK

将 Model 类从 parser 整体下移到 sdk，使得 SyncRow 可以直接持有 Mapping/TableGroup。

**迁移清单：**

| 类 | 源位置 | 目标位置 | 依赖处理 |
|----|--------|---------|---------|
| `ConfigModel` | parser.model | sdk.model | 仅依赖 `ConfigConstant`（已在 sdk） |
| `AbstractConfigModel` | parser.model | sdk.model | `List<Convert>` → `Convert` 需同步下移 |
| `FieldMapping` | parser.model | sdk.model | 无 parser 依赖，可直接迁移 |
| `Mapping` | parser.model | sdk.model | 依赖 `AbstractConfigModel` |
| `TableGroup` | parser.model | sdk.model | 依赖 `AbstractConfigModel`、`FieldMapping` |
| `Meta` | parser.model | sdk.model | 依赖 `ConfigModel`、`MetaEnum`、`SyncPhaseEnum` |
| `Convert` | parser.model | sdk.model | 需同步迁移 `ConvertEnum`、`ParseResult` |
| `MetaEnum` | parser.enums | sdk.enums | 纯枚举，无依赖 |
| `SyncPhaseEnum` | parser.enums | sdk.enums | 纯枚举，无依赖 |
| `ParserException` | parser | common | 供多方引用，移到 common 层 |

**额外依赖处理：**
- `SnowflakeIdWorker`：`Meta.create()` 中使用 → 改为调用方传入 ID 或移到 common
- `org.apache.logging.log4j.util.Strings`：`TableGroup.hasError()` 使用 → 替换为 `StringUtil`（已在 sdk 类路径）

### Step 3: SyncRow 改持 Mapping/TableGroup + PluginContext 精简

Model 下移到 sdk 后，SyncRow 直接持有 Mapping/TableGroup 对象。

**改造前：**
```java
public class SyncRow {
    private Map<String, Object> data;
    private PluginContext context;  // 包含配置 + runtime 信息
}
// Connector 获取配置需通过 context:
context.getBatchSize()       // 实际来自 mapping
context.getTargetFields()    // 实际来自 tableGroup
context.isForceUpdate()      // 实际来自 mapping
```

**改造后：**
```java
public class SyncRow {
    private Map<String, Object> data;
    private Mapping mapping;        // 配置来源：batchNum、forceUpdate 等
    private TableGroup tableGroup;  // 配置来源：fieldMapping、command 等
    private PluginContext context;  // 仅保留 runtime 状态
}
// Connector 直接从 SyncRow 获取配置:
syncRow.getMapping().getBatchNum()
syncRow.getTableGroup().getTargetFields()
```

**PluginContext 精简：** 去掉以下冗余字段（由 Mapping/TableGroup 提供）：
- `batchSize` → `mapping.getBatchNum()`
- `forceUpdate` → `mapping.isForceUpdate()`
- `targetFields` → `tableGroup.getTargetFields()`
- `sourceTableName` → `tableGroup.getSourceTable().getName()`
- `targetTableName` → `tableGroup.getTargetTable().getName()`
- `sourceList` → `syncRow.getData()` 已承载

保留的 runtime 字段：
- `event`（INSERT/UPDATE/DELETE）
- `traceId`
- `pkFields`
- `sourceConnectorInstance`
- `targetConnectorInstance`

**收益：** SyncRow 成为自包含的数据单元，connector 无需通过 PluginContext 间接获取配置，PluginContext 职责单一。

## 影响

- **技术影响**：
  - Step 1（已完成）：Model 贫血化，架构清晰
  - Step 2：Model 下移 sdk，消除 SyncRow 跨模块引用的根本障碍
  - Step 3：SyncRow 自包含，PluginContext 职责单一
  - 性能无显著变化
- **团队影响**：
  - 需要理解 Model 贫血化思想
  - Service 层职责增加，需要注意事务边界
  - 单元测试可独立进行，无需 mock ProfileComponent
- **时间影响**：
  - Step 1：已完成
  - Step 2：约 4-6 小时（~1 天）
  - Step 3：约 2-3 小时（~0.5 天）

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-06-27 | v1.0 | 初始版本 | @lixuebin |

## 过程文档索引

- [设计稿](../history/0019/design-01-base.md)
- [实施方案](../history/0019/design-02-plan.md)
- [验证方案](../history/0019/verification-plan.md) ← **重点**：24 个调用点逐项承接验证

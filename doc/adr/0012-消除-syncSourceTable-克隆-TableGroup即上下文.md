# ADR-0012: 消除 syncSourceTable/syncTargetTable 克隆，TableGroup 即上下文

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Proposed |
| 决策者 | 老李 |
| 参与者 | 凌曦 |
| 创建日期 | 2026-05-28 |
| 最后更新 | 2026-05-28 |
| 关联文档 | [实现说明](../history/0012/impl-01-notes.md) |

## 背景

ADR-0011 简化 `FieldMapping` 为仅存字段名后，`Picker` 构造函数与 `TableGroup.initCommand()` 各自通过遍历 `fieldMapping` + `targetTable.findColumnByName()` 构建同步字段列表。为消除重复计算，引入了 `buildSyncTables()` 方法，克隆出 `syncSourceTable` 和 `syncTargetTable` 两份完整的 `Table` 对象缓存。

此方案存在以下问题：

1. **冗余克隆**：克隆的 `Table` 对象除 `column` 列表外，其余属性（name、count 等）均为无意义的复制，且克隆操作本身有性能开销
2. **职责不清**：`TableGroup` 本质是配置+上下文载体，不应额外维护一套"克隆表"对象
3. **语义冗余**：`syncSourceTable/syncTargetTable` 本质是 `fieldMapping + sourceTable + targetTable` 的组合计算结果，物化为独立对象增加了心智负担

## 决策

**不再克隆 `syncSourceTable/syncTargetTable`，改为按需组合计算字段列表，`TableGroup` 直接作为上下文传递。**

具体方案：

1. 删除 `syncSourceTable`、`syncTargetTable` 字段及 `buildSyncTables()` 方法
2. 在 `TableGroup` 上新增两个方法：
   - `getSyncSourceFields()` — 按 `fieldMapping` 顺序，从 `sourceTable` 提取同步需要的源字段列表
   - `getSyncTargetFields()` — 按 `fieldMapping` 顺序，从 `targetTable` 提取目标字段；目标表无元数据时（如 Kafka），从源字段克隆，保留 pk 等属性
3. `Picker` 构造函数改为调用 `tableGroup.getSyncSourceFields()` / `getSyncTargetFields()`
4. `initCommand()` 中用 `getSyncSourceFields()` / `getSyncTargetFields()` 构建 `CommandConfig`，替代原先的 `syncSourceTable`/`syncTargetTable`
5. `clear()` 方法中移除对克隆表的清理（因为不再持有）

**关于 DDL 变更**：系统中已有逻辑负责在 DDL 变化时更新 `TableGroup` 中的 `sourceTable`/`targetTable` 对象，因此按需组合计算的方式天然感知 DDL 变更，无需额外 refresh 机制。全量同步中途 DDL 不影响当前任务（Picker 已在构造时持有字段副本），增量同步 DDL 刷新后新建 Picker 自动使用最新字段——**相比当前 `syncTargetTable` 缓存机制，改后增量 DDL 刷新更可靠，因为不存在缓存不失效的隐患。**

## 影响

- **技术影响**：
  - `TableGroup` 减少 ~50 行克隆代码，职责更清晰
  - `Picker` 构造函数减少 2 行引用路径
  - `initCommand()` 中 `CommandConfig` 构建需适配（当前传入 `Table`，改为传入字段列表或保留 Table 但字段按需构建）
  - 无破坏性 API 变更
- **团队影响**：无
- **时间影响**：小改动，预计 1 小时内完成

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-05-28 | v1.0 | 初始版本 | 凌曦 |

## 过程文档索引

- [实现说明](../history/0012/impl-01-notes.md)

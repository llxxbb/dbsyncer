# ADR-0017: MetaInfo 继承 Table，统一主键传递链

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Accepted |
| 决策者 | @用户 |
| 参与者 | @AI助手 |
| 创建日期 | 2026-06-26 |
| 最后更新 | 2026-06-26 |
| 关联文档 | [过程文档](../history/0017/) |

## 背景

当前系统中存在主键传递链断裂问题：
1. `Table` 和 `MetaInfo` 字段高度重叠，职责分离不清
2. `CommandConfig.primaryKeys` 已设置但从未被读取
3. `PluginContext.primaryKeys` 在全量/增量同步时未被设置，连接器只能回退到 `pluginExtInfo` 兼容逻辑
4. 主键来源混：`Table.getColumn().filter(isPk())`（旧）与 `CommandConfig.getPrimaryKeys()`（新）并存

此外，`Table` 持有 `primaryKeys` 字段导致业务场景混淆：列表浏览（无主键）与映射使用（有主键）使用同一构造函数，无法在编译时强制主键传递。

## 决策

1. **`MetaInfo extends Table`**：`Table` 回归简单，仅保留表结构信息（name, type, column, sql, indexType, count）；`MetaInfo` 作为扩展，增加 `primaryKeys` 字段
2. **`TableGroup` 改用 `MetaInfo`**：sourceTable/targetTable 类型从 `Table` → `MetaInfo`
3. **`CommandConfig` 改用 `MetaInfo`**：table 字段类型从 `Table` → `MetaInfo`
4. **列表场景保持 `Table`**：`getTable()` 返回 `List<Table>` 用于展示，不涉及主键
5. **修复 primaryKeys 数据流**：`PluginContext.setPrimaryKeys()` 使用 `tableGroup.getTargetTablePrimaryKeys()` 来源
6. **移除 `Table.primaryKeys`**：`Table` 构造函数从 6 参改为 5 参

## 影响

- **技术影响**：
  - 主键传递链完整：`TableGroup.targetTablePK` → `PluginContext.primaryKeys` → 连接器
  - `MetaInfo` 成为"含主键的完整表元数据"的唯一载体
  - `Table` 降级为"表结构信息"，列表场景专用
  - 删除 `CommandConfig.primaryKeys`（冗余字段）

- **团队影响**：
  - 需理解 `Table` 与 `MetaInfo` 的职责分离
  - 列表场景用 `Table`，映射场景用 `MetaInfo`

- **时间影响**：
  - 一次性完成，不分两波
  - 影响 5 个阶段约 30 个文件

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-06-26 | v1.0 | 初始版本 | @用户 |

## 过程文档索引

- [设计稿](../history/0017/design-01-base.md)
- [实现说明](../history/0017/impl-01-notes.md)
- [测试报告](../history/0017/test-01-report.md)

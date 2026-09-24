# ADR-0038: SqlServerCT 源表选择自动标注 CT 支持状态

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Accepted |
| 决策者 | @lxb |
| 参与者 | @lxb |
| 创建日期 | 2026-09-24 |
| 最后更新 | 2026-09-24 |
| 关联文档 | [过程文档](../history/0038/) |

## 背景

SqlServerCT 连接器使用 Change Tracking（CT）模式进行增量同步，但 CT 要求源端数据表必须启用 Change Tracking。当前创建任务时，源表选择器展示所有表（包括未启用 CT 的表和视图），用户无法区分哪些表支持 CT 模式，容易选错表导致任务失败。

需要在前端源表选择时：
1. 已启用 CT 的表显示 `[CT]` 后缀，可正常选择
2. 未启用 CT 的表灰色显示，不可选择
3. 用户能直观看到全部表及其 CT 状态

## 决策

我们决定采用**前端两次请求 + 对比标记**方案：

### 方案要点

1. **后端 Model 零改动**：`Table.java` 不新增字段，保持通用性
2. **SqlServerCTConnector 零改动**：`getTable()` 保持返回全量表
3. **新增轻量接口**：`GET /mapping/getCTTables?connectorId=xxx`，仅返回已启用 CT 的表名列表
4. **前端对比渲染**：
   - 页面加载时，若源连接器为 `SqlServerCT`，异步请求 `getCTTables`
   - 全量表名 - CT 表名 = 需要 disabled 的表
   - CT 表追加 `[CT]` 后缀显示
   - 非 CT 表灰色半透明，`cursor: not-allowed`，不可点击/双击/全选
5. **DualListSelector 通用化支持**：`disabledIds` 参数，非 CT 连接器传空数组，完全不受影响

### 被否决的方案

| 方案 | 否决原因 |
|------|---------|
| Model 层新增 `ctEnabled` 字段 | 缺乏通用性，污染公共模型 |
| 后端过滤只返回 CT 表 | 用户无法看到全貌，不知道哪些表未启用 CT |

## 影响

- **技术影响**：
  - 新增 1 个 REST 接口（`getCTTables`）
  - 新增 1 个 SQL 模板方法（`buildQueryChangeTrackingTablesSql`）
  - 前端 `editTableSelector.js` 增加异步逻辑
  - 前端 `dualListSelector.js` 增加 `disabledIds` 通用支持
- **团队影响**：无，改动集中在前后端各自模块
- **时间影响**：小改动，预计 1 天内完成
- **向后兼容**：非 SqlServerCT 连接器完全不受影响；CT 接口失败时降级为全部可选

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-09-24 | v1.0 | 初始版本 | @lxb |

## 过程文档索引

- [实现说明](../history/0038/impl-01-notes.md)

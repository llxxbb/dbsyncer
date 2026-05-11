# ADR 0007: 批量添加转换配置 — 临时主键变更为正式主键

**状态**: Proposed  
**日期**: 2026-05-11

---

## 背景

### 现状

批量添加转换配置弹窗中的"设置为临时主键"选项：
- 勾选后仅通过 `TempPKManager` 存储到浏览器 localStorage
- 后端 `targetTablePK` 参数不变，**不修改实际主键配置**
- 用户还需在字段配置页面手动保存才能生效

### 问题

- **语义混淆** — "临时主键"让人误解为"临时有效的主键"
- **操作冗余** — 需要额外步骤才能生效
- **体验割裂** — 批量配置后还要手动补操作

### 相关文档

- [ADR-0001 字段映射高级配置](0001-field-mapping-advanced-config.md) — 自定义字段 + DDL 执行
- [ADR-0002 支持编辑表映射时修改主键配置](0002-支持编辑表映射时修改主键配置.md) — 主键修改确认机制
- [batch-convert-config.md](batch-convert-config.md) — 批量添加转换配置功能说明

---

## 分析

### 1. 当前主键修改的完整链路

```
前端 targetTablePK 参数
  → TableGroupServiceImpl.edit()
  → 检测 hasPkDifference
  → 检测失败 → PrimaryKeyDifferenceException (需前端 confirmPrimaryKeyChange)
  → 检测通过 → 先 executeCustomFieldDDL(创建新字段)
  → 再 alterPrimaryKey() → SqlTemplate.buildAlterPrimaryKeySql() → DDL 执行
  → 所有 DDL 成功 → editTableGroup() 保存配置
```

已有实现：
- `SqlTemplate.buildAlterPrimaryKeySql()` — MySQL/SqlServer 已实现，default 抛异常
- `PrimaryKeyDifferenceException` — 主键变更确认弹窗机制

### 2. 批量场景下主键修改的处理

当前 `batchAddConvertToTableGroups` 为**串行处理**（递归逐个），无并发问题。

**风险点**：
- 主键变更触发 `PrimaryKeyDifferenceException` 时需前端确认
- 批量场景下无法逐个弹窗确认
- 需绕过确认机制或预确认

### 3. 不同数据库的主键修改差异

| 数据库 | 实现 | 限制 |
|--------|------|------|
| MySQL | `ALTER TABLE DROP PRIMARY KEY, ADD PRIMARY KEY(...)` | 支持 |
| SQL Server | 需先删约束再建约束 | 支持 |
| PostgreSQL | `ALTER TABLE DROP CONSTRAINT, ADD CONSTRAINT` | SqlTemplate 待实现 |
| Oracle | `ALTER TABLE DROP PRIMARY KEY, ADD CONSTRAINT...` | SqlTemplate 待实现 |
| SQLite | 不支持修改主键 | SqlTemplate 待实现 |

### 4. 错误回滚

当前 DDL 执行失败直接 throw，**无自动回滚**：
- `executeCustomFieldDDL` 失败 → 回滚配置（`profileComponent.removeTableGroup`）
- `alterPrimaryKey` 失败 → 抛异常，但配置可能已部分保存

---

## 决策

### 方案方向

| 方案 | 说明 | 优劣 |
|------|------|------|
| A. 前端预确认 | 批量前收集所有主键变更明细，一次性确认后执行 | 改动最小，安全可控 |
| B. 后端静默跳过 | 批量场景下自动跳过 `PrimaryKeyDifferenceException` | 改动小，但失去确认安全 |

### 推荐方案：A（前端预确认）

**流程**：
1. 用户勾选"设置为主键" → 前端预收集所有表组的主键变更明细
2. 弹出确认框列出所有变更：
   - 表组A: 旧主键[id] → 新主键[id, sync_flag]
   - 表组B: 旧主键[id] → 新主键[id, sync_flag]
   - 表组C: 旧主键[id,name] → 新主键[id,name, sync_flag]
3. 用户确认后，逐个表组执行 `POST /tableGroup/edit`，携带 `confirmPrimaryKeyChange=true`
4. 后端正常链路处理：`executeCustomFieldDDL` → `alterPrimaryKey` → `editTableGroup`

**不变的部分**：非批量模式（单个表组编辑）的主键变更确认逻辑完全不变，互不干扰。

---

## 影响分析

### 涉及组件

| 组件 | 改动 |
|------|------|
| `edit.js` → `addConvertToSingleTableGroup` | 构造 `targetTablePK` 参数 |
| `edit.js` → `submitBatchConvertConfig` | 增加预确认弹窗 |
| `editTable.html` | 提示文案：`临时主键` → `主键` |

### 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 误操作 | 用户可能不清楚哪些表的主键会被修改 | 预确认弹窗清晰列出所有变更 |
| 部分成功 | 某些表组 DDL 失败 | 串行处理，失败后停止后续操作，明确报告 |
| 数据库不支持 | SqlTemplate 未实现 | 校验并提前报错 |

---

*参考：ADR-0001、ADR-0002、batch-convert-config.md*

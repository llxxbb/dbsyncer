# 设计讨论记录

## 讨论时间

2026-05-13

## 核心问题

大小写分析发现了 168 处大小写相关代码，其中 6 处 P0 级风险和 5 处 P1 级风险。如何在业务层面统一处理，而不是技术层面散点修改？

## 决策过程

### 用户观点

> "以业务对象为出发点进行统一处理，而不是技术上的统一，我想减少业务复杂度，而不是技术复杂度。"

> "targetTablePK 是否可以通过 tableGroup 对象来封装？这样所有的点都有封装了，可以形成完整的闭环。"

### 关键结论

1. **Field 和 Table** 增加 `nameIgnoreCase()` 方法，用于大小写不敏感的匹配/查找
2. **TableGroup** 封装主键操作方法，形成闭环
3. **DDL 生成**保持原始大小写（由数据库 collation 决定）
4. **Schema 名**暂不动（各数据库策略差异大）
5. **拒绝工具类**：不增加 `CaseInsensitiveNameUtil`，避免技术复杂度

## 设计要点

| 对象 | 方法 | 用途 |
|------|------|------|
| `Field` | `nameIgnoreCase()` | 字段匹配/查找（小写） |
| `Table` | `nameIgnoreCase()` | 表名匹配/查找（小写） |
| `TableGroup` | `addPrimaryKeyIfAbsent()` | 主键添加（内部去重+大小写不敏感） |
| `TableGroup` | `containsPrimaryKey()` | 主键判断（大小写不敏感） |
| `TableGroup` | `primaryKeyChangedSince()` | 主键变化检测（大小写不敏感） |

## 使用原则

- **匹配/查找**：用 `nameIgnoreCase()` 或业务方法
- **展示/DDL 生成**：用 `getName()`（保持原始大小写）

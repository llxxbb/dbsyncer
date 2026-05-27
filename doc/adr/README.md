# Architecture Decision Records (ADR)

本项目使用 ADR（Architecture Decision Record）记录重要架构决策。

## ADR 列表

| 编号 | 标题 | 状态 | 日期 |
|------|------|------|------|
| [0011](0011-简化-FieldMapping-结构.md) | 简化 FieldMapping 结构 | Proposed | 2026-05-27 |
| [0010](0010-mysql-non-utf8-charset-handling.md) | MySQL 非 UTF-8 字符集列的数据一致性处理 | Proposed | 2026-05-20 |
| [0009](0009-字段差异分析增加MAPPING_ONLY检测与修复.md) | 字段差异分析增加 MAPPING_ONLY 检测与修复 | Proposed | 2026-05-14 |
| [0008](0008-field-table-case-insensitive.md) | 字段名/表名大小写不敏感的业务对象封装 | Accepted | 2026-05-13 |
| [0007](0007-batch-convert-temp-to-formal-pk.md) | 批量添加转换配置 — 临时主键变更为正式主键 | Accepted | 2026-05-11 |
| [0006](0006-sqlserver-ct-query-refactor.md) | SQL Server CT 查询重构 — RIGHT JOIN + U→I 转换 + 强制覆盖写入 | Accepted | 2026-04-28 |
| [0005](0005-sqlserver-ct-delete-race-condition.md) | SQL Server CT 数据删除竞态问题 | Accepted（核心方案被 ADR 06 替代） | 2026-04-24 |
| [0004](0004-sqlserver-ct-bigtx-optimization.md) | SQL Server CT 大事务同步优化 | Accepted | 2026-04-10 |
| [0003](0003-tablegroup-primary-key-order-fix.md) | TableGroup 主键顺序修复 | Accepted | 2026-03-30 |
| [0002](0002-支持编辑表映射时修改主键配置.md) | 支持编辑表映射时修改主键配置 | Accepted | 2026-03-20 |
| [0001](0001-field-mapping-advanced-config.md) | 字段映射高级配置 | Accepted | 2026-03-20 |

## 状态说明

- **Proposed**: 提议中，待讨论和决策
- **Accepted**: 已接受，将按此执行
- **Deprecated**: 已废弃，不再推荐
- **Superseded**: 已被新 ADR 替代

## 相关文档

- [设计文档](design/)
- [历史过程文档](history/)

## 如何创建 ADR

```bash
# 使用 decision-tracker 技能
openclaw decision create "ADR 标题"

# 或手动创建
mkdir -p doc/adr doc/history/NNNN
# 复制模板并编辑
```

---

*最后更新：2026-05-27*

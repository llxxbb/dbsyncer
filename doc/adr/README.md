# Architecture Decision Records (ADR)

本项目使用 ADR（Architecture Decision Record）记录重要架构决策。

## ADR 列表

| 编号 | 标题 | 状态 | 日期 |
|------|------|------|------|
| [0001](0001-field-mapping-advanced-config.md) | 字段映射高级配置 | Accepted | 2026-03-20 |
| [0002](0002-支持编辑表映射时修改主键配置.md) | 支持编辑表映射时修改主键配置 | Accepted | 2026-03-20 |
| [0003](0003-tablegroup-primary-key-order-fix.md) | TableGroup 主键顺序修复 | Accepted | 2026-03-30 |
| [0004](0004-sqlserver-ct-bigtx-optimization.md) | SQL Server CT 大事务同步优化 | Proposed | 2026-03-27 |

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

*最后更新：2026-03-30*

# ADR 06: SQL Server CT 写入重构 — U→I 转换 + 覆盖写入 + 去除异常检测

**状态**: Proposed  
**日期**: 2026-04-28  
**决策者**: DBSyncer 团队  
**前置 ADR**: [0005](0005-sqlserver-ct-delete-race-condition.md)（本 ADR 替代其核心方案）

---

## 背景

### 问题场景

在 SQL Server Change Tracking (CT) 同步场景中，ADR 05 采用 LEFT JOIN + 异常捕获 + CtDeleteDetector 的方案处理数据删除竞态问题。该方案存在以下问题：

1. **异常驱动流程**：依赖写入异常来检测 CT 删除，正常流程中混入异常处理路径
2. **重试复杂度**：`handleCtDeleteScenario()` 需要区分 CT 删除和其他异常，递归重试逻辑复杂
3. **CtDeleteDetector 耦合**：新增专用工具类，增加维护成本

### 核心前提

**CHANGETABLE 每行只保留最新版本数据，一次查询不会同时出现 I、U、D 操作。**

这意味着：
- 对于同一主键，CHANGETABLE 中只有一条记录
- 该记录的操作类型（`SYS_CHANGE_OPERATION`）只能是 I、U、D 之一
- 不存在同一主键既有 I 又有 U 的情况

---

## 决策

sql 改造：

- 使用 Right Join 替代 Left Join, 以消除 Insert 或 Update 无源表数据 null 字段无法执行问题。
- union all ChangeTable 中的 D 操作数据，弥补 Right Join 造成的无源表数据无法提取 D 操作的问题。

将 U 操作全部替换为 I 操作，避免目标表没有 Insert 直接 Update 的报错问题。
删除 handleCtDeleteScenario
删除 CtDeleteDetector


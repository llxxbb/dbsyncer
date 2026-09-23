# 架构决策记录 (ADR) 索引

> 最后更新：2026-09-22

## 统计概览

| 状态 | 数量 |
|------|------|
| Unknown | 34 |
| **已废弃** (Deprecated) — 核心方案已被 [ADR 06](0006-sqlserver-ct-query-refactor.md) 替代 | 1 |
| **Accepted** (2026-06-23) | 1 |
| **总计** | **36** |

## ADR 列表

### Unknown

| 编号 | 标题 | 日期 |
|------|------|------|
|  | [0001-field-mapping-advanced-config](0001-field-mapping-advanced-config.md) | - |
|  | [0002-支持编辑表映射时修改主键配置](0002-支持编辑表映射时修改主键配置.md) | - |
|  | [0003-tablegroup-primary-key-order-fix](0003-tablegroup-primary-key-order-fix.md) | - |
|  | [0004-sqlserver-ct-bigtx-optimization](0004-sqlserver-ct-bigtx-optimization.md) | - |
|  | [0006-sqlserver-ct-query-refactor](0006-sqlserver-ct-query-refactor.md) | - |
|  | [0007-batch-convert-temp-to-formal-pk](0007-batch-convert-temp-to-formal-pk.md) | - |
| 0008 | [0008-field-table-case-insensitive](0008-field-table-case-insensitive.md) | - |
| 0009 | [0009-字段差异分析增加MAPPING_ONLY检测与修复](0009-字段差异分析增加MAPPING_ONLY检测与修复.md) | - |
| 0010 | [0010-mysql-non-utf8-charset-handling](0010-mysql-non-utf8-charset-handling.md) | - |
| 0011 | [0011-简化-FieldMapping-结构](0011-简化-FieldMapping-结构.md) | - |
| 0012 | [0012-消除-syncSourceTable-克隆-TableGroup即上下文](0012-消除-syncSourceTable-克隆-TableGroup即上下文.md) | - |
| 0013 | [0013-同步任务指数退避重试机制](0013-同步任务指数退避重试机制.md) | - |
| 0014 | [0014-钉钉群消息通知机制](0014-钉钉群消息通知机制.md) | - |
| 0015 | [0015-同步任务重试排除关键字配置](0015-同步任务重试排除关键字配置.md) | - |
| 0017 | [0017-MetaInfo继承Table统一主键传递链](0017-MetaInfo继承Table统一主键传递链.md) | - |
|  | [0018-引入上下文感知的数据模型](0018-引入上下文感知的数据模型.md) | - |
| 0019 | [0019-Model层贫血化重构](0019-Model层贫血化重构.md) | - |
|  | [0020-异常同步任务监控面板](0020-异常同步任务监控面板.md) | - |
| 0021 | [0021-字段差异修复弹窗公共化](0021-字段差异修复弹窗公共化.md) | - |
| 0022 | [0022-MySQL与SqlServerCT类型映射风险分析](0022-MySQL与SqlServerCT类型映射风险分析.md) | - |
| 0023 | [0023-错误队列数据一致性修复](0023-错误队列数据一致性修复.md) | - |
| 0024 | [0024-同步管线抽象](0024-同步管线抽象.md) | - |
|  | [0025-致命关键字终止机制](0025-致命关键字终止机制.md) | - |
|  | [0026-mysql-gtid-support](0026-mysql-gtid-support.md) | - |
| 0027 | [0027-mysql-cdc-performance-optimization](0027-mysql-cdc-performance-optimization.md) | - |
| 0028 | [0028-系统配置启动期重复创建防护](0028-系统配置启动期重复创建防护.md) | - |
| 0029 | [0029-运行时配置UI化](0029-运行时配置UI化.md) | - |
| 0030 | [0030-消除-sidecar-统一延迟线程池](0030-消除-sidecar-统一延迟线程池.md) | - |
| 0031 | [0031-B类配置业务热重载](0031-B类配置业务热重载.md) | - |
| 0032 | [0032-SPA单容器页面模型](0032-SPA单容器页面模型.md) | - |
| 0033 | [0033-任务列表接口投影](0033-任务列表接口投影.md) | - |
| 0034 | [0034-任务面板组件化](0034-任务面板组件化.md) | - |
| 0035 | [0035-报警按分组配置](0035-报警按分组配置.md) | - |
| 0036 | [0036-增量终止语义与快照点一致性修复](0036-增量终止语义与快照点一致性修复.md) | - |

### **已废弃** (Deprecated) — 核心方案已被 [ADR 06](0006-sqlserver-ct-query-refactor.md) 替代

| 编号 | 标题 | 日期 |
|------|------|------|
|  | [0005-sqlserver-ct-delete-race-condition](0005-sqlserver-ct-delete-race-condition.md) | - |

### **Accepted** (2026-06-23)

| 编号 | 标题 | 日期 |
|------|------|------|
| 0016 | [0016-日志查询功能分散到任务面板](0016-日志查询功能分散到任务面板.md) | - |

## 快速链接

- [创建新 ADR](#创建新-adr)
- [更新 ADR 状态](#更新-adr-状态)
- [ADR 模板](#adr-模板)

---

## 使用指南

### 创建新 ADR

```bash
openclaw adr create "决策标题"
```

### 更新 ADR 状态

```bash
openclaw adr status 0001 Accepted
```

### 重新生成索引

```bash
openclaw adr index
```

### 查看进度报告

```bash
openclaw adr report
```

---

_本索引由 `generate-index.js` 自动生成_

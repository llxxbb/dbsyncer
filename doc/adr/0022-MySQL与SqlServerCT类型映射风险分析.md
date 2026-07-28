# ADR-0022: MySQL 与 SqlServerCT 类型映射风险分析

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Accepted |
| 决策者 | 待定 |
| 创建日期 | 2026-07-28 |

## 背景

MySQL 与 SqlServerCT 之间的数据同步涉及异构数据库类型转换。转换链路为：

```
源库类型 →[源SyncConverter]→ 标准类型 →[目标SyncConverter]→ 目标库类型 →[目标Template]→ DDL
```

SqlServerCT 继承 SqlServerConnector，复用 `SqlServerSyncConverter`。以下列出经代码核实确认存在风险的映射。

## 决策

### 一、MySQL → SqlServerCT

| # | 源类型 | 标准类型 | 当前目标类型 | 风险 | 建议目标类型 |
|---|--------|---------|------------|------|------------|
| 1 | `TINYINT` | BYTE | `TINYINT` | MySQL TINYINT 有符号 -128\~127；SQL Server TINYINT 无符号 0\~255，负数无法写入 | `SMALLINT` |
| 2 | `BIGINT UNSIGNED` | UNSIGNED_LONG | `DECIMAL(18,0)` | 无符号 BIGINT 需要 20 位十进制，`DECIMAL(18,0)` 容量不足 | `DECIMAL(20,0)` |

### 二、SqlServerCT → MySQL

| # | 源类型 | 标准类型 | 当前目标类型 | 风险 | 建议目标类型 |
|---|--------|---------|------------|------|------------|
| 1 | `TINYINT` / `TINYINT IDENTITY` | BYTE | `TINYINT` | SQL Server TINYINT 为 0\~255；MySQL TINYINT 为 -128\~127，128\~255 溢出 | `TINYINT UNSIGNED` |
| 2 | `FLOAT` | FLOAT | `FLOAT` | SQL Server FLOAT 为双精度 8 字节；MySQL FLOAT 为单精度 4 字节，精度损失 | `DOUBLE` |
| 3 | `DATE` | TIMESTAMP | `DATETIME` | 日期列引入无业务含义的时间部分 | `DATE` |
| 4 | `TIME` | TIMESTAMP | `DATETIME` | 时间列引入无业务含义的日期部分 | `TIME` |

### 三、代码依据

| 文件 | 职责 |
|------|------|
| `SqlServerSyncConverter.java` | SQL Server 标准类型 → 目标类型映射 |
| `MySQLSyncConverter.java` | MySQL 标准类型 → 目标类型映射 |
| `SqlServerTemplate.java` | SQL Server DDL 类型细分（`convertToDatabaseType`） |
| `MySQLTemplate.java` | MySQL DDL 类型细分（`convertToDatabaseType`） |

## 影响

- **技术影响**：识别出 6 处类型映射风险，需逐项评估是否修复
- **团队影响**：修复涉及 SyncConverter 映射表和 Template DDL 生成逻辑

## 变更历史

| 日期 | 版本 | 变更内容 |
|------|------|----------|
| 2026-07-28 | v1.0 | 初始版本 |

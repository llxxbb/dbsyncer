# ADR-0010: MySQL 非 UTF-8 字符集列的数据一致性处理

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Proposed |
| 决策者 | 老李 |
| 参与者 | 老李, 凌曦 |
| 创建日期 | 2026-05-20 |
| 最后更新 | 2026-05-20 |
| 关联文档 | [过程文档](../history/0010/) |

## 背景

### 问题描述

MySQL 列定义 `creator varchar(32) CHARACTER SET utf16`，同步到 Kafka 后数据不一致：
- **MySQL 实际值**: `433`
- **Kafka 中值**: `"\u00004\u00003\u00003"`

### 根因分析

**数据流转链路：**

```
MySQL binlog → BinaryLogRemoteClient → RowChangedEvent → Picker.exchange() → SchemaResolver.merge(byte[], Field) → Kafka
```

**问题点：**

1. `CHARACTER SET utf16` 的列在 binlog 中以 **UTF-16BE** 编码存储原始字节
2. `BinaryLogRemoteClient` 提取的 `byte[]` 是 UTF-16BE 编码的原始字节
3. `MySQLStringType.merge(byte[], Field)` 一律用 `StandardCharsets.UTF_8` 解码
4. UTF-16BE 字节 `[0x00, 0x34, 0x00, 0x33, 0x00, 0x33]` 被误解码为 `"\u00004\u00003\u00003"`

**影响范围：**
- `MySQLStringType`（VARCHAR/CHAR）
- `MySQLTextType`（TEXT）
- 所有使用非 UTF-8 字符集的字符串列（utf16, utf32, gbk, latin1 等）

## 候选方案

### 方案 A：运行时启发式检测（Heuristics）

**思路：** 在 `merge()` 中通过字节模式判断编码

```java
protected String merge(Object val, Field field) {
    if (val instanceof byte[]) {
        byte[] bytes = (byte[]) val;
        if (isUtf16Be(bytes)) return new String(bytes, StandardCharsets.UTF_16BE);
        if (isUtf16Le(bytes)) return new String(bytes, StandardCharsets.UTF_16LE);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    return throwUnsupportedException(val, field);
}
```

| 优点 | 缺点 |
|------|------|
| 改动最小，仅修改 `merge()` 方法 | 非 ASCII 内容可能误判（如 UTF-8 中文可能误判为 UTF-16） |
| 无需修改 Field 模型 | 无法处理 gbk/latin1 等非 Unicode 编码 |
| 向后兼容，不影响已有同步任务 | 运行时性能开销（需遍历字节数组） |

### 方案 B：Field 增加 charset 字段（推荐）

**思路：** DDL 解析时从 `ColDataType.characterSet` 提取字符集，写入 `Field`，`merge()` 时按精确字符集解码

**改造点：**
1. `Field` 增加 `charset` 字段
2. `StringType.handleDDLParameters()` 从 `ColDataType.getCharacterSet()` 写入 `Field.charset`
3. `MySQLStringType.merge()` 用 `field.getCharset()` 精确解码

| 优点 | 缺点 |
|------|------|
| 100% 准确，无歧义 | 需修改 Field 模型（向后兼容，新增字段默认为 null） |
| 支持所有字符集（utf16/utf32/gbk/latin1 等） | 改动范围稍大（Field + handleDDLParameters + merge） |
| 符合"配置驱动"理念 | |
| 无运行时开销 | |

### 方案 C：Binlog 层统一转换

**思路：** 在 `BinaryLogRemoteClient` 读取 binlog 时，根据列元数据统一转换为 UTF-8

| 优点 | 缺点 |
|------|------|
| 源头解决，下游无需关心编码 | BinaryLogRemoteClient 依赖第三方库，改造难度大 |
| | 需要维护列字符集元数据映射 |

## 决策

**选择方案 B：Field 增加 charset 字段**

**理由：**
1. **准确性优先**：同步场景对数据一致性要求极高，不能接受启发式误判
2. **已有基础设施**：jsqlparser 的 `ColDataType.characterSet` 已支持字符集解析，无需额外开发
3. **可扩展性**：`Field.charset` 可用于其他场景（如目标端 DDL 生成、跨库同步类型映射）
4. **改动可控**：涉及 3 个文件，改动点清晰，不影响现有同步任务

## 影响

### 技术影响

| 文件 | 改动 |
|------|------|
| `dbsyncer-sdk/Field.java` | 新增 `String charset` 字段及 getter/setter |
| `dbsyncer-sdk/StringType.java` | `handleDDLParameters()` 增加 `field.setCharset(colDataType.getCharacterSet())` |
| `dbsyncer-connector-mysql/MySQLStringType.java` | `merge()` 使用 `Charset.forName(field.getCharset())` 解码，null 时降级 UTF-8 |
| `dbsyncer-connector-mysql/MySQLTextType.java` | 同上 |

### 兼容性

- **Field.charset 为 null**：按 UTF-8 解码（保持现有行为）
- **已运行任务**：不受影响（Field 新增字段默认 null）
- **DDL 解析**：`ColDataType.characterSet` 已在 jsqlparser 中支持，无需额外配置

## 验收标准

1. **UTF-16 列**：`varchar(32) CHARACTER SET utf16` 值 `433` → Kafka 中 `433`
2. **UTF-8 列**：普通 VARCHAR 列同步不受影响
3. **无字符集声明**：`varchar(32)`（无 CHARACTER SET）→ 按 UTF-8 解码
4. **其他字符集**：`varchar(32) CHARACTER SET gbk` → 按 GBK 解码
5. **TEXT 类型**：`text CHARACTER SET utf16` → 同样正确处理
6. **向后兼容**：已运行的同步任务不受影响

## 其他数据库对比

| 数据库 | 采集方式 | 数据格式 | 是否有此问题 |
|--------|----------|----------|-------------|
| MySQL | binlog 原生协议 | `byte[]`（原始二进制） | ✅ 有 |
| SQL Server | JDBC 查询 CDC/CT 系统表 | `rs.getString()`（JDBC 已转码） | ❌ 无 |
| Oracle | JDBC 查询 CDC 视图 | `rs.getString()` | ❌ 无 |
| PostgreSQL | JDBC 查询 logical decoding | 待确认 | ❓ |

**结论：** MySQL binlog 是唯一直接暴露原始字节的采集方式，JDBC 驱动的数据库由 JDBC 负责编码转换。

## 后续优化

1. **字符集别名映射**：MySQL `utf8` = `utf8mb3`，`utf8mb4` 等别名需要统一映射
2. **目标端字符集**：Kafka/ES 等目标端是否需要保留原始字符集信息
3. **其他数据库**：SQL Server/Oracle 的 NVARCHAR/NCHAR 是否需要同理处理

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-05-20 | v1.0 | 初始版本，提出方案 B 为推荐方案 | 凌曦 |

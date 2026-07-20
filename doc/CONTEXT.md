# DbSyncer Context

**领域模型** - 数据同步中间件的核心词汇表和通用语言

## 项目概述

DBSyncer 是一款开源的数据同步中间件，支持 MySQL、Oracle、SqlServer、PostgreSQL、Elasticsearch、Kafka、File 等多种数据源之间的实时数据同步。支持全量同步和增量同步两种模式。

## 核心概念

### Mapping (映射)

两个数据源之间的映射关系配置，定义了源表和目的表的对应关系。

- **Source Connector**: 源数据库连接器
- **Target Connector**: 目标数据库连接器  
- **Table Mapping**: 表级别映射配置

### TableGroup (表组)

单个 Mapping 下的同步任务配置单元，包含：

- **源表配置**: `sourceTable` - 源数据库的表结构
- **目标表配置**: `targetTable` - 目标数据库的表结构
- **字段映射**: `convert` - 字段级别的转换规则
- **过滤条件**: `filter` - 行级别的过滤规则

### Convert (转换)

字段级别的转换逻辑：

- **convertName**: 转换名称
- **convertCode**: 转换代码（如 `fixed_value`, `concat`, `sql_func`）
- **args**: 转换参数
- **isRoot**: 是否为根节点（用于组合转换）
- **fieldMetadata**: 自定义字段元数据（类型、长度、精度、注释等）

### Connector (连接器)

支持的数据源类型：

- **Database**: 关系型数据库（MySQL, Oracle, PostgreSQL, SQL Server）
- **NoDatabase**: 非关系型数据源（Elasticsearch, Kafka, File）

### Parser (解析器)

负责从源数据库解析数据：

- **DDL 解析**: 解析数据定义语言
- **Data 解析**: 解析业务数据
- **增量解析**: 解析增量变更（CDC）

### AbstractDataType (抽象数据类型)

统一的类型系统，用于跨数据库类型转换：

- **标准类型编码**: 使用 `java.sql.Types` 常量
- **类型映射**: 不同数据库类型之间的映射关系
- **大小写处理**: 支持大小写不敏感匹配

### DDL Intermediate Representation（DDL 中间表示 / IR）

异构数据库 DDL 同步的数据库无关枢纽结构，使源库和目标库的 DDL 方言解耦。

**架构流程：**

```
源库 DDL ──SourceToIRConverter──▶ IR ──IRToTargetConverter──▶ 目标库 DDL
```

**路由规则**（`DDLParserImpl`）：
- 同构 + 无 CHANGE 操作：原生 SQL 仅替换表名，不产生 IR
- 异构 或 含 CHANGE 操作：走 IR 转换链路

**IR 核心数据**（`DDLIntermediateRepresentation`）：

| 字段 | 作用 |
|---|---|
| `tableName` / `schema` | 目标表名与模式名 |
| `columnsByOperation` | 按 `AlterOperation`（ADD/MODIFY/DROP/CHANGE）分组的列列表 |
| `oldToNewColumnNames` | CHANGE 操作的旧→新字段名映射 |

**转换实现：**

- **源→IR**：`MySQLToIRConverter`、`SQLServerToIRConverter` 等，继承 `AbstractSourceToIRConverter`
  - 通过 `SchemaResolver.toStandardTypeFromDDL` 将源库类型标准化为 `Field`（`type` 为 `java.sql.Types` 数值码，`typeName` 为标准名）
  - 提取列规范：`NOT NULL` / `DEFAULT` / `COMMENT`
  - 处理数据库方言差异（如 SQL Server 的 `UNSPECIFIC` 操作推断）
- **IR→目标**：`IRToMySQLConverter`、`IRToSQLServerConverter` 等，实现 `IRToTargetConverter`
- **非数据库连接器**（Kafka 等）：复用源端的 `IRToTargetConverter` 输出标准 SQL

**关键文件：**
- IR 模型：`dbsyncer-sdk/.../parser/ddl/ir/DDLIntermediateRepresentation.java`
- 转换器接口：`dbsyncer-sdk/.../parser/ddl/converter/`（`SourceToIRConverter`、`IRToTargetConverter`）
- 抽象基类：`AbstractSourceToIRConverter`、`AbstractIRToTargetConverter`
- 解析器入口：`dbsyncer-parser/.../ddl/impl/DDLParserImpl.java`

## 同步模式

### Full Sync (全量同步)

一次性复制全部数据：

1. 读取源表全量数据
2. 应用转换规则
3. 写入目标表

### Incremental Sync (增量同步)

实时捕获和复制数据变更：

- **MySQL**: Binlog 监听
- **SQL Server**: CDC (Change Data Capture)
- **Oracle**: LogMiner
- **PostgreSQL**: WAL 流复制

### Snapshot (快照)

全量同步的阶段性状态快照，用于：

- **进度跟踪**: 记录已同步的数据范围
- **断点续传**: 中断后从上次快照继续
- **一致性保证**: 确保数据一致性

## DDL 同步

支持在同步过程中自动同步表结构变更：

1. **监听 DDL**: 监听源数据库的 DDL 操作
2. **解析变更**: 解析 ALTER TABLE, CREATE TABLE 等
3. **执行 DDL**: 在目标数据库执行对应的 DDL
4. **更新元数据**: 更新 TableGroup 的表结构信息

支持的 DDL 操作：

- **ADD COLUMN**: 添加字段
- **DROP COLUMN**: 删除字段
- **MODIFY COLUMN**: 修改字段
- **RENAME COLUMN**: 重命名字段

## 数据模型

### Core Models

- **TableGroup**: 同步任务配置模型
- **Mapping**: 数据表映射关系模型
- **MappingField**: 字段映射模型
- **TableGroupFilter**: 表组过滤条件
- **TableGroupConvert**: 表组转换配置
- **MetaInfo**: 元数据信息（表、字段、主键等）
- **Field**: 字段定义（名称、类型、长度、精度等）

### Domain Models

- **Table**: 表定义（表名、字段列表、主键等）
- **Partition**: 分区信息
- **Index**: 索引信息
- **PrimaryKey**: 主键约束
- **ForeignKey**: 外键约束
- **UniqueKey**: 唯一键约束

### Log Models

- **Log**: 操作日志（使用 `modelType + modelId` 双字段）
- **Monitor**: 同步监控数据
- **Progress**: 同步进度信息

## 架构决策参考

相关 ADR 文档：

- [0001-Field Mapping 高级配置](./adr/0001-field-mapping-advanced-config.md) - 自定义字段元数据编辑
- [0008-字段表名大小写不敏感](./adr/0008-field-table-case-insensitive.md) - 大小写处理
- [0009-字段差异分析](./adr/0009-字段差异分析增加 MAPPING_ONLY 检测与修复.md) - 字段差异检测
- [0018-引入上下文感知的数据模型](./adr/0018-引入上下文感知的数据模型.md) - 数据模型重构

## 业务规则

### 字段映射规则

1. **主键强制映射**: 主键字段必须映射
2. **类型兼容性**: 源字段类型必须与目标类型兼容
3. **长度兼容性**: 目标字段长度必须 >= 源字段长度
4. **精度兼容性**: 目标字段精度必须 >= 源字段精度
5. **大小写处理**: 字段名比较使用大小写不敏感匹配（参考 ADR-0008）

### 同步流程规则

1. **先全量后增量**: 首次同步必须先执行全量，再启用增量
2. **快照保进度**: 每次同步后记录快照，确保断点续传
3. **错误可恢复**: 单个行记录出错不影响其他记录
4. **DDL 实时同步**: DDL 操作实时触发，不等待数据同步完成

### 配置验证规则

1. **连接器检查**: 源和目标连接器必须有效
2. **表存在检查**: 源表必须存在于源数据库
3. **权限检查**: 连接器必须有足够的权限（SELECT, INSERT, DDL 等）
4. **冲突检查**: 同步任务名称必须唯一

## 常用术语

| 术语 | 说明 |
|------|------|
| 同步任务 | TableGroup，一个完整的同步配置 |
| 映射 | Mapping，源表到目标表的对应关系 |
| 转换器 | Convert，字段级别的转换逻辑 |
| 过滤器 | Filter，行级别的过滤条件 |
| 元数据 | MetaInfo，数据库表的结构和约束信息 |
| DDL | Data Definition Language，数据定义语言 |
| CDC | Change Data Capture，变更数据捕获 |
| 快照 | Snapshot，同步进度快照 |
| 增量 | Incremental，基于日志的实时同步 |
| 全量 | Full，一次性全量数据复制 |

## 开发约定

### 大小写处理

- **字段名比较**: 使用大小写不敏感匹配（参考 ADR-0008）
  - 推荐：`field.matchesName(targetName)`
  - 禁用：`.equalsIgnoreCase()`, `.toLowerCase()`, `.toUpperCase()`

### 日志记录

- **日志模型**: 使用 `modelType + modelId` 双字段关联
- **日志级别**: ERROR 记录异常，WARN 记录警告，INFO 记录操作

### 异常处理

- **可恢复异常**: 记录日志，跳过当前行，继续处理
- **不可恢复异常**: 记录日志，停止任务，需要人工干预

## 外部依赖

### 数据库版本支持

| 数据库 | 最低版本 | 推荐版本 |
|--------|----------|----------|
| MySQL | 5.7.19 | 8.0+ |
| Oracle | 10g | 19c |
| SQL Server | 2008 | 2019+ |
| PostgreSQL | 9.5.25 | 13+ |
| Elasticsearch | 6.0.0 | 7.x/8.x |

### Java 环境

- **JDK 版本**: Java 8
- **构建工具**: Maven 3.6+
- **Spring Boot**: 2.5.14

## 参考资源

- **项目文档**: [`doc/history/`](./history/) - 实现细节文档
- **设计文档**: [`doc/design/`](./design/) - 架构设计文档
- **测试用例**: [`doc/tests/`](./tests/) - 测试用例清单
- **数据字典**: [`doc/data-structure/`](./data-structure/) - 数据结构定义

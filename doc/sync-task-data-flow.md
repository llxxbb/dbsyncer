# 同步任务数据流分析：源端到目标端的载体演进

## 一、概述

同步任务是 DBSyncer 的核心功能，负责将数据从源端（Source）同步到目标端（Target）。数据在整个生命周期中经历多次形态转换，每个阶段由不同的**载体（Carrier）**承载。本文档详细分析数据在各环境中的载体结构及其演进过程。

---

## 二、核心模型层次

### 2.1 Mapping（同步任务定义）

**位置**: `dbsyncer-sdk/.../model/Mapping.java`

同步任务的顶层配置，定义"从哪来、到哪去、怎么同步"：

### 2.2 TableGroup（表映射组）

**位置**: `dbsyncer-sdk/.../model/TableGroup.java`

一个 Mapping 包含多个 TableGroup，每个 TableGroup 定义一对源表到目标表的映射：

### 2.3 Meta（运行时状态）

**位置**: `dbsyncer-sdk/.../model/Meta.java`

任务的运行时"影子"，跟踪执行进度：

---

## 三、连接器体系（源端与目标端）

### 3.1 SPI 接口

**位置**: `dbsyncer-sdk/.../spi/ConnectorService.java`

所有连接器的核心接口，定义读/写/元数据/DDL 等全部能力：

### 3.2 连接器工厂

**位置**: `dbsyncer-connector/.../base/ConnectorFactory.java`

通过 Java SPI（`ServiceLoader`）加载所有 `ConnectorService` 实现，提供连接池管理和统一调度：

- `pool`：连接器实例缓存池（`ConcurrentHashMap`）
- `service`：连接器服务注册表（按类型）
- `writeBatch(syncRows)`：统一写入入口，根据 event 类型分发到 insert/update/delete/upsert

### 3.3 已支持的连接器

| 连接器 | 模块路径 |
|--------|----------|
| MySQL | `dbsyncer-connector-mysql/` |
| Oracle | `dbsyncer-connector-oracle/` |
| PostgreSQL | `dbsyncer-connector-postgresql/` |
| SQL Server | `dbsyncer-connector-sqlserver/` |
| Elasticsearch | `dbsyncer-connector-elasticsearch/` |
| Kafka | `dbsyncer-connector-kafka/` |
| File | `dbsyncer-connector-file/` |

---

## 四、数据载体演进（核心链路）

数据在管道中经历 **5 个关键载体形态**，每个载体对应不同的处理阶段：

### 4.1 ChangedEvent（变更事件）—— 源端产出

**位置**: `dbsyncer-sdk/.../listener/ChangedEvent.java`

Listener 从源端捕获的原始变更事件，是数据进入管道的**第一个载体**。

| 方法 | 含义 |
|------|------|
| `getType()` | 事件类型：DDL / SCAN / ROW |
| `getSourceTableName()` | 变更表名 |
| `getEvent()` | 操作类型（INSERT/UPDATE/DELETE） |
| `getChangedOffset()` | 增量偏移量（binlog 位点） |
| `getSql()` | DDL SQL 语句 |
| `getChangedRow()` | 变更行数据 `List<Object>` |

**三种具体事件类型**：

| 事件类 | 位置 | 数据内容 |
|--------|------|----------|
| `RowChangedEvent` | `sdk/.../listener/event/RowChangedEvent.java` | CDC 行变更：`changedRow`（after）+ `beforeRow`（before）+ `columnNames` |
| `ScanChangedEvent` | `sdk/.../listener/event/ScanChangedEvent.java` | 定时扫描变更 |
| `DDLChangedEvent` | `sdk/.../listener/event/DDLChangedEvent.java` | DDL 结构变更 |

**数据形态**：`List<Object>`（按列序排列的原始值）

---

### 4.2 WriterRequest（写入请求）—— 进入缓冲区

**位置**: `dbsyncer-parser/.../model/WriterRequest.java`

ChangedEvent 被包装为 WriterRequest，进入 BufferActuator 队列：

| 字段 | 类型 | 含义 |
|------|------|------|
| `row` | List\<Object\> | 行数据（after image） |
| `beforeRow` | List\<Object\> | 旧行数据（UPDATE 的 before image） |
| `columnNames` | List\<String\> | CDC 列名列表 |
| 继承自 `AbstractWriter` | | |
| `typeEnum` | TypeEnum | 事件类型（DDL/SCAN/ROW） |
| `changedOffset` | ChangedOffset | 增量偏移量 |
| `tableName` | String | 源表名 |
| `event` | String | INSERT/UPDATE/DELETE |
| `sql` | String | DDL SQL |
| `traceId` | String | 追踪 ID |

**数据形态**：`List<Object>` + 元数据（表名、事件类型、偏移量）

---

### 4.3 WriterResponse（写入响应）—— 批处理聚合

**位置**: `dbsyncer-parser/.../model/WriterResponse.java`

多个 WriterRequest 按表名分区聚合为 WriterResponse：

| 字段 | 类型 | 含义 |
|------|------|------|
| `dataList` | List\<List\<Object\>\> | 聚合的多行数据 |
| `beforeRows` | List\<List\<Object\>\> | 对应的旧值行 |
| `columnNames` | List\<String\> | CDC 列名列表 |
| `batchIndex` | int | 批次序号（保序用） |

**数据形态**：`List<List<Object>>`（多行聚合，按表名分区）

---

### 4.4 SyncRow（同步数据行）—— 核心载体

**位置**: `dbsyncer-sdk/.../plugin/SyncRow.java`

**这是数据在管道中最核心的载体**，贯穿映射、转换、写入全过程。

| 字段 | 类型 | 含义 |
|------|------|------|
| `data` | Map\<String, Object\> | **目标数据**（字段名 -> 值） |
| `context` | PluginContext | 插件上下文（含源/目标连接器实例、事件类型） |
| `mapping` | Mapping | Mapping 配置引用 |
| `tableGroup` | TableGroup | TableGroup 配置引用 |
| `beforeRow` | List\<Object\> | UPDATE 旧值 |
| `columnNames` | List\<String\> | CDC 列名列表 |

**数据形态**：`Map<String, Object>`（目标字段名 -> 值），每条数据自带完整上下文。

**设计哲学**：SyncRow 是"自描述"的数据载体，持有配置模型和连接器实例的引用，插件可以直接访问所有上下文信息，避免层层传递参数。

---

### 4.5 ChangedOffset（增量偏移量）—— 位点追踪

**位置**: `dbsyncer-sdk/.../model/ChangedOffset.java`

| 字段 | 类型 | 含义 |
|------|------|------|
| `metaId` | String | 所属任务 ID |
| `nextFileName` | String | binlog 文件名 |
| `position` | Long | 偏移位置（binlog position / LSN 等） |

---

## 五、处理管道（各环境详解）

### 5.1 全量同步管道

```
┌─────────────────────────────────────────────────────────────────┐
│  FullPuller.start()                                             │
│  位置: dbsyncer-manager/.../impl/FullPuller.java               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  FullSyncExecutor.executeTableGroup()                           │
│  位置: dbsyncer-parser/.../impl/FullSyncExecutorImpl.java      │
│                                                                 │
│  1. 上下文初始化: 创建 PluginContext, 连接源/目标                 │
│  2. 流式读取源数据: databaseTemplate.queryForStream()           │
│     -> 返回 Stream<Map<String, Object>>                        │
│  3. 分批处理 (每 batchNum 条):                                  │
│     ┌─────────────────────────────────────────────────────┐     │
│     │  processTableGroupDataBatch()                       │     │
│     │                                                     │     │
│     │  3a. 字段映射: Picker.pickTargetData()              │     │
│     │      List<Map> -> List<SyncRow>                     │     │
│     │                                                     │     │
│     │  3b. 参数转换: ConvertUtil.convert()                │     │
│     │      对 SyncRow.data 应用转换规则                    │     │
│     │                                                     │     │
│     │  3c. 写入目标: connectorFactory.writeBatch()        │     │
│     │      -> ConnectorService.insert/update/delete       │     │
│     │                                                     │     │
│     │  3d. 更新统计: flushStrategy.flushFullData()        │     │
│     └─────────────────────────────────────────────────────┘     │
│  4. 更新游标: PrimaryKeyUtil.getLastCursors() (断点续传)         │
└─────────────────────────────────────────────────────────────────┘
```

**全量管道载体演进**：

```
数据库行 -> Stream<Map<String, Object>> -> Picker -> List<SyncRow> -> ConvertUtil -> writeBatch()
```

### 5.2 增量同步管道

```
┌─────────────────────────────────────────────────────────────────┐
│  IncrementPuller.start()                                        │
│  位置: dbsyncer-manager/.../impl/IncrementPuller.java          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  1. 获取源/目标连接器, 创建 ParserConsumer                       │
│  2. 创建 Listener (CDC/定时), 注册 ParserConsumer               │
│  3. listener.start() -- 开始监听变更事件                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
          ==================|================== 事件驱动循环
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  Listener 捕获变更 -> ChangedEvent                              │
│  位置: dbsyncer-sdk/.../listener/                               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  ParserConsumer.changeEvent()                                   │
│  位置: dbsyncer-parser/.../consumer/ParserConsumer.java        │
│  -> 包装为 WriterRequest                                        │
│  -> 放入 MetaBufferActuator 的 BlockingQueue                    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
          ==================|================== 定时批处理
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  AbstractBufferActuator.submit()                                │
│  位置: dbsyncer-parser/.../flush/AbstractBufferActuator.java   │
│                                                                 │
│  - 从队列 poll WriterRequest                                    │
│  - 按表名分区 (getPartitionKey)                                  │
│  - 聚合为 WriterResponse (分区合并)                               │
│  - 按偏移量排序 (process)                                        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  MetaBufferActuator.pull()                                      │
│  位置: dbsyncer-parser/.../flush/impl/MetaBufferActuator.java  │
│                                                                 │
│  根据事件类型分发:                                                │
│  ┌─────────────────────────────────────────────────────┐        │
│  │  [DDL] -> parseDDL()                                │        │
│  │    DDL 解析 -> 目标执行 -> 更新字段映射 -> 持久化      │        │
│  │                                                     │        │
│  │  [SCAN/ROW] -> distributeTableGroup()               │        │
│  │    1. 构建字段索引映射 buildFieldIndexMap()           │        │
│  │    2. 字段映射: Picker.pickTargetData()              │        │
│  │       List<List<Object>> -> List<SyncRow>            │        │
│  │    3. 参数转换: ConvertUtil.convert()                │        │
│  │    4. 批量写入: connectorFactory.writeBatch()        │        │
│  │    5. 持久化统计: flushStrategy.flushIncrementData() │        │
│  └─────────────────────────────────────────────────────┘        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  afterProcess()                                                 │
│  - listener.refreshEvent(offset) -- 更新增量位点                 │
│  - ParserConsumer.flushEvent() -- 持久化 snapshot               │
└─────────────────────────────────────────────────────────────────┘
```

**增量管道载体演进**：

```
ChangedEvent -> WriterRequest -> BlockingQueue -> WriterResponse(聚合)
  -> Picker -> List<SyncRow> -> ConvertUtil -> writeBatch()
```

### 5.3 混合模式管道（全量 + 增量）

**位置**: `dbsyncer-manager/.../impl/FullIncrementPuller.java`

```
FullIncrementPuller.start()
    │
    ├── [FULL 阶段]
    │     1. recordIncrementStartPoint() -- 记录增量起始位点
    │     2. fullPuller.start() -- 启动全量
    │     3. 设置 phaseHandler 回调
    │     4. 全量完成 -> 自动启动增量
    │
    └── [INCREMENTAL 阶段]
          直接启动增量同步
```

### 5.4 手动同步管道（重试/手动触发，用于对接错误队列）

**位置**: `dbsyncer-biz/.../impl/DataSyncServiceImpl.java`

手动同步用于重试失败数据或人工触发单条同步，其核心特点是**绕过缓冲队列，同步执行**。

#### 5.4.1 入口与参数

```java
// DataSyncService.sync(Map<String, String> params)
// 参数: metaId (任务ID) + messageId (失败记录ID)
// 可选: retryDataParams (修改后的同步值 JSON)
```

#### 5.4.2 完整处理流程

```
┌─────────────────────────────────────────────────────────────────┐
│  DataSyncServiceImpl.sync()                                     │
│  位置: dbsyncer-biz/.../impl/DataSyncServiceImpl.java:158      │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  1. 从存储中加载失败数据                                          │
│     getData(metaId, messageId)                                  │
│     -> storageService.query() (Lucene 查询)                     │
│     -> 返回 Map row (含 BINLOG_DATA 二进制数据)                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  2. 解析 Binlog 数据                                             │
│     getBinlogData(row, false)                                   │
│     -> BinlogMap.parseFrom(bytes) (Protobuf 反序列化)            │
│     -> BinlogMessageUtil.deserializeValue() 按源表字段类型还原     │
│     -> 返回 Map binlogData (源字段名 -> 原始值)                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  3. 加载 TableGroup 配置                                         │
│     profileComponent.getTableGroup(tableGroupId)                │
│     -> 获取源表/目标表结构、字段映射关系                            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  4. 判断事件类型，分发处理                                         │
│     event = row.get(DATA_EVENT)                                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
              v                           v
┌─────────────────────────┐  ┌─────────────────────────────────┐
│  [DDL 事件]              │  │  [ROW 事件]                      │
│  event = ALTER           │  │  event = INSERT/UPDATE/DELETE    │
└────────────┬────────────┘  └────────────────┬────────────────┘
             │                                │
             v                                v
┌─────────────────────────┐  ┌─────────────────────────────────┐
│  反序列化 DDLConfig       │  │  可选: 合并用户修改的值            │
│  JsonUtil.jsonToObj()    │  │  retryDataParams -> binlogData   │
│  创建 DDLChangedEvent    │  │  convertValue() 类型转换          │
│  设置 metaId             │  │                                 │
└────────────┬────────────┘  │  按 sourceFields 顺序构建:        │
             │               │  - changedRow: List<Object>       │
             │               │  - columnNames: List<String>      │
             │               │  创建 RowChangedEvent              │
             │               │  设置 metaId                      │
             │               └────────────────┬────────────────┘
             │                                │
             └─────────────┬──────────────────┘
                           │
                           v
┌─────────────────────────────────────────────────────────────────┐
│  5. 直接执行（绕过缓冲队列）                                      │
│     MetaBufferActuator.executeDirectly(event)                   │
│     位置: dbsyncer-parser/.../flush/impl/MetaBufferActuator.java:291 │
│                                                                 │
│     a. 创建 WriterRequest(event)                                │
│     b. 创建 WriterResponse，标记 errQueueRetry=true              │
│     c. partition(request, response) -- 单条数据分区聚合           │
│     d. pull(response) -- 直接调用处理逻辑                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  6. pull() -> distributeTableGroup() (与增量共用管道)             │
│                                                                 │
│     a. 构建字段索引映射 buildFieldIndexMap()                      │
│     b. 字段映射: Picker.pickTargetData() -> List<SyncRow>        │
│     c. 参数转换: ConvertUtil.convert()                           │
│     d. 批量写入: connectorFactory.writeBatch()                   │
│     e. 持久化统计: flushStrategy.flushIncrementData()            │
│                                                                 │
│     注意: errQueueRetry=true 时，异常直接抛出而非写入错误队列       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            v
┌─────────────────────────────────────────────────────────────────┐
│  7. 清理与统计更新                                                │
│                                                                 │
│     a. storageService.remove(DATA, metaId, messageId)           │
│        -- 删除存储中的失败记录                                     │
│     b. meta.fail -= 1, meta.success += 1                        │
│     c. tableGroup.fail -= 1, tableGroup.success += 1            │
│     d. tableGroup.status = fail > 0 ? "异常" : "正常"            │
│     e. profileComponent.editConfigModel(meta) -- 持久化           │
└─────────────────────────────────────────────────────────────────┘
```

#### 5.4.3 手动同步载体演进

```
存储(Map row) -> BinlogMap(Protobuf) -> Map<String,Object>(binlogData)
  -> ChangedEvent(RowChangedEvent/DDLChangedEvent)
  -> WriterRequest -> WriterResponse(单条)
  -> Picker -> List<SyncRow> -> ConvertUtil -> writeBatch()
```

#### 5.4.4 与增量同步的关键差异

| 维度 | 增量同步 | 手动同步 |
|------|----------|----------|
| **数据来源** | Listener 实时捕获 | 存储中的失败记录（Lucene） |
| **数据格式** | CDC 原始 `List<Object>` | Protobuf 反序列化后的 `Map` |
| **执行方式** | 异步（BlockingQueue + 定时消费） | 同步（`executeDirectly` 直接调用） |
| **批处理** | 多条聚合为 WriterResponse | 单条直接构造 WriterResponse |
| **失败处理** | 写入错误队列（StorageBufferActuator） | 异常直接抛出（`errQueueRetry=true`） |
| **位点更新** | `afterProcess()` 刷新 binlog 位点 | 不更新位点（重放已有数据） |

#### 5.4.5 批量重试

**位置**: `DataSyncServiceImpl.retryAll(String metaId)`

分页查询所有失败记录（`dataStatus=0`），逐条调用 `sync()` 方法：

```
retryAll(metaId)
  -> 分页查询失败数据 (每页100条)
  -> 逐条调用 sync(params)
  -> 返回 {total, success, fail} 统计
```

---

## 六、关键处理环节

### 6.1 字段映射（Picker）

**位置**: `dbsyncer-parser/.../model/Picker.java`

Picker 负责将源端数据映射到目标端字段：

- **全量场景**（line 49-60）: `pickTargetData(List<Map>, context)` -- 源数据已是 Map 格式
- **增量场景**（line 83-111）: `pickTargetData(sourceFields, fieldIndexMap, rows, beforeRows, ...)` -- CDC 原始数据是 `List<List<Object>>`，需先通过 `RowConverter.toListOfMaps()` 转为 Map

**核心映射逻辑 `exchange()`**（line 191-217）：

1. 遍历源字段列表
2. 从源 Map 中按源字段名取值
3. 可选：通过 `SchemaResolver.merge()` 合并为标准类型
4. 放入目标 Map 中按目标字段名为 key
5. 支持多对一字段合并（字符串拼接）

### 6.2 参数转换（ConvertUtil）

**位置**: `dbsyncer-parser/.../util/ConvertUtil.java`

对 SyncRow 中的 data 应用转换规则：

| 转换器 | 功能 |
|--------|------|
| `AesEncryptHandler` / `AesDecryptHandler` | AES 加解密 |
| `DateHandler` / `TimestampHandler` | 日期转换 |
| `ReplaceHandler` / `AppendHandler` / `SubStrFirstHandler` | 字符串操作 |
| `TemplateHandler` | 模板转换 |
| `UUIDHandler` | UUID 生成 |

共 20+ 种转换器，位于 `dbsyncer-parser/.../convert/handler/` 目录。

### 6.3 缓冲区机制（BufferActuator）

**位置**: `dbsyncer-parser/.../flush/AbstractBufferActuator.java`

核心缓冲机制，实现"生产者-消费者"模式：

| 组件 | 说明 |
|------|------|
| `BlockingQueue<Request>` | `LinkedBlockingQueue`，容量由配置决定 |
| `offer(request)` | 生产者入队 |
| `submit()` | 定时消费：poll -> 分区 -> 聚合 -> 批处理 |
| `process(map)` | 按偏移量排序后逐批 `pull()` |
| `pull(response)` | 子类实现实际处理逻辑 |

**MetaBufferActuator** 是增量同步的专用执行器，每个 Meta 独享一个：

- DDL 事件特殊处理：阻塞等待队列消费完，保证表结构修改原子性
- `pending` 状态跟踪：事件入队设 true，队列空时设 false
- `afterProcess()` 钩子：刷新偏移量 + 检查队列清空 pending

### 6.4 类型转换（SchemaResolver + ValueMapper）

**写入前转换**：`AbstractConnector.convertProcessBeforeWriter()`

- 遍历 SyncRow 的目标字段
- 通过 `SchemaResolver.convert(value, field)` 将 Java 类型转换为目标数据库类型

**ValueMapper 体系**：`dbsyncer-sdk/.../connector/schema/` 目录下有 30+ 种类型映射器：

- `IntValueMapper`, `BigintValueMapper`, `VarcharValueMapper`
- `DateValueMapper`, `TimestampValueMapper`
- `BlobValueMapper`, `ClobValueMapper`
- 等等

---

## 七、数据流全景图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         源端 (Source)                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │   MySQL      │  │  PostgreSQL  │  │   Oracle     │  ...             │
│  │  Connector   │  │  Connector   │  │  Connector   │                  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                  │
│         │                 │                 │                           │
│    Listener(CDC)     Listener(CDC)     Listener(CDC)                   │
│    或 Quartz(定时)    或 Quartz(定时)    或 Quartz(定时)                  │
└─────────┬─────────────────┬─────────────────┬───────────────────────────┘
          │                 │                 │
          v                 v                 v
   ┌──────────────────────────────────────────────┐
   │          ChangedEvent (变更事件)               │
   │  RowChangedEvent / ScanChangedEvent / DDL     │
   │  数据: List<Object> (按列序排列)               │
   │  位点: ChangedOffset (binlog position)         │
   └──────────────────┬───────────────────────────┘
                      │
                      v
   ┌──────────────────────────────────────────────┐
   │         WriterRequest (写入请求)               │
   │  包装 ChangedEvent -> BufferRequest            │
   └──────────────────┬───────────────────────────┘
                      │
                      v
   ┌──────────────────────────────────────────────┐
   │     BufferActuator (缓冲队列 + 批处理)         │
   │  BlockingQueue<WriterRequest>                 │
   │  定时消费 -> 按表名分区 -> 聚合为 WriterResponse │
   └──────────────────┬───────────────────────────┘
                      │
                      v
   ┌──────────────────────────────────────────────┐
   │         MetaBufferActuator.pull()             │
   │  1. 查找 TableGroupPicker                     │
   │  2. 构建字段索引映射                            │
   └──────────────────┬───────────────────────────┘
                      │
    ┌─────────────────┼─────────────────────┐
    │                 │                     │
    v                 v                     v
┌────────┐    ┌────────────┐    ┌──────────────┐
│ Picker │    │ RowConverter│    │  exchange()  │
│pickTarget│   │toListOfMaps│    │ 源字段->目标   │
│  Data()│    │ List->Map  │    │ 字段名映射     │
└────┬───┘    └─────┬──────┘    └──────┬───────┘
     │              │                   │
     v              v                   v
   ┌──────────────────────────────────────────────┐
   │           SyncRow (核心载体)                   │
   │  data: Map<String, Object> (目标字段名->值)    │
   │  context: PluginContext (源/目标连接器)         │
   │  mapping: Mapping 配置引用                      │
   │  tableGroup: TableGroup 配置引用                │
   │  beforeRow: UPDATE 旧值                         │
   └──────────────────┬───────────────────────────┘
                      │
                      v
   ┌──────────────────────────────────────────────┐
   │       ConvertUtil.convert() (参数转换)         │
   │  应用字段转换规则(AES/日期/字符串/模板等)        │
   │  操作 SyncRow.data 中的值                      │
   └──────────────────┬───────────────────────────┘
                      │
                      v
   ┌──────────────────────────────────────────────┐
   │    ConnectorFactory.writeBatch() (写入调度)    │
   │  根据 event 分发:                              │
   │  INSERT -> connector.insert()                 │
   │  UPDATE -> connector.update()                 │
   │  DELETE -> connector.delete()                 │
   │  UPSERT -> connector.upsert()                 │
   │                                               │
   │  写入前: SchemaResolver.convert() 类型转换      │
   └──────────────────┬───────────────────────────┘
                      │
                      v
   ┌──────────────────────────────────────────────┐
   │    AbstractDatabaseConnector.executeWriter()   │
   │  1. 获取目标 SQL (from TableGroup.command)     │
   │  2. 提取 SyncRow.data 为参数列表               │
   │  3. databaseTemplate.batchUpdate(sql, args)   │
   │  4. 带重试机制 (RetryInterceptor)              │
   └──────────────────┬───────────────────────────┘
                      │
                      v
┌─────────────────────────────────────────────────────────────────────────┐
│                         目标端 (Target)                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │   MySQL      │  │Elasticsearch │  │    Kafka     │  │    File    │  │
│  │  Connector   │  │  Connector   │  │  Connector   │  │  Connector │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 八、载体对照表

| 阶段 | 载体 | 数据形态 | 位置 | 说明 |
|------|------|----------|------|------|
| 源端捕获 | `ChangedEvent` | `List<Object>` | `sdk/.../listener/ChangedEvent.java` | 原始变更事件，按列序排列 |
| 入队缓冲 | `WriterRequest` | `List<Object>` + 元数据 | `parser/.../model/WriterRequest.java` | 包装 ChangedEvent，含表名、事件类型、偏移量 |
| 批聚合 | `WriterResponse` | `List<List<Object>>` | `parser/.../model/WriterResponse.java` | 多行按表名分区聚合 |
| 字段映射后 | `SyncRow` | `Map<String, Object>` | `sdk/.../plugin/SyncRow.java` | **核心载体**，目标字段名 -> 值，自带完整上下文 |
| 位点追踪 | `ChangedOffset` | `String + Long` | `sdk/.../model/ChangedOffset.java` | binlog 文件名 + 偏移位置 |

---

## 九、关键设计特点

1. **SyncRow 为中心**：数据载体携带完整上下文（配置 + 连接器 + 转换规则），避免层层传递参数。

2. **缓冲区批处理**：`BlockingQueue` + 定时消费 + 按表分区聚合 + 按位点排序，保证吞吐量和顺序性。

3. **SPI 插件化**：`ConnectorService` 通过 Java SPI 加载，支持动态扩展新连接器。

4. **双流道共用下游**：全量（Stream 流式读取）和增量（事件驱动 + 缓冲队列）共用下游的 Picker / Convert / Write 管道。

5. **DDL 同步原子性**：DDL 事件阻塞队列，保证表结构变更原子性；通过 IR（中间表示）支持异构数据库 DDL 转换。

6. **断点续传**：全量通过 `Cursor` 记录分页游标，增量通过 `ChangedOffset` 记录 binlog 位点，任务重启后可恢复。

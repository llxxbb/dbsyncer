# ADR-0027 设计稿（初始版本）

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | base |
| 作者 | @dbsyncer-team |
| 日期 | 2026-08-24 |
| 关联 ADR | [ADR-0027](../../adr/0027-mysql-cdc-performance-optimization.md) |

## 设计目标

解决 MySQL 增量同步在多表场景（100+ 表）下的性能下降问题，涵盖以下瓶颈：

1. GTID 快照可能超前导致的数据丢失风险
2. 列名缓存懒加载导致的运行时数据库查询开销
3. 表过滤效率低（每个事件都执行字符串转换）
4. 批次处理开销大（不必要的排序和并发容器）
5. DDL 等待机制（忙等待导致 CPU 空转）

## 技术方案

### 1. GTID 跟随事件（核心改进）

**当前问题**：

```
binlog线程:  读取A(GTID-A) → 更新共享GTID → 入队A
             读取B(GTID-B) → 更新共享GTID → 入队B
             读取C(GTID-C) → 更新共享GTID → 入队C
             继续读取D、E、F... → 共享GTID不断更新

消费线程:    处理完A、B、C后 → 从共享GTID读取 → 读到{A,B,C,D,E,F}
             保存快照: GTID={A,B,C,D,E,F}  ← 超前了！
```

**改进设计**：

```
binlog线程:  XID(A) → client.getGtidSet()={A,B,C} → 入队A(携带完整GTID set)
             XID(B) → client.getGtidSet()={A,B,C,D} → 入队B(携带完整GTID set)
             XID(C) → client.getGtidSet()={A,B,C,D,E} → 入队C(携带完整GTID set)

消费线程:    处理完A、B、C后 → 使用事件携带的GTID={A,B,C,D,E}
             保存快照: GTID={A,B,C,D,E}  ← 精确！
```

**核心改动**：

```java
// 1. ChangedOffset 增加 GTID 字段
public class ChangedOffset {
    private String metaId;
    private Object position;        // binlog position
    private String nextFileName;    // binlog filename
    private String gtid;            // 新增：事件携带的完整 GTID set
}

// 2. GTID 来源：XID 时从 client.getGtidSet() 获取完整集合
// 此时 GtidSetManager.commitGtid() 已将当前事务 GTID 提交到集合
private String lastXIDGtidSet = null;

// 处理 XID 事件
if (eventType == EventType.XID) {
    lastXIDPosition = eventHeaderV4.getNextPosition();
    // 捕获当前完整 GTID set（此时 GtidSetManager 已提交当前事务 GTID）
    lastXIDGtidSet = client.getGtidSet();
    
    // 设置到 ChangedOffset
    ChangedOffset offset = new ChangedOffset();
    offset.setNextFileName(lastXIDFileName);
    offset.setPosition(lastXIDPosition);
    offset.setGtid(lastXIDGtidSet);  // 完整的 GTID set，无需合并
}

// 3. 通过事件下传完整 GTID set
RowChangedEvent rowEvent = new RowChangedEvent(..., lastXIDFileName, lastXIDPosition, columnNames);
rowEvent.setGtid(lastXIDGtidSet);  // 设置到 CommonChangedEvent.changedOffset.gtid
trySendEvent(rowEvent);

// 4. 消费线程使用事件携带的完整 GTID set
public void refreshEvent(ChangedOffset offset) {
    snapshot.put(BINLOG_FILENAME, offset.getNextFileName());
    snapshot.put(BINLOG_POSITION, String.valueOf(offset.getPosition()));
    snapshot.put(BINLOG_GTID, offset.getGtid() != null ? offset.getGtid() : "");
    super.refreshEvent(offset);
}
```

**GTID 传递链路**：
- XID 时 `client.getGtidSet()` → `lastXIDGtidSet`（完整 GTID set）
- `RowChangedEvent`/`DDLChangedEvent` 创建时调用 `setGtid(lastXIDGtidSet)`
- `CommonChangedEvent.changedOffset.gtid` 携带完整 GTID set
- 消费线程 `refreshEvent(offset)` → `refreshSnapshot(offset.getGtid())`
- snapshot 直接存储完整 GTID set，无需合并逻辑

### 2. 列名缓存内聚到 MetaInfo

**当前问题**：

```java
// MySQLListener 独立维护列名缓存
private final Map<Long, List<String>> tableColumnNames = new ConcurrentHashMap<>();

// 获取列名
List<String> columnNames = tableColumnNames.get(tableId);

// 问题：
// 1. binlog 切换后，新 tableId 无法命中旧缓存，需要重新查询 INFORMATION_SCHEMA
// 2. DDL 清缓存需要遍历 tables 找 tableId
// 3. 旧 tableId 的条目残留，造成内存泄漏
// 4. 与 TableGroup.sourceTable 中的 MetaInfo.column 信息重复
```

**改进设计**：将 `columnNames` 作为 `MetaInfo` 的派生缓存成员，与 `pkFields` 采用相同的设计模式。

```java
// MetaInfo.java 新增
@JsonIgnore
private transient volatile List<String> columnNames;  // volatile 保证线程安全

/**
 * 获取列名列表（按字段定义顺序）
 * 懒加载缓存，column 变更时自动失效
 */
public List<String> getColumnNames() {
    if (columnNames == null && getColumn() != null) {
        columnNames = getColumn().stream()
                .map(Field::getName)
                .collect(Collectors.toList());
    }
    return columnNames;
}

@Override
public MetaInfo setColumn(List<Field> column) {
    super.setColumn(column);
    this.columnNames = null;  // column 变更，缓存失效
    this.pkFields = null;
    return this;
}
```

**MySQLListener 变化**：

```java
// 删除独立缓存
// private final Map<Long, List<String>> tableColumnNames = new ConcurrentHashMap<>();

// 删除相关方法
// - clearColumnNamesCache()
// - updateColumnNamesCache()
// - getTableColumnNames()

// 新增：通过表名查找 MetaInfo 的 Map（使用 CaseInsensitiveMap，符合 ADR-0008）
import org.apache.commons.collections4.map.CaseInsensitiveMap;
private Map<String, MetaInfo> sourceTableMap = new CaseInsensitiveMap<>();

@Override
public void init() {
    super.init();
    // 构建表名 → MetaInfo 映射（从 sourceTable 获取，无需查库）
    for (Table table : sourceTable) {
        sourceTableMap.put(table.getName(), (MetaInfo) table);  // CaseInsensitiveMap 自动处理大小写
    }
}

// 获取列名时直接查 MetaInfo
private List<String> getColumnNames(long tableId) {
    TableMapEventData tableMap = tables.get(tableId);
    if (tableMap == null) {
        return null;
    }
    MetaInfo metaInfo = sourceTableMap.get(tableMap.getTable());  // 大小写不敏感查找
    return metaInfo != null ? metaInfo.getColumnNames() : null;
}

// DDL 后无需手动清缓存
// MetaInfo.setColumn() 会自动失效 columnNames
```

**优势对比**：

| 场景 | 当前设计（独立缓存） | 改进设计（MetaInfo 成员） |
|------|-------------------|------------------------|
| 数据来源 | 查询 INFORMATION_SCHEMA | 直接从 sourceTable 获取 ✓ |
| binlog 切换 | 缓存无法复用，重新查询 | 无需缓存，直接读取 ✓ |
| DDL 清缓存 | 遍历找 tableId | 自动失效（setColumn 时） ✓ |
| 内存泄漏 | 旧 tableId 条目残留 | 无泄漏 ✓ |
| 代码复杂度 | 4 个方法维护缓存 | 1 个派生属性 ✓ |
| 单一职责 | 列名缓存在 Listener | 列名是 MetaInfo 属性 ✓ |

### 3. 表过滤逻辑优化

**当前问题**：每个事件都执行 `equalsIgnoreCase` 和 `toLowerCase()`。

**改进设计**：使用大小写不敏感的集合，符合 ADR-0008 约定。

```java
// 使用 TreeSet(String.CASE_INSENSITIVE_ORDER)
private Set<String> filterTableLowerCache = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

@Override
public void init() {
    filterTableLowerCache.addAll(filterTable);  // 无需 toLowerCase
}

private boolean isFilterTable(String dbName, String tableName) {
    return StringUtil.equalsIgnoreCase(database, dbName) 
        && filterTableLowerCache.contains(tableName);  // 大小写不敏感比较
}
```

### 4. 批次处理优化

**当前问题**：
1. `submit()` 是单线程执行，但使用 `ConcurrentHashMap` 作为聚合容器，存在不必要的并发开销
2. `process()` 方法对 map 进行全量排序（60+ 行代码），但排序的唯一目的是让"最后一个 = 最大 position"

**改进设计**：
1. 将 `ConcurrentHashMap` 换成 `HashMap`
2. 由上游（`submit()`）直接提供最大 offset，`process()` 无需排序

**原理**：
- queue 是 FIFO，事件按 binlog 顺序入队
- 最后一个 poll 出来的 request，其 offset 一定最大
- 上游记录最大 offset，直接传给 `process()`

```java
// submit() 方法中
Map<String, Response> map = new HashMap<>();  // 替代 ConcurrentHashMap
WriterResponse maxOffsetResponse = null;  // 记录最大 offset

while (!queue.isEmpty()) {
    Request poll = queue.poll();
    String key = getPartitionKey(poll);
    Response response = map.compute(key, ...);
    partition(poll, response);
    
    // 最后一个 response 的 offset 最大（queue 是 FIFO）
    if (response instanceof WriterResponse) {
        maxOffsetResponse = (WriterResponse) response;
    }
    ...
}

process(map, maxOffsetResponse);  // 直接传给 process

// process() 方法中
protected void process(Map<String, Response> map, WriterResponse maxOffsetResponse) {
    // 直接处理，无需排序
    for (Map.Entry<String, Response> entry : map.entrySet()) {
        pull(entry.getValue());
    }
    afterProcess(maxOffsetResponse);  // 直接用上游提供的最大值
}
```

**收益**：
1. 删除 60+ 行排序代码（`AbstractBufferActuator.java:156-221`）
2. `ConcurrentHashMap` 换成 `HashMap`，减少并发开销
3. 逻辑更清晰：上游提供最大值，下游直接使用

**注意**：`pull()` 的执行顺序不影响正确性，因为每个表的 `pull()` 是独立的（不同表写入不同目标表）。DDL 事件会通过 `skipPartition` 打断批次，不会与普通 DML 混在同一批次。

### 5. DDL 等待机制优化

**当前问题**：

```java
// 忙等待，CPU 空转
while (!getQueue().isEmpty()) {
    TimeUnit.MILLISECONDS.sleep(10);
}
```

**改进设计**：

```java
// 信号通知，减少 CPU 开销
ddlLock.lock();
try {
    while (!getQueue().isEmpty()) {
        queueEmptyCondition.await(100, TimeUnit.MILLISECONDS);
    }
} finally {
    ddlLock.unlock();
}

// 队列清空时信号通知
if (getQueue().isEmpty()) {
    ddlLock.lock();
    try {
        queueEmptyCondition.signalAll();
    } finally {
        ddlLock.unlock();
    }
}
```

## 接口/数据

### ChangedOffset 扩展

```java
public class ChangedOffset implements Serializable {
    private String metaId;
    private Object position;        // binlog position (Long)
    private String nextFileName;    // binlog filename
    private String gtid;            // 新增：事件携带的 GTID
    
    // getter/setter
}
```

### 快照存储格式

```json
{
  "binlog_filename": "mysql-bin.000001",
  "binlog_position": "12345",
  "binlog_gtid": "uuid:1-100"  // 精确到已处理位置
}
```

## 风险点

### 1. 向后兼容

**风险**：旧快照可能没有 GTID 字段。

**应对**：降级处理，GTID 为空时使用 filename+position 模式。

```java
String gtid = offset.getGtid();
if (StringUtil.isBlank(gtid)) {
    // 降级：使用 filename+position
    dumpBinaryLogCommand = new DumpBinaryLogCommand(...);
} else {
    // 正常：使用 GTID 模式
    dumpBinaryLogCommand = new DumpBinaryLogGtidCommand(...);
}
```

### 2. GTID 完整性

**风险**：事件携带的 GTID set 需要是完整的，否则断点续传会丢失数据。

**应对**：XID 时 `GtidSetManager.commitGtid()` 已将当前事务 GTID 提交到集合，`client.getGtidSet()` 返回的是完整集合。通过事件下传，消费线程直接使用，无需合并。

```java
// XID 事件时捕获完整 GTID set
lastXIDGtidSet = client.getGtidSet();  // 此时已包含当前事务 GTID
rowEvent.setGtid(lastXIDGtidSet);      // 随事件下传

// 消费线程直接使用
snapshot.put(BINLOG_GTID, offset.getGtid());  // 完整 GTID set
```

### 3. 测试覆盖

**风险**：需要验证断点续传、主备切换等场景。

**应对**：
- 单元测试：GTID 跟随事件传递
- 集成测试：断点续传、主备切换
- 性能测试：100+ 表场景吞吐量对比

## 预期收益

| 优化项 | 预期提升 | 实施难度 |
|-------|---------|---------|
| GTID 跟随事件 | 消除数据丢失风险，简化锁 | 中 |
| 列名缓存内聚到 MetaInfo | 零查库，简化代码 | 低 |
| 表过滤逻辑优化（TreeSet） | 5-10% | 低 |
| 批次处理优化（移除排序） | 10-15% | 低 |
| DDL 信号通知 | 减少 CPU 空转 | 低 |

**综合预期**：100+ 表场景下，吞吐量提升 50-100%，延迟降低 30-50%。

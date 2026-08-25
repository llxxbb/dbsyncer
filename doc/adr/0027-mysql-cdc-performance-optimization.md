# ADR-0027: MySQL 增量同步多表性能优化

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Proposed |
| 决策者 | @dbsyncer-team |
| 参与者 | @dbsyncer-team |
| 创建日期 | 2026-08-24 |
| 最后更新 | 2026-08-24 |
| 关联文档 | [过程文档](../history/0027/) |

## 背景

MySQL 作为源端的增量同步任务，当源端数据表数量较多（100+ 表）时，同步性能明显下降。经过代码分析，发现以下性能瓶颈：

1. **GTID 快照可能超前**：消费线程从共享的 `GtidSetManager` 读取 GTID，但 binlog 读取线程可能已读取更多事件，导致 GTID 超前于实际处理位置。重启后使用 GTID 模式可能跳过未处理事件。

2. **列名缓存懒加载**：当前采用懒加载策略，缓存未命中时才查询 `INFORMATION_SCHEMA.COLUMNS`，表多时运行时产生大量数据库查询。

3. **表过滤效率低**：每个 binlog 事件都需要执行 `equalsIgnoreCase` 和 `toLowerCase()` 字符串操作，高吞吐场景下额外增加 1-2 微秒处理时间。

4. **批次处理开销**：`submit()` 是单线程执行，但使用 `ConcurrentHashMap` 作为聚合容器；`process()` 方法对 map 进行全量排序（60+ 行代码），但排序的唯一目的是让"最后一个 = 最大 position"，可以由上游直接提供。

5. **锁竞争开销**：`GtidSetManager` 使用 synchronized 保护共享状态，高吞吐场景下存在锁等待。

## 决策

我们决定采用以下优化方案：

### 核心改进：GTID 跟随事件

**方案**：GTID 像 binlog filename/position 一样跟随事件传递，而非从共享状态读取。

**原理**：
- 当前设计：binlog 线程更新共享 GTID → 消费线程读取共享 GTID → 可能读到超前值
- 改进设计：binlog 线程将 GTID 设置到事件 → 消费线程使用事件携带的 GTID → GTID 精确

**优势**：
1. GTID 永远不会超前（只可能落后，但"至少一次"语义可接受）
2. 锁可以大幅简化或去掉
3. 设计更一致（GTID 与 position 同为事件属性）

**GTID 来源**：
- 在 XID 事件时，`GtidSetManager.commitGtid()` 已将当前事务 GTID 提交到集合
- 此时 `client.getGtidSet()` 返回的是包含当前事务的完整 GTID set
- 捕获这个值随事件下传，消费线程直接使用，无需合并

```java
// 处理 XID 事件时
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
```

**GTID 传递链路**：
- XID 时 `client.getGtidSet()` → `lastXIDGtidSet`
- `RowChangedEvent`/`DDLChangedEvent` 创建时调用 `setGtid(lastXIDGtidSet)`
- `CommonChangedEvent.changedOffset.gtid` 携带完整 GTID set
- 消费线程 `refreshEvent(offset)` → `refreshSnapshot(offset.getGtid())`
- snapshot 直接存储完整 GTID set，无需合并逻辑

### 辅助优化：列名缓存内聚到 MetaInfo

**当前问题**：`MySQLListener` 独立维护 `tableColumnNames` 缓存（以 `tableId` 为 key），存在以下问题：
- 每个 binlog 文件内 `tableId` 不同，缓存无法跨文件复用
- 需要独立维护缓存失效逻辑（DDL 清缓存、binlog 切换清缓存）
- 缓存与 `TableGroup.sourceTable` 中的 `MetaInfo.column` 信息重复

**改进方案**：将 `columnNames` 作为 `MetaInfo` 的派生缓存成员，与 `pkFields` 采用相同的设计模式。

**优势**：
1. **零查库**：直接从 `sourceTable` 获取，无需查询 `INFORMATION_SCHEMA`
2. **自动失效**：`setColumn()` 时 `columnNames = null`，DDL 后无需手动清缓存
3. **代码更少**：删除 `tableColumnNames`、`clearColumnNamesCache()`、`updateColumnNamesCache()`、`getTableColumnNames()` 四个方法
4. **单一职责**：列名是 `MetaInfo` 的属性，不应由 `MySQLListener` 独立维护

**关键改动**：

```java
// MetaInfo.java 新增
@JsonIgnore
private transient volatile List<String> columnNames;  // volatile 保证线程安全

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
- 删除 `tableColumnNames` 独立缓存
- 通过 `sourceTableMap`（使用 `CaseInsensitiveMap`，符合 ADR-0008）查找 `MetaInfo`，调用 `getColumnNames()` 获取列名
- DDL 后 `MetaInfo.setColumn()` 自动失效 `columnNames`，无需手动清缓存

### 辅助优化：表过滤逻辑优化

**当前问题**：每个事件都执行 `equalsIgnoreCase` 和 `toLowerCase()`。

**改进方案**：使用大小写不敏感的集合，符合 ADR-0008 约定。

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

### 辅助优化：批次处理优化

**当前问题**：
1. `submit()` 是单线程执行，但使用 `ConcurrentHashMap` 作为聚合容器，存在不必要的并发开销
2. `process()` 方法对 map 进行全量排序（60+ 行代码），但排序的唯一目的是让"最后一个 = 最大 position"

**改进方案**：
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
    ...
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
1. 删除 60+ 行排序代码
2. `ConcurrentHashMap` 换成 `HashMap`，减少并发开销
3. 逻辑更清晰：上游提供最大值，下游直接使用

### 辅助优化：DDL 等待机制

将 `sleep(10ms)` 忙等待改为 `Condition.await()` 信号通知，减少 CPU 空转。

## 影响

- **技术影响**：
  - 需要修改 `ChangedOffset` 增加 GTID 字段
  - 需要修改事件处理链路传递 GTID
  - `GtidSetManager` 可简化（去掉部分锁）
  - `MetaInfo` 新增 `columnNames` 派生缓存
  - `MySQLListener` 删除独立列名缓存，改用 `MetaInfo.getColumnNames()`
  - 优化表过滤和批次处理逻辑（移除排序，上游提供最大 offset）
  - 预期吞吐量提升 50-100%，延迟降低 30-50%（100+ 表场景）

- **团队影响**：
  - 需要理解 GTID 事务语义
  - 需要测试断点续传场景

- **时间影响**：
  - 核心改进（GTID 跟随）：2-3 天
  - 辅助优化（缓存、过滤、排序、DDL）：2-3 天
  - 测试验证：1-2 天

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-08-24 | v1.0 | 初始版本 | @dbsyncer-team |
| 2026-08-24 | v1.1 | 根据审核意见修正：批次排序改为批次处理优化（移除排序，上游提供最大 offset）；表过滤使用 TreeSet 符合 ADR-0008；GTID 来源明确为 GTID_LOG_EVENT；MetaInfo.columnNames 加 volatile | @dbsyncer-team |
| 2026-08-26 | v1.2 | GTID 方案简化：XID 时直接从 `client.getGtidSet()` 获取完整 GTID set（此时 `GtidSetManager.commitGtid()` 已提交当前事务），通过事件下传，消费线程直接使用，无需合并逻辑。去掉 `currentTransactionGtid` 字段、GTID 事件处理、`mergeGtid()` 方法 | @dbsyncer-team |

## 过程文档索引

- [设计稿](../history/0027/design-01-base.md)
- [实现说明](../history/0027/impl-01-notes.md)（待创建）
- [测试报告](../history/0027/test-01-report.md)（待创建）

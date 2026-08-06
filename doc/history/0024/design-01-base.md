# ADR-0024 设计稿（初始版本）

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | base |
| 作者 | @lxb |
| 日期 | 2026-08-06 |
| 关联 ADR | [ADR-0024](../../adr/0024-同步管线抽象.md) |

## 设计目标

消除全量/增量同步流程的重复代码，建立统一的管线抽象。

## 技术方案

### 流程骨架

```
Picker.pickTargetData() → enrichRows() → ConvertUtil.convert() → writeBatch()
```

### 类结构

```
pipeline/
├── SyncTask.java      # 值对象：Mapping + TableGroup + Meta
└── SyncPipeline.java  # 管线，提供两个重载 execute 方法
```

### 核心接口

```java
// 同步任务值对象
public class SyncTask {
    private final Mapping mapping;
    private final TableGroup tableGroup;
    private final Meta meta;
}

// 管线
public class SyncPipeline {
    // 全量同步
    public void execute(Picker picker, List<Map> source, SyncTask task,
                        AbstractPluginContext context, boolean isErrQueueRetry);

    // 增量同步
    public void execute(Picker picker, List<Field> sourceFields, Map<String, Integer> fieldIndexMap,
                        boolean enableFilter, List<List<Object>> rows, List<List<Object>> beforeRows,
                        List<String> columnNames, SyncTask task,
                        AbstractPluginContext context, boolean isErrQueueRetry);
}
```

### 调用方修改

**FullSyncExecutorImpl**（cursor 持久化留在调用方）：
```java
new SyncPipeline(connectorFactory, counterService, errorQueueCollector)
    .execute(picker, batch, task, context, false);

// cursor 持久化（全量独有）
Object[] cursors = PrimaryKeyUtil.getLastCursors(batch, primaryKeys);
tableGroup.setCursors(cursors);
if (shouldSave()) { ... }
```

**MetaBufferActuator**（注意 context 设置时序）：
```java
// 先设置 context 属性
context.setEvent(response.getEvent());
context.setEnablePrintTraceInfo(StringUtil.isNotBlank(response.getTraceId()));

// 执行管线
new SyncPipeline(connectorFactory, counterService, errorQueueCollector)
    .execute(picker.setSourceResolver(schemaResolver),
             actualSourceFields, fieldIndexMap, enableFilter,
             response.getDataList(), response.getBeforeRows(),
             response.getColumnNames(), task, context, response.isErrQueueRetry());
```

## 风险点

- `context.setEvent()` 必须在 `execute()` 之前调用，否则 `writeBatch` 无法正确分发事件类型

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
DataPicker.pick() → enrichRows() → ConvertUtil.convert() → writeBatch()
```

### 类结构

```
pipeline/
├── DataPicker.java    # @FunctionalInterface，抹平 pick 签名差异
├── SyncTask.java      # 值对象：Mapping + TableGroup + Meta
└── SyncPipeline.java  # 管线，无扩展点
```

### 核心接口

```java
// 数据抽取接口
@FunctionalInterface
public interface DataPicker {
    List<SyncRow> pick() throws Exception;
}

// 同步任务值对象
public class SyncTask {
    private final Mapping mapping;
    private final TableGroup tableGroup;
    private final Meta meta;
}

// 管线
public class SyncPipeline {
    private final ConnectorFactory connectorFactory;
    private final CounterService counterService;
    private final ErrorQueueCollector errorQueueCollector;

    public void execute(DataPicker picker, SyncTask task,
                        List<String> columnNames,
                        AbstractPluginContext context,
                        boolean isErrQueueRetry) throws Exception {
        List<SyncRow> target = picker.pick();
        enrichRows(target, task, columnNames);
        ConvertUtil.convert(task.getTableGroup().getConvert(), target);
        connectorFactory.writeBatch(target, task.getMeta(), task.getMapping(),
                task.getTableGroup(), counterService, errorQueueCollector, isErrQueueRetry);
    }

    private void enrichRows(List<SyncRow> rows, SyncTask task, List<String> columnNames) {
        for (SyncRow row : rows) {
            row.setMapping(task.getMapping());
            row.setTableGroup(task.getTableGroup());
            if (columnNames != null) row.setColumnNames(columnNames);
        }
    }
}
```

### 调用方修改

**FullSyncExecutorImpl**（cursor 持久化留在调用方）：
```java
// 执行管线
new SyncPipeline(connectorFactory, counterService, errorQueueCollector)
    .execute(() -> picker.pickTargetData(batch, context), syncTask, null, context, false);

// cursor 持久化（全量独有）
Object[] cursors = PrimaryKeyUtil.getLastCursors(sourceData, primaryKeys);
tableGroup.setCursors(cursors);
if (shouldSave()) {
    profileComponent.editConfigModel(tableGroup);
    profileComponent.editConfigModel(meta);
}
```

**MetaBufferActuator**（注意 context 设置时序）：
```java
// 先设置 context 属性
context.setEvent(response.getEvent());
context.setEnablePrintTraceInfo(StringUtil.isNotBlank(response.getTraceId()));

// 执行管线
new SyncPipeline(connectorFactory, counterService, errorQueueCollector)
    .execute(() -> tableGroupPicker.getPicker()
            .setSourceResolver(enableSchemaResolver ? sourceConnector.getSchemaResolver() : null)
            .pickTargetData(actualSourceFields, fieldIndexMap, enableFilter,
                    response.getDataList(), response.getBeforeRows(), context),
            syncTask, response.getColumnNames(), context, response.isErrQueueRetry());
```

## 风险点

- `context.setEvent()` 必须在 `execute()` 之前调用，否则 `writeBatch` 无法正确分发事件类型

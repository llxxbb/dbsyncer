# 同步管线抽象 — Template Method 重构计划

## 目标

将 `FullSyncExecutorImpl.processTableGroupDataBatch` 和 `MetaBufferActuator.distributeTableGroup` 的共性逻辑抽象为统一的管线（Pipeline），全量和增量只需按需定制差异点。

## 主线流程（不变骨架）

```
pickTargetData → enrichRows → ConvertUtil.convert → writeBatch → postProcess
```

## 差异分析

| 步骤 | 全量 (FullSync) | 增量 (IncrementalSync) |
|------|----------------|----------------------|
| pick | `picker.pickTargetData(List<Map>, ctx)` | `picker.pickTargetData(fields, idxMap, filter, rows, beforeRows, ctx)` |
| enrichRows | `setMapping` + `setTableGroup` | 额外 `setColumnNames` |
| postProcess | cursor 更新 + 定时保存 | 空实现 |

## 新增文件（3个）

### 1. `dbsyncer-parser/.../parser/pipeline/AbstractSyncPipeline.java`

```java
package org.dbsyncer.parser.pipeline;

import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.plugin.AbstractPluginContext;
import org.dbsyncer.sdk.plugin.SyncRow;

import java.util.List;

public abstract class AbstractSyncPipeline {

    protected final ConnectorFactory connectorFactory;
    protected final CounterService counterService;
    protected final ErrorQueueCollector errorQueueCollector;

    protected AbstractSyncPipeline(ConnectorFactory connectorFactory,
                                   CounterService counterService,
                                   ErrorQueueCollector errorQueueCollector) {
        this.connectorFactory = connectorFactory;
        this.counterService = counterService;
        this.errorQueueCollector = errorQueueCollector;
    }

    @FunctionalInterface
    public interface DataPickStrategy {
        List<SyncRow> pick() throws Exception;
    }

    public final void execute(DataPickStrategy picker,
                              AbstractPluginContext context,
                              Mapping mapping,
                              TableGroup tableGroup,
                              Meta meta,
                              boolean isErrQueueRetry) throws Exception {
        List<SyncRow> target = picker.pick();
        enrichRows(target, mapping, tableGroup);
        ConvertUtil.convert(tableGroup.getConvert(), target);
        connectorFactory.writeBatch(target, meta, mapping, tableGroup,
                counterService, errorQueueCollector, isErrQueueRetry);
        postProcess(target, context, mapping, tableGroup, meta);
    }

    protected void enrichRows(List<SyncRow> rows, Mapping mapping, TableGroup tableGroup) {
        for (SyncRow row : rows) {
            row.setMapping(mapping);
            row.setTableGroup(tableGroup);
        }
    }

    protected abstract void postProcess(List<SyncRow> target,
                                        AbstractPluginContext context,
                                        Mapping mapping,
                                        TableGroup tableGroup,
                                        Meta meta) throws Exception;
}
```

### 2. `dbsyncer-parser/.../parser/pipeline/FullSyncPipeline.java`

```java
package org.dbsyncer.parser.pipeline;

import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.plugin.AbstractPluginContext;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class FullSyncPipeline extends AbstractSyncPipeline {

    private static final Logger logger = LoggerFactory.getLogger(FullSyncPipeline.class);
    private static final long SAVE_INTERVAL_MS = 3000L;

    private final ProfileComponent profileComponent;
    private final List<Map> sourceData;
    private final List<String> primaryKeys;
    private final long[] lastSaveTime;

    public FullSyncPipeline(ConnectorFactory connectorFactory,
                            CounterService counterService,
                            ErrorQueueCollector errorQueueCollector,
                            ProfileComponent profileComponent,
                            List<Map> sourceData,
                            List<String> primaryKeys,
                            long[] lastSaveTime) {
        super(connectorFactory, counterService, errorQueueCollector);
        this.profileComponent = profileComponent;
        this.sourceData = sourceData;
        this.primaryKeys = primaryKeys;
        this.lastSaveTime = lastSaveTime;
    }

    @Override
    protected void postProcess(List<SyncRow> target,
                               AbstractPluginContext context,
                               Mapping mapping,
                               TableGroup tableGroup,
                               Meta meta) throws Exception {
        Object[] newCursors = PrimaryKeyUtil.getLastCursors(sourceData, primaryKeys);
        tableGroup.setCursors(newCursors);
        saveTableGroupIfNeeded(meta, tableGroup);
    }

    private void saveTableGroupIfNeeded(Meta meta, TableGroup tableGroup) throws Exception {
        long currentTime = System.currentTimeMillis();
        long lastSave = lastSaveTime[0];
        if (currentTime - lastSave >= SAVE_INTERVAL_MS) {
            profileComponent.editConfigModel(tableGroup);
            profileComponent.editConfigModel(meta);
            logger.info("保存表 {} 全量同步", tableGroup.getName());
            lastSaveTime[0] = currentTime;
        }
    }
}
```

### 3. `dbsyncer-parser/.../parser/pipeline/IncrementalSyncPipeline.java`

```java
package org.dbsyncer.parser.pipeline;

import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.plugin.AbstractPluginContext;
import org.dbsyncer.sdk.plugin.SyncRow;

import java.util.List;

public class IncrementalSyncPipeline extends AbstractSyncPipeline {

    private final List<String> columnNames;

    public IncrementalSyncPipeline(ConnectorFactory connectorFactory,
                                   CounterService counterService,
                                   ErrorQueueCollector errorQueueCollector,
                                   List<String> columnNames) {
        super(connectorFactory, counterService, errorQueueCollector);
        this.columnNames = columnNames;
    }

    @Override
    protected void enrichRows(List<SyncRow> rows, Mapping mapping, TableGroup tableGroup) {
        for (SyncRow row : rows) {
            row.setMapping(mapping);
            row.setTableGroup(tableGroup);
            row.setColumnNames(columnNames);
        }
    }

    @Override
    protected void postProcess(List<SyncRow> target,
                               AbstractPluginContext context,
                               Mapping mapping,
                               TableGroup tableGroup,
                               Meta meta) {
    }
}
```

## 修改文件（2个）

### 4. 修改 `FullSyncExecutorImpl.java`

**删除** `processTableGroupDataBatch` 方法和 `saveTableGroupIfNeeded` 方法（第172-215行）。

**修改** `executeTableGroupWithStreaming` 中的调用（第148-157行），将：

```java
processTableGroupDataBatch(metaId, batch, tableGroup, group, mapping, context, picker, primaryKeys, lastSaveTime);
```

替换为：

```java
new FullSyncPipeline(connectorFactory, counterService, errorQueueCollector,
        profileComponent, batch, primaryKeys, lastSaveTime)
    .execute(() -> picker.pickTargetData(batch, context),
             context, mapping, group, meta, false);
```

**删除** 不再需要的 import：
- `org.dbsyncer.parser.util.ConvertUtil`
- `org.dbsyncer.sdk.util.PrimaryKeyUtil`

**新增** import：
- `org.dbsyncer.parser.pipeline.FullSyncPipeline`

### 5. 修改 `MetaBufferActuator.java`

**简化** `distributeTableGroup` 方法（第319-372行），将 pick → enrich → convert → writeBatch 逻辑替换为管线调用：

```java
private void distributeTableGroup(WriterResponse response, Meta meta, Mapping mapping,
        TableGroupPicker tableGroupPicker, List<Field> sourceFields, boolean enableFilter) {
    if (response.getColumnNames() == null || response.getColumnNames().isEmpty()) {
        return;
    }

    TableGroup tableGroup = tableGroupPicker.getTableGroup();
    Map<String, Integer> fieldIndexMap = buildFieldIndexMap(response.getColumnNames(), sourceFields);

    if (fieldIndexMap.isEmpty()) {
        String message = String.format("字段定位错误！表名: %s, 列名: %s，未找到任何匹配字段",
                response.getTableName(), response.getColumnNames());
        handleFailData(response, meta, mapping, tableGroup, sourceFields, fieldIndexMap, message, false);
        return;
    }

    List<Field> actualSourceFields = sourceFields.stream()
            .filter(f -> fieldIndexMap.containsKey(f.nameIgnoreCase()))
            .collect(Collectors.toList());

    boolean enableSchemaResolver = profileComponent.getSystemConfig().isEnableSchemaResolver();
    ConnectorConfig sourceConfig = getConnectorConfig(mapping.getSourceConnectorId());
    ConnectorService sourceConnector = connectorFactory.getConnectorService(sourceConfig.getConnectorType());

    AbstractPluginContext context;
    try {
        context = new AbstractPluginContext();
        context.setSourceConnectorInstance(connectorFactory.connect(sourceConfig));
        context.setTargetConnectorInstance(
                connectorFactory.connect(getConnectorConfig(mapping.getTargetConnectorId())));
    } catch (Exception e) {
        String message = String.format("异常！表名: %s, 列名: %s，msg %s",
                response.getTableName(), response.getColumnNames(), e.getMessage());
        handleFailData(response, meta, mapping, tableGroup, sourceFields, fieldIndexMap, message, true);
        return;
    }

    try {
        new IncrementalSyncPipeline(connectorFactory, counterService, errorQueueCollector,
                response.getColumnNames())
            .execute(() -> tableGroupPicker.getPicker()
                    .setSourceResolver(enableSchemaResolver ? sourceConnector.getSchemaResolver() : null)
                    .pickTargetData(actualSourceFields, fieldIndexMap, enableFilter,
                            response.getDataList(), response.getBeforeRows(), context),
                    context, mapping, tableGroup, meta, response.isErrQueueRetry());
        context.setEvent(response.getEvent());
        context.setEnablePrintTraceInfo(StringUtil.isNotBlank(response.getTraceId()));
    } catch (Exception e) {
        String message = String.format("异常！表名: %s, 列名: %s，msg %s",
                response.getTableName(), response.getColumnNames(), e.getMessage());
        handleFailData(response, meta, mapping, tableGroup, sourceFields, fieldIndexMap, message, true);
    }
}
```

**删除** 不再需要的 import：
- `org.dbsyncer.parser.util.ConvertUtil`

**新增** import：
- `org.dbsyncer.parser.pipeline.IncrementalSyncPipeline`

## 设计要点

1. **Template Method + 函数式接口**：`DataPickStrategy` 抹平两种 `pickTargetData` 签名差异
2. **两个定制点**：`enrichRows`（行数据填充）和 `postProcess`（写后处理）
3. **管线无状态**：依赖通过构造器注入，批次数据通过 `execute` 参数传入
4. **错误处理保持原位**：`distributeTableGroup` 的 try-catch + `handleFailData` 保留在调用方（增量特有逻辑）
5. **向后兼容**：不改变任何公开接口，`FullSyncExecutor` 和 `MetaBufferActuator` 的外部行为不变

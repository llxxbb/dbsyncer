# 错误队列数据一致性修复 - 设计文档

## 1. 架构概述

### 1.1 当前架构问题

```
┌─────────────────────────────────────────────────────────────────┐
│                        当前错误处理流程                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  MetaBufferActuator ──catch──> Result ──> FlushStrategyImpl    │
│        (增量)                        │                          │
│                                      ├─> updateMetaCounts()   │
│                                      ├─> updateTableGroup()   │
│                                      ├─> asyncWrite()         │
│                                      └─> notifyOnError()      │
│                                                                 │
│  FullSyncExecutorImpl ──> Result ──> FlushStrategyImpl         │
│        (全量)                        │                          │
│                                      ├─> refreshTotal()       │
│                                      └─> flush()              │
│                                      (无通知!)                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**问题**：
- 全量/增量路径不一致
- Result 传导链长，易遗漏
- 无熔断机制
- 计数可能漂移
- 错误数据格式不一致（增量用源格式，Connector 层用目标格式）
- 全量同步错误数据丢失（异常直接抛出）
- 部分行失败静默丢弃

### 1.2 目标架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        目标错误处理流程                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  MetaBufferActuator ──catch──┐                                  │
│        (增量)                │                                  │
│                              ▼                                  │
│  FullSyncExecutorImpl ─────> ErrorQueueService                 │
│        (全量)                │  ┌─ persistError()              │
│                              │  ├─ updateCounts()             │
│                              │  ├─ notifyIfError()            │
│                              │  └─ checkCircuitBreaker()      │
│                              │                                 │
│                              └──> 熔断: Meta置ERROR + 发布      │
│                                   CircuitBreakEvent + 通知      │
│                                        │                       │
│                                        ▼                       │
│                              biz 层监听 CircuitBreakEvent       │
│                              └─> managerFactory.close(mapping) │
│                                                                 │
│  ConnectorFactory.writeBatch ──> 统一重试（全部连接器）           │
│        └─> 重试失败抛异常 ──> 上游 catch ──> ErrorQueueService  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**关键边界**：
- `Result` 保留为 `ConnectorService` 接口契约，仅承载"源格式 failData + 整批重试"
- 被去除的是 `Result → FlushStrategyImpl` 错误处理链
- `FlushStrategyImpl` 只走成功路径（成功数据存储 + `CounterService.incrementSuccess`）

## 2. 核心组件设计

### 2.1 ErrorQueueService

**职责**：统一错误队列的写入、计数、通知、熔断检查（DML/DDL 双入口）。

**位置**：`dbsyncer-parser/.../parser/error/ErrorQueueService.java`

```
ErrorQueueService {
    // DML 入口：基于 SyncRow.sourceData
    handleFailData(metaId, tableGroupId, targetTableName, event, failData, errorMsg) {
        persistError(failData)     // 持久化到存储
        updateCounts(failCount)    // CounterService 原子计数
        notifyError()              // 发送通知
        checkCircuitBreaker()      // 检查熔断
    }

    // DDL 入口：接收 DDLConfig JSON，存储格式与重试分支不变
    handleDdlFailData(metaId, tableGroupId, targetTableName, ddlConfigJson, errorMsg) {
        persistError([{BINLOG_DATA: ddlConfigJson}])
        updateCounts(1)
        notifyError()
        checkCircuitBreaker()
    }

    onRetrySuccess(metaId, tableGroupId) {
        counterService.decrementFail(meta, tableGroup)  // 原子
        counterService.flush(meta, tableGroup)
    }

    alignCount(metaId) {
        for (tg in 该meta下所有TableGroup) {
            actualCount = storage.query(tgId AND success=0).getTotal()
            tg.fail = actualCount
        }
        meta.fail = Σ tg.fail   // total 不对齐
        记录漂移日志
    }

    checkCircuitBreaker(metaId) {
        if (threshold > 0 && meta.fail >= threshold) {
            meta.saveState(ERROR, "熔断原因")
            profileComponent.editConfigModel(meta)
            publish(CircuitBreakEvent(mappingId))   // Spring 事件
            messageService.send(..., DATA_ERROR_CIRCUIT_BREAKER)
        }
    }
}
```

### 2.2 熔断停止机制

```
ErrorQueueService 熔断触发
    ├─> Meta 置 ERROR（errorMessage 注明熔断原因）+ 持久化
    ├─> 发布 Spring 事件 CircuitBreakEvent(mappingId)
    │     └─> biz 层 CircuitBreakerEventListener
    │           └─> managerFactory.close(mapping)   // 复用 stop 流程
    │                 ├─> listener.close()
    │                 ├─> bufferActuatorRouter.unbind(metaId)
    │                 └─> tableGroupContext.clear(metaId)
    └─> 发送熔断通知 DATA_ERROR_CIRCUIT_BREAKER

全量同步：无需主动中断
    └─> FullSyncExecutorImpl 每批次检查 meta.isRunning()，状态变更后自然退出

恢复：手动重启任务（start() 重新拉取 listener / 重建执行线程）
```

**模块依赖**：parser 模块不依赖 manager，通过 Spring 事件解耦；监听器置于 biz 层（可访问 `managerFactory`）。

### 2.3 ErrorQueueConfig

**位置**：`dbsyncer-parser/.../parser/error/ErrorQueueConfig.java`

```
配置项：dbsyncer.error.threshold = 0  // 0 表示不启用熔断
```

**配置迁移**：`application.properties` 移除无实现引用的 `dbsyncer.error.fence.enable` / `dbsyncer.error.fence.max-size`。

### 2.4 CounterService

**职责**：统一管理计数更新，聚合与细分字段一次维护，原子累加。

**位置**：`dbsyncer-parser/.../parser/service/CounterService.java`

```
CounterService {
    // 增加成功数（签名不区分全量/增量，内部读 meta.getSyncPhase() 判定）
    incrementSuccess(Meta meta, TableGroup tg, int count) {
        synchronized(meta) {
            meta.success += count
            if (meta.getSyncPhase() == FULL) {
                meta.fullSuccess += count; tg.fullSuccess += count
            } else {
                meta.incrementSuccess += count; tg.incrementSuccess += count
            }
            tg.success += count
            syncStatus(tg)
        }
    }

    // 增加失败数（同上）
    incrementFail(Meta meta, TableGroup tg, int count) {
        synchronized(meta) {
            meta.fail += count
            if (FULL) { meta.fullFail += count; tg.fullFail += count }
            else      { meta.incrementFail += count; tg.incrementFail += count }
            tg.fail += count
            syncStatus(tg)
        }
    }

    // 减少失败数（重试成功；细分字段无法归属，不更新）
    decrementFail(Meta meta, TableGroup tg) {
        synchronized(meta) {
            meta.fail = max(0, meta.fail - 1)
            meta.success += 1
            tg.fail = max(0, tg.fail - 1)
            tg.success += 1
            syncStatus(tg)
        }
    }

    syncStatus(tg) { tg.status = tg.fail > 0 ? "异常" : "正常" }

    // 刷新：将修改应用到 DB（全量快照点 / 增量偏移量刷新 / 重试成功后）
    flush(Meta meta, TableGroup tg) {
        meta.updateTime = now()
        profileComponent.editConfigModel(meta)
        profileComponent.editConfigModel(tg)
    }
}
```

**原子性**：按 meta 加锁，不改 Meta 字段类型（volatile long），避免破坏 JSON 序列化与存储兼容。

### 2.5 SyncRow 扩展

**职责**：携带源格式原始数据，确保错误队列存储的数据可重试。

**位置**：`dbsyncer-sdk/.../plugin/SyncRow.java`

```
SyncRow {
    data: Map<目标字段名, 值>        // 现有
    sourceData: Map<源字段名, 原始值>  // 新增
    // ... 其他字段不变
}
```

**Picker 改造**：合并两个 `pickTargetData` 方法，提取核心逻辑。

```
// 核心方法：处理 Map 列表（统一入口）
pickTargetDataFromMaps(sourceMaps, beforeRows, enableFilter, context) {
    for (sourceMap in sourceMaps) {
        target = exchange(sourceMap)
        if (enableFilter && !filter(target)) continue
        syncRow = new SyncRow(target, context)
        syncRow.sourceData = sourceMap       // 直接引用，不深拷贝
        if (beforeRows != null) syncRow.beforeRow = beforeRows[i]
        targetList.add(syncRow)
    }
}

// 全量方法：简化为适配调用
pickTargetData(List<Map> source, context) {
    return pickTargetDataFromMaps(source, null, false, context)
}

// 增量方法：先转换再调用（移除 sourceMapList 输出参数）
pickTargetData(sourceOriginalFields, fieldIndexMap, enableFilter, rows, beforeRows, context) {
    sourceMaps = RowConverter.toListOfMaps(sourceOriginalFields, fieldIndexMap, rows)
    return pickTargetDataFromMaps(sourceMaps, beforeRows, enableFilter, context)
}
```

**数据格式对照**：

| 路径 | sourceData 内容 | 说明 |
|------|-----------------|------|
| 全量 | `Map<源字段名, JDBC原始值>` | 来自源表查询结果 |
| 增量 | `Map<源字段名, CDC原始值>` | 来自 `RowConverter.toListOfMaps()` |

**内存开销说明**：sourceData 直接引用 Picker 已构造的源 Map（增量路径 `toListOfMaps` 新建、全量路径 JDBC 行 Map 独立），正常路径无额外拷贝开销；仅在错误时序列化存储。

**重试流程（DML）**：

```
错误队列 (BINLOG_DATA protobuf)
    ↓ 反序列化
Map<源字段名, 原始值>
    ↓ 按 sourceFields 顺序重建
RowChangedEvent(columnNames, changedRow)
    ↓ executeDirectly()
重新走完整管道：Picker → ConvertUtil → writeBatch
```

**重试流程（DDL）**：走 `DataSyncServiceImpl.sync` 的 `OPERTION_ALTER` 分支（反序列化 DDLConfig → `DDLChangedEvent` → `executeDirectly`），存储格式不变。

## 3. 调用方改造

### 3.1 MetaBufferActuator 改造

**改造前**：
```
catch (Exception e) {
    result = new Result()
    result.addFailData(sourceDataList)
}
flushStrategy.flushIncrementData(result)
```

**改造后**：
```
catch (Exception e) {
    failData = RowConverter.toListOfMaps(sourceFields, fieldIndexMap, response.getDataList())  // picker 失败时重建
    errorQueueService.handleFailData(metaId, tableGroupId, ..., failData, errorMsg)
    return  // 不再调用 flushStrategy
}
// writeBatch 失败时：failData = targetDataList.stream().map(SyncRow::getSourceData)
```

### 3.2 FullSyncExecutorImpl 改造

**改造前**：
```
result = writeBatch(target)
flushStrategy.flushFullData(result)   // 异常直接上抛，错误数据丢失
```

**改造后**：
```
try {
    result = writeBatch(target)
} catch (Exception e) {
    failData = target.stream().map(SyncRow::getSourceData)
    errorQueueService.handleFailData(..., failData, e.getMessage())
    return  // 不向上抛出，继续下一批
}

if (result.failData 非空) {
    errorQueueService.handleFailData(..., result.failData, result.error)
}
// 成功数据走 flushFullData（只走成功路径）
```

### 3.3 DataSyncServiceImpl 改造

**改造前**：
```
// 重试成功后
Meta.fail--；Meta.success++
TableGroup.fail--；TableGroup.success++
```

**改造后**：
```
// 重试成功后（DML 与 DDL 分支共用）
errorQueueService.onRetrySuccess(metaId, tableGroupId)
```

### 3.4 FlushStrategyImpl 改造

**改造前**：全量/增量各自更新计数、异步写成功+失败数据、通知。

**改造后**：只走成功路径。

```
// flushFullData / flushIncrementData
flush(metaId, result, event) {
    counterService.incrementSuccess(meta, tg, result.getSuccessData().size())
    counterService.incrementFail(meta, tg, result.getFailData().size())   // 若仍残留失败数据
    if (enableStorageWriteSuccess) asyncWrite(success=true, result.getSuccessData())
}
// 移除：notifyOnError / 失败数据 asyncWrite / updateTableGroupProgress 错误分支
// 失败数据写入统一由 ErrorQueueService.persistError 负责
```

### 3.5 ConnectorFactory 改造

**职责**：统一错误处理和自动重试，提取 `SyncRow.getSourceData()` 作为 failData。

**改造后**：
```
// ConnectorFactory 统一处理
writeBatch(List<SyncRow> syncRows) {
    try {
        // 统一自动重试（读 RetryConfig.getGlobal()，默认 disable=true 保持现状）
        return retryInterceptor.execute(() -> {
            return operation.execute(connector, instance, syncRows)
        }, policy, mappingId)
    } catch (Exception e) {
        // 重试失败后，统一提取源数据
        result.failData = syncRows.stream().map(SyncRow::getSourceData)
        result.error = e.getMessage()
        return result
    }
}

// 非数据库连接器简化：整体异常（含 ES bulk 部分 failed item）直接抛
writer() {
    doWrite()  // 不 catch，直接抛
}
```

**注意**：`convertProcessBeforeWriter` 失败路径的 failData 同样改用 `SyncRow.getSourceData()`。

### 3.6 AbstractDatabaseConnector 改造

**职责**：部分行失败时加入 `failData`，使用 `getSourceData()`；移除内部 retryInterceptor。

**改造前**：
```
for (i in execute[]) {
    if (execute[i] == 1) {
        successData.add(syncRows[i].getData())
    }
    // 失败行被静默丢弃！
}
```

**改造后**：
```
for (i in execute[]) {
    if (execute[i] == 1 || execute[i] == -2) {   // -2 = SUCCESS_NO_INFO，维持成功语义
        successData.add(syncRows[i].getData())
    } else {
        failData.add(syncRows[i].getSourceData())  // 新增
    }
}
```

### 3.7 非数据库连接器简化

**职责**：移除 try-catch 与 `addFailData`，整体异常（含 ES bulk 部分 item 失败）直接抛，由 `ConnectorFactory` 统一重试。

```
// ES：bulk 有 failed item 或整体异常 → 抛异常
writer() {
    if (bulk 有 failed item) throw ElasticsearchException(...)
    // 不再逐个 addFailData
}

// File / Kafka：移除 catch，直接抛
writer() {
    doWrite()  // 不 catch
}
```

### 3.8 Picker 方法合并

**职责**：合并两个 `pickTargetData` 方法，消除重复代码，统一设置 `sourceData`，移除 `sourceMapList` 输出参数。

**改造后**：
```
// 核心方法：处理 Map 列表（统一入口）
private pickTargetDataFromMaps(sourceMaps, beforeRows, enableFilter, context) {
    for (sourceMap in sourceMaps) {
        target = exchange(sourceMap)
        if (enableFilter && !filter(target)) continue
        syncRow = new SyncRow(target, context)
        syncRow.sourceData = sourceMap          // 直接引用
        if (beforeRows != null) syncRow.beforeRow = beforeRows[i]
        targetList.add(syncRow)
    }
}

// 全量方法：简化为适配调用
pickTargetData(List<Map> source, context) {
    return pickTargetDataFromMaps(source, null, false, context)
}

// 增量方法：先转换再调用
pickTargetData(sourceOriginalFields, fieldIndexMap, enableFilter, rows, beforeRows, context) {
    sourceMaps = RowConverter.toListOfMaps(sourceOriginalFields, fieldIndexMap, rows)
    return pickTargetDataFromMaps(sourceMaps, beforeRows, enableFilter, context)
}
```

### 3.9 DDL 错误独立处理

**位置**：`MetaBufferActuator.parseDDl` 改造。

```
// writerDDL 失败时
errorQueueService.handleDdlFailData(metaId, tableGroupId, targetTableName,
        ddlConfigJson /* result.failData 中的 BINLOG_DATA */, result.error)
```

存储格式 `{BINLOG_DATA: DDLConfig JSON}` 不变，重试分支（`DataSyncServiceImpl` 的 `OPERTION_ALTER`）不变。

## 4. 配置项

**文件**：`application.properties`

```properties
# 错误队列熔断阈值（0 表示不启用）
dbsyncer.error.threshold=0
```

**移除**（无实现引用的死配置）：

```properties
dbsyncer.error.fence.enable=true
dbsyncer.error.fence.max-size=10000
```

## 5. API 接口

### 5.1 计数对齐接口

**文件**：`MonitorController.java`

```
POST /monitor/alignCount?metaId=xxx
    → errorQueueService.alignCount(metaId)
    → 返回成功
```

## 6. UI 实现

### 6.1 按钮位置

**文件**：`edit.html` 错误队列 Tab 面板

```
改造前：[清空数据]

改造后：[计数对齐] [清空数据]
         ↑ title="以错误队列实际失败数为准，修正任务面板显示的失败计数"
```

### 6.2 JS 交互

**文件**：`edit.js`

```
bindAlignCountEvent() {
    $("#alignCountBtn").click(function() {
        doPoster('/monitor/alignCount', {metaId: metaId}, function(result) {
            if (result.success) {
                bootGrowl("计数对齐完成", "success")
                location.reload()  // 刷新页面更新失败数显示
            }
        })
    })
}
```

## 7. 通知类型

**文件**：`NotifyType.java`

```
新增：DATA_ERROR_CIRCUIT_BREAKER("错误队列熔断")
同时更新 getName() 增加对应 case，避免回退到默认 name()
```

## 8. 熔断事件

**文件**：`dbsyncer-parser/.../parser/error/CircuitBreakEvent.java`、`dbsyncer-biz/.../biz/impl/CircuitBreakerEventListener.java`

```
// 事件载体
CircuitBreakEvent extends ApplicationEvent {
    String mappingId
}

// 监听器（biz 层，可访问 managerFactory）
@EventListener
public void onCircuitBreak(CircuitBreakEvent event) {
    Mapping mapping = profileComponent.getMapping(event.mappingId)
    if (mapping != null && meta.isRunning()) {
        managerFactory.close(mapping)   // 复用现有关闭流程
    }
}
```

## 9. 迁移策略

### 9.1 阶段一：基础设施（无破坏性）

1. 创建 `ErrorQueueService`、`ErrorQueueConfig`、`CounterService`、`CircuitBreakEvent`、`CircuitBreakerEventListener`
2. `application.properties`：移除 `dbsyncer.error.fence.*`，添加 `dbsyncer.error.threshold`
3. 添加计数对齐 API `/monitor/alignCount` 与"计数对齐"按钮
4. 扩展 `SyncRow`，新增 `sourceData` 字段
5. 合并 `Picker` 两个 `pickTargetData` 方法，提取 `pickTargetDataFromMaps()`，统一设置 `sourceData`，移除 `sourceMapList` 输出参数
6. `NotifyType` 新增 `DATA_ERROR_CIRCUIT_BREAKER` 及名称分支

### 9.2 阶段二：统一错误处理和重试

1. 改造 `ConnectorFactory.writeBatch()`，统一自动重试机制
2. 改造 `AbstractDatabaseConnector.executeWriter()`，移除内部 retryInterceptor，部分行失败加入 `failData`
3. 非数据库连接器（ES/File/Kafka）简化：移除 try-catch，整体异常直接抛

### 9.3 阶段三：逐步迁移调用方

1. 先迁移 `MetaBufferActuator`（增量场景）：catch 直接调用 ErrorQueueService（含 DDL 分支）
2. 再迁移 `FullSyncExecutorImpl`（全量场景）：捕获异常，调用 ErrorQueueService
3. 最后迁移 `DataSyncServiceImpl`（重试场景）：调用 `onRetrySuccess()`

### 9.4 阶段四：迁移计数更新

1. 改造 `FlushStrategyImpl`：只走成功路径，计数委托 `CounterService`
2. 改造 `DataSyncServiceImpl`：使用 `CounterService.decrementFail()` 替代直接更新
3. 在关键节点调用 `counterService.flush()`

### 9.5 阶段五：清理旧代码

1. 移除 `FlushStrategyImpl` 中的错误处理逻辑（notifyOnError / 失败数据 asyncWrite）
2. 更新单元测试

## 10. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 迁移过程中错误处理中断 | 分阶段迁移，每阶段独立验证 |
| 熔断误触发 | 默认阈值为 0（不启用），需手动开启；全量脏数据多时可先计数对齐再重启 |
| 熔断事件时序 | 置 ERROR 与发布事件分离；监听器校验 `meta.isRunning()` 防止重复关闭 |
| 计数对齐查询性能 | 复用现有 query 接口，按表组循环 count，手动触发可接受 |
| 计数修正影响业务 | 修正前记录日志，支持回滚 |
| SyncRow 内存开销 | sourceData 直接引用源 Map，正常路径无拷贝开销 |
| 全量同步异常捕获后继续执行 | 记录日志，错误数据入队，靠熔断兜底 |
| 非数据库重试副作用 | 依赖幂等写入（ES _id / Kafka 消息语义），重试后失败数据统一入错误队列 |

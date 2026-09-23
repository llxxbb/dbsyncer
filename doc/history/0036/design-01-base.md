# ADR-0036 设计稿：增量终止语义与快照点一致性修复

> 决策与理由见 [ADR-0036](../../adr/0036-增量终止语义与快照点一致性修复.md)，本文只记录实现细节。

## 1. 缺陷调用链（修复前）

```
[actuator 出队线程 taskScheduler-3]
MetaBufferActuator.dequeue()
 └─ executeBatch(tableGroups, maxOffsetResponse)
     ├─ executeSync(row1) → doSync → writeDML → SyncPipeline
     │    └─ ErrorQueueCollectorImpl.handleFailData()
     │        ├─ persistError()              → 第1条进错误队列（可重试恢复）
     │        ├─ counterService.incrementFail()
     │        └─ checkCircuitBreaker() → closeTask()
     │             ├─ meta.saveState(ERROR)            ← ①先置 ERROR
     │             └─ publishEvent(CircuitBreakEvent)  ← ②再发事件（同步 @EventListener）
     │                  └─ CircuitBreakerEventListener.onCircuitBreak()
     │                       if (!meta.isRunning()) return;   ✗ 恒成立 → close 永不执行
     │                       // managerFactory.close(mapping) 从未被调用
     │                       //   → listener.close() / meta.setListener(null) / unbind() 全部跳过
     └─ commitOffset(maxOffsetResponse)   ← 无条件提交
          └─ listener.refreshEvent(offset)（listener 仍非 null）
               └─ pendingSnapshot = snapshot → 3s 后 flushEvent → Meta.snapshot 落库

[下一个调度 tick（300ms 周期，积压立即补发）]
dequeue() → 第2、3条 → doSync: !isRunning → WARN 跳过（未写、未入错误队列）
          → commitOffset → 快照点越过第2、3条      ✗ 永久丢失

[重启]
IncrementPuller.start(): meta.getListener() != null → 复用旧实例（跳过 tableGroupContext.put）
 └─ MySQLListener.start(): client.isConnected() → ERROR 日志 + return   ✗ 空操作
      → 旧 client 从实时位置续读，第2、3条永不重放
```

## 2. 修复后调用链

```
closeTask() → CircuitBreakEvent
 └─ onCircuitBreak(): closing.add(mappingId) 成功
      └─ meta.getListener() != null → managerFactory.close(mapping)
           ├─ FullIncrementPuller.close()
           │    ├─ fullPuller.close() → resetMetaState(): isError() → 保留 ERROR（决策6）
           │    └─ incrementPuller.close()
           │         ├─ listener.close()
           │         │    ├─ stopSnapshotFlush()  ← 首行：closed=true → 补写一次 → 停 executor（决策5）
           │         │    └─ client.disconnect()
           │         ├─ meta.setListener(null)
           │         ├─ bufferActuatorRouter.unbind() → actuator.stop() → 丢弃残留 + WARN（决策3）
           │         └─ tableGroupContext.clear()
           └─ editConfigModel(meta)  ← 持久化 ERROR + 未越过的快照点

[重启] IncrementPuller.start(): 残留先释放 → 新建 listener.setSnapshot(meta.getSnapshot())
        → 从快照点重连 → 第2、3条重放 → upsert 幂等落库
```

## 3. 关键实现

### 3.1 决策 1：CircuitBreakerEventListener

```java
private final Set<String> closing = ConcurrentHashMap.newKeySet();

onCircuitBreak(event):
    mappingId = event.getMappingId()
    if (!closing.add(mappingId)) { INFO 关闭进行中; return; }   // CAS 幂等
    // 异步：避免出队线程被 disconnect/快照补写阻塞，也避免 unbind→stop→cancel(true) 自我中断
    CompletableFuture.runAsync(() -> {
        try { closeIfResourceBound(mappingId); }
        catch (e) { ERROR 熔断事件处理失败; }
        finally { closing.remove(mappingId); }
    })

closeIfResourceBound(mappingId):
    mapping = getMapping(mappingId);      if (null) { WARN; return; }
    meta    = getMeta(mapping.metaId);    if (null) { WARN; return; }
    if (meta.getListener() == null) { INFO 增量资源已释放, 无需关闭; return; }   // 判据=资源绑定
    INFO 正在关闭任务; managerFactory.close(mapping);
```

### 3.2 决策 2：MetaBufferActuator 条件提交

```java
dequeue():
    while (!queue.isEmpty()) {
        if (!isRunning()) { WARN 任务已停止,停止出队(残留=queue.size()); return; }  // 快照点不动
        ... 原分组逻辑 ...
    }
    if (!tableGroups.isEmpty()) executeBatch(...);

executeBatch(tableGroups, maxOffsetResponse):
    if (!processAll(tableGroups)) { WARN 批次未落库,跳过快照提交; return; }
    commitOffset(maxOffsetResponse);

processAll(tableGroups) -> boolean:                 // 不短路：所有表组都执行
    allProcessed = true
    for (entry : tableGroups) allProcessed &= executeSync(entry.getValue())
    return allProcessed

executeSync(response) -> boolean:  ... return doSync(response, meta, mapping);

doSync(response, meta, mapping) -> boolean:
    if (!isRunning(meta.state)) { WARN 任务已停止,跳过批次处理; return false; }   // ← 唯一 false 出口
    ... DDL / SCAN / ROW 分发（写入失败仍由 handleFailData 吞掉 → 返回 true，见 ADR-0023 决策10）
    return true
```

`executeDirectly()`（错误队列重试入口）忽略返回值，行为不变。

### 3.3 决策 3：stop() 丢弃残留

```java
stop():
    scheduledTaskService.stop(taskKey)
    hasPendingTask = false
    discardQueuedEvents()

discardQueuedEvents():
    if (queue == null || queue.isEmpty()) return
    discarded = queue.size(); queue.clear()
    WARN "[MetaBufferActuator] 任务停止,丢弃队列残留事件: metaId={}, 条数={}"
```

### 3.4 决策 5：AbstractListener 冻结快照

```java
private volatile boolean closed = false;

protected final void stopSnapshotFlush():        // 幂等，子类 close() 首行调用
    if (closed) return
    closed = true                                // 先置位：阻断后续 refreshEvent
    flushPendingOnce()                           // 同步补写，兑现 destroy() 既有注释
    pendingSnapshot = null
    shutdownFlushExecutor()                      // 原 destroy() 逻辑

refreshEvent(offset):
    if (closed) { WARN 监听器已关闭,忽略快照推进; return; }
    pendingSnapshot = new HashMap<>(snapshot)

flush 定时任务体: if (closed) return; ... 原逻辑 ...
close():   stopSnapshotFlush(); log("结束增量同步")
destroy(): stopSnapshotFlush()                   // @PreDestroy 保留，兼容 Spring Bean 场景
```

子类改造（首行插入 `stopSnapshotFlush()`，其余不动）：
`MySQLListener`、`SqlServerListener`、`SqlServerCTListener`、`PostgreSQLListener`、`OracleListener`、`FileListener`、`AbstractQuartzListener`。

兜底：`ParserConsumer.flushEvent()` 开头 `if (meta.getListener() == null) { WARN 丢弃快照写入; return; }`。

### 3.5 决策 4：真重启

```java
// IncrementPuller.start() worker 线程
Listener stale = meta.getListener();
if (stale != null) {                                  // 残留：先释放
    WARN 检测到残留监听器,先关闭再重建
    try { stale.close(); } catch (e) { ERROR }
    meta.setListener(null);
    bufferActuatorRouter.unbind(metaId);
}
meta.saveState(RUNNING, "");  editConfigModel(meta);
tableGroupContext.put(mapping, list);                 // 不再被复用分支跳过
listener = getListener(...);                          // setSnapshot(meta.getSnapshot())
meta.setListener(listener);
listener.start();

// MySQLListener.start()
if (client != null && client.isConnected()) {
    WARN 已在运行,先断开再按快照点重连
    client.disconnect();
}
run();
```

### 3.6 决策 6：resetMetaState 保留异常

```java
resetMetaState(mapping):
    meta = getMeta(mapping)
    if (meta == null || meta.isError()) return        // errorMessage 非空 → 保留状态与原因
    meta.resetState(); editConfigModel(meta)
```

## 4. 验证要点

| 场景 | 期望 |
|------|------|
| 复现原缺陷（下线目标库 → 插 3 条 → 熔断 → 上线 → 重启） | 日志出现"正在关闭任务"+"结束增量同步"；第 2、3 条重放入库；第 1 条在错误队列可重试 |
| 熔断后 Meta 状态 | `异常(3)` + errorMessage 保留（不被抹成 `未运行(0)`） |
| 熔断后资源 | binlog 连接断开、`snapshot-flush-*` 线程退出、actuator 从路由移除 |
| 并发熔断（多条数据同时失败） | 只关闭一次，无重复 close 日志 |
| 正常手动停止 | 状态 `未运行`，快照点补写一次后冻结 |
| 停止时队列有残留 | WARN 打印丢弃条数，重启后从快照点重放 |
| 重启后监听器 | 新建实例（日志无 "already started"），从 `meta.getSnapshot()` 续读 |

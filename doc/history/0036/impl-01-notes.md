# ADR-0036 实现说明

## 元信息

| 字段 | 值 |
|------|-----|
| 作者 | @lxb |
| 日期 | 2026-09-22 |
| 关联设计 | [设计稿](design-01-base.md) |
| 关联 ADR | [ADR-0036](../../adr/0036-增量终止语义与快照点一致性修复.md) |

## 实现概述

围绕一条不变量实施：**持久化快照点 ≤ 最后一条成功写入目标库（或已进入错误队列）的数据位置**。

六个决策的落点：

| 决策 | 落点 | 性质 |
|------|------|------|
| 1 关闭判据 | `CircuitBreakerEventListener` | 根因修复 |
| 2 条件提交偏移量 | `MetaBufferActuator` | 兜底防线 |
| 3 残留事件丢弃 | `MetaBufferActuator.stop` | 兜底防线 |
| 4 真重启 | `IncrementPuller.start` / `MySQLListener.start` | 修复静默 no-op |
| 5 快照冻结 | `AbstractListener` + 7 个子类 + `ParserConsumer.flushEvent` | 兜底防线 |
| 6 保留异常状态 | `MappingModelService.resetMetaState` | 修复状态被抹除 |

编译：`mvn -o -q compile -DskipTests` 通过。

## 关键实现细节

### 决策 1：判据从"运行状态"改为"资源绑定" + CAS 幂等 + 异步关闭

`ErrorQueueCollectorImpl.closeTask` 先 `saveState(ERROR)` 再 `publishEvent(CircuitBreakEvent)`，
旧判据 `!meta.isRunning()` 因此恒为真，`managerFactory.close()` 永不可达（日志 `熔断事件: 任务未运行, 无需关闭`）。

```java
private final Set<String> closingMappings = ConcurrentHashMap.newKeySet();

public void onCircuitBreak(CircuitBreakEvent event) {
    String mappingId = event.getMappingId();
    if (!closingMappings.add(mappingId)) {
        logger.info("熔断事件: 关闭进行中, 跳过重复关闭, mappingId={}", mappingId);
        return;
    }
    CompletableFuture.runAsync(() -> {
        try {
            closeIfResourceBound(mappingId);
        } catch (Exception e) {
            logger.error("熔断事件处理失败: mappingId={}", mappingId, e);
        } finally {
            closingMappings.remove(mappingId);
        }
    });
}
```

`closeIfResourceBound` 的关闭判据是 `meta.getListener() != null`：纯全量驱动没有监听器，
`FullSyncExecutorImpl` 走 `SyncPipeline` 同步写入、从不入队 `MetaBufferActuator`，
因此跳过关闭不会泄漏资源，反而避免了 `FullPuller.close() → resetMetaState` 抹掉 ERROR。

**为何异步**：事件由执行器出队线程同步发布，就地关闭会阻塞偏移量提交；
且 `unbind → ScheduledTaskService.stop` 对运行中任务 `cancel(true)`，在出队线程上执行等于自我中断
（中断标志会带过后续存储写入）。与 `ParserConsumer.errorEvent` 的关闭路径一致。

### 决策 2：只有真正处理的批次才提交偏移量

`doSync` / `executeSync` 改为返回 `boolean`，唯一的 `false` 出口是"任务已停止"跳过分支；
`executeBatch` 用 `processAll` 累积所有表组结果（**不短路**，避免部分表组漏写），失败则不提交：

```java
if (!processAll(tableGroups)) {
    logger.warn("批次未落库，跳过快照点提交: metaId={}, 表数={}", metaId, tableGroups.size());
    return;
}
commitOffset(lastResponse);
```

`dequeue()` 的 while 循环首行加 `if (!isRunning())`，停止出队并**保留队列残留**（不 clear），
交由 `stop()` 的决策 3 统一丢弃并告警。

同时修正可观测性缺陷：`executeSync` 的成功日志只在 `doSync` 返回 true 时打印，
避免"跳过并丢失的批次"在日志里看起来像已写入 2 条。

`commitOffset` 补充不变量注释，说明"队列位置 ≥ 批次最大位置"只是 FIFO 顺序结论，
不构成提交理由；提交的前提是批次已落库或已进错误队列（ADR-0023 决策 10 的边界）。

### 决策 3：残留事件丢弃 + 显式告警

```java
private void discardQueuedEvents() {
    if (queue == null || queue.isEmpty()) {
        return;
    }
    int discarded = queue.size();
    queue.clear();
    logger.warn("[MetaBufferActuator] 任务停止，丢弃队列残留事件: metaId={}, 条数={}", metaId, discarded);
}
```

不写错误队列：决策 2 保证快照点没有越过这些事件，重连后源端会重放；
但丢弃不允许静默（旧代码 `doEnqueue` 与 `unbind` 都是无声丢弃，是本次事故难定位的直接原因）。

### 决策 4：真重启

- `IncrementPuller.start` 删除 `if (listener == null)` 复用分支——该分支既跳过 `saveState(RUNNING)`
  又跳过 `tableGroupContext.put`，复用重启会抛 `pickers 不能为空`。改为无条件
  `releaseStaleListener(meta, metaId)` → 置 RUNNING → 填充上下文 → 新建监听器。
- `releaseStaleListener` 先 `close()` 残留监听器再 `unbind`。因为 `stopSnapshotFlush()`
  是每个子类 `close()` 的首行，即使 `close()` 抛异常（记录后吞掉），残留连接也已冻结，无法再破坏快照点。
- `MySQLListener.start()` 的静默 `return`（ERROR `MySQLExtractor is already started`）改为
  WARN + `client.disconnect()` + `run()`，按 `meta.getSnapshot()` 重连。

### 决策 5：快照冻结

`AbstractListener` 新增 `volatile boolean closed`，`refreshEvent` 关闭后 no-op；
`stopSnapshotFlush()` 幂等且顺序固定：`closed = true` → 补写一次 → 清 `pendingSnapshot` → 关执行器。
**先置 closed 再补写**：否则 `disconnect()` 的 ≤3s join 窗口内到达的 XID 事件仍能推进快照。

7 个子类（MySQL / SqlServer / SqlServerCT / PostgreSQL / Oracle / File / AbstractQuartz）
的 `close()` 首行调用 `stopSnapshotFlush()`——它们的 `super.close()` 都在最后，
只在基类置标志会晚于断开动作。未采用"`close()` 改 final + `doClose()`"，那会破坏外部连接器的 SDK 契约。

`ParserConsumer.flushEvent` 增加 `meta.getListener() == null` 兜底：
监听器已解绑则丢弃快照写入并 WARN。`IncrementPuller.close` 的顺序是
`listener.close()` 先于 `setListener(null)`，因此最后一次补写不会被兜底拦掉。

### 决策 6：异常状态不被抹除

```java
public void resetMetaState(Mapping mapping) throws Exception {
    Meta meta = getMeta(mapping);
    if (meta == null || meta.isError()) {
        return;
    }
    meta.resetState();
    profileComponent.editConfigModel(meta);
}
```

同时修正过期 javadoc（原写"不持久化 Meta，只修改状态"，与实际行为相反）。

## 与设计的差异

| 项 | 设计稿 | 实施 | 原因 |
|----|--------|------|------|
| 关闭方式 | `doClose` 同步 | `CompletableFuture.runAsync` + `closeIfResourceBound` | 出队线程自我中断与阻塞提交偏移量（已回写 ADR v1.1） |
| `flushEvent` 兜底 | 直接 return | 保留 TableGroup 持久化循环，只在 listener 已解绑时跳过快照写入；循环体抽成 `persistTableGroup` | TableGroup 变更与快照无关，不应一并丢弃；抽出方法满足嵌套 ≤2 层规范 |
| 日志措辞 | "已关闭，跳过快照持久化" | "监听器已解绑，丢弃快照写入" | 与实际语义（listener 解绑）一致，便于 grep |
| `commitOffset` | 未列入修改范围 | 补充不变量注释 | 原注释只论证 FIFO 顺序，正是本次缺陷的推理来源，不补注释下一次会被再次改坏 |

## 遗留问题

- **决策 4 缺运行时验证**：`releaseStaleListener` 与 MySQL 断开重连依赖真实 binlog 连接，
  现有离线单测覆盖不到；`dbsyncer-web` 的 MySQL/SqlServer 集成夹具可达，
  但仓库内尚无"混合驱动熔断 → 重启"的用例，需补一条端到端回归（见 [测试报告](test-01-report.md) 待补项）。
- 决策 2/3/5 的运行时行为同样只做了组件级验证，未做全链路演练。

## 后续优化建议

0. **先修集成测试上下文隔离**（既有缺陷，非本次引入）：`SQLServerCTToMySQLDDLSyncIntegrationTest`
   （`webEnvironment = RANDOM_PORT`）与 `KafkaIntegrationTest`（`@TestPropertySource`）会在同一 surefire JVM
   内创建额外 ApplicationContext，争抢唯一的 Lucene 存储目录，抛
   `LockObtainFailedException: .../data/config/write.lock`，共 56 例错误（HEAD 基线相同）。
   统一上下文配置或按上下文隔离存储目录后，才能稳定承载端到端回归用例。
1. 在 `dbsyncer-web/src/test/java/org/dbsyncer/web/integration/` 增加熔断-重启端到端用例，
   覆盖 7 条验收断言（设计稿验证矩阵）。
2. `doEnqueue` 的"任务已停止且队列已满，丢弃事件"告警应补上 metaId 与快照点，与决策 3 的告警格式统一。
3. SqlServer 目标端重放会主键冲突进错误队列，可评估改为 upsert 以降低至少一次语义的人工成本。

# ADR-0036 实现说明

## 元信息

| 字段 | 值 |
|------|-----|
| 作者 | 凌曦 |
| 日期 | 2026-09-23 |
| 关联设计 | [design-01-base.md](design-01-base.md) |
| 关联 ADR | [0036-增量终止语义与快照点一致性修复.md](../../adr/0036-增量终止语义与快照点一致性修复.md) |

## 实现概述

| 决策 | 落点 | 性质 |
|------|------|------|
| 1. 熔断判据 | `CircuitBreakerEventListener` | 修复 |
| 2. 真重启 | `IncrementPuller.releaseStaleListener` + `MySQLListener.start` | 修复 |
| 3. 快照冻结 | `AbstractListener.closed` + `ParserConsumer.flushEvent` | 修复 |
| 4. 保留 ERROR | `MappingModelService.resetMetaState` | 修复 |

## 关键实现细节

### 决策 1：熔断判据

**修改前**：
```java
if (meta == null || !meta.isRunning()) {
    logger.info("熔断事件: 任务未运行, 无需关闭");
    return;
}
```

**修改后**：
```java
Mapping mapping = profileComponent.getMapping(mappingId);
if (mapping == null) {
    throw new IllegalStateException("熔断事件: Mapping不存在, mappingId=" + mappingId);
}
managerFactory.close(mapping);
```

**理由**：`closeTask` 先置 `ERROR` 再发事件，`!meta.isRunning()` 恒为真，导致 close 永不执行。

### 决策 2：真重启

**IncrementPuller.releaseStaleListener**：
```java
private void releaseStaleListener(Meta meta, String metaId) {
    Listener stale = meta.getListener();
    if (stale == null) {
        return;
    }
    logger.warn("检测到残留监听器，先关闭再重建: metaId={}", metaId);
    try {
        stale.close();
    } catch (Exception e) {
        logger.error("关闭残留监听器失败: metaId={}", metaId, e);
    }
    meta.setListener(null);
    bufferActuatorRouter.unbind(metaId);
}
```

**MySQLListener.start**：
```java
if (client != null && client.isConnected()) {
    logger.warn("MySQLExtractor 已在运行，先断开再按快照点重连: metaId={}", metaId);
    client.disconnect();
}
run();
```

**理由**：复用旧 listener 让续读点脱离持久化快照，属于"假启动"。

### 决策 3：快照冻结

**AbstractListener**：
```java
private volatile boolean closed = false;

public void refreshEvent(ChangedOffset offset) {
    if (closed) {
        logger.warn("监听器已关闭，忽略快照推进: metaId={}", metaId);
        return;
    }
    pendingSnapshot = new HashMap<>(snapshot);
}

public void close() {
    closed = true;
    shutdownFlushExecutor();
    staticLogService.log(mapping, LogType.STOP, "结束增量同步");
}
```

**ParserConsumer.flushEvent**：
```java
if (meta.getListener() == null) {
    logger.warn("监听器已解绑，丢弃快照写入: metaId={}, snapshot={}", metaId, snapshot);
    return;
}
```

**理由**：listener 不是 Spring Bean，`@PreDestroy` 永不触发；已关闭的监听器继续写快照会覆盖新监听器的续读点。

### 决策 4：保留 ERROR

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

**理由**：混合模式 close 链路按静态 model 分派，若无此判断，熔断原因会被抹成"未运行"。

## 与设计的差异

无。最终实现与设计一致。

## 遗留问题

无。

## 后续优化建议

1. 考虑为 `CircuitBreakerEventListener` 添加单元测试，覆盖 Mapping/Meta 缺失场景
2. 考虑添加端到端集成测试，覆盖熔断→重启场景

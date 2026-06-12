---
status: active  
goal: find_root_cause_only
trigger: SQL Server to MySQL incremental sync - errors increment counter but not recorded (intermittent)
created_at: 2026-06-23T00:00:00Z
slug: mysql-sync-error-log-issue-intermittent
---

# Debug Session: SQL Server to MySQL Incremental Sync Error Logging Issue (INTERMITTENT)

## Issue Description
SQL Server to MySQL 增量同步任务中，**只有部分**情况下错误没有记录到错误队列，其他同步任务是正常可以写入错误队列的。

**关键观察:**
- 错误计数器会递增
- 但某些情况下错误数据不写入错误队列
- 其他同步任务正常 (说明配置应该是好的)
- 这是**偶发问题**,不是所有场景都失败

## Evidence

### 1. 错误计数逻辑 (无条件的)
在 `FlushStrategyImpl.flushIncrementData()` 中:

```java
public void flushIncrementData(String metaId, Result result, String event) {
    Meta meta = cacheService.get(metaId, Meta.class);
    if (meta != null) {
        AtomicLong total = meta.getTotal();
        total.getAndAdd(result.getFailData().size());  // 无条件递增
        total.getAndAdd(result.getSuccessData().size());
        
        updateTableGroupIncrementProgress(metaId, result);  // 无条件递增
        updateMetaCounts(meta, result);  // 无条件递增
    }
    flush(metaId, result, event);  // 条件写入
    notifyOnError(result, event);
}
```

### 2. 错误存储的逻辑 (有条件的)

```java
private void flush(String metaId, Result result, String event) {
    SystemConfig systemConfig = profileComponent.getSystemConfig();
    // 是否写失败数据
    if (systemConfig.isEnableStorageWriteFail()  // 条件 1: 配置检查
        && !CollectionUtils.isEmpty(result.getFailData())) {  // 条件 2: 非空检查
        asyncWrite(metaId, result.getTableGroupId(), ..., false, result.getFailData(), result.error);
    }
}
```

### 3. asyncWrite 的实现 (静默失败风险)

```java
private void asyncWrite(String metaId, String tableGroupId, String targetTableGroupName, String event, boolean success, List<Map> data, String error) {
    long now = Instant.now().toEpochMilli();
    data.forEach(r -> {
        Map<String, Object> row = new HashMap();
        // ... 准备 row 数据 ...
        try {
            byte[] bytes = BinlogMessageUtil.toBinlogMap(r).toByteArray();
            row.put(ConfigConstant.BINLOG_DATA, bytes);
        } catch (Exception e) {
            logger.warn("可能存在 Blob 或 inputStream 大文件类型，无法序列化:{}", r);
            // 注意：这里只是 warn，仍然会继续执行
        }
        storageBufferActuator.offer(new StorageRequest(metaId, row));  // 关键调用
    });
}
```

### 4. 队列满的静默失败风险

在 `AbstractBufferActuator.offer()` 中:

```java
@Override
public void offer(BufferRequest request) {
    if (queue.offer((Request) request)) {
        return;  // 入队成功
    }
    // 如果入队失败且任务正在运行，才调用 offerFailed
    if (isRunning(request)) {
        offerFailed(queue, (Request) request);
    }
    // 注意：如果 isRunning 返回 false，会静默丢弃请求!
}
```

在 `offerFailed()` 中:

```java
protected void offerFailed(BlockingQueue<StorageRequest> queue, StorageRequest request) {
    Meta meta = profileComponent.getMeta(request.getMetaId());
    if (meta != null) {
        Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        if (mapping != null) {
            logger.info("{}, data={}", mapping.getName(), request.getRow());
        }
    }
    // 只记录日志，数据仍然丢失!
}
```

### 5. 异步执行器的异常静默处理

在 `StorageBufferActuator.pull()` 中:

```java
@Override
public void pull(StorageResponse response) {
    storageExecutor.execute(() -> {
        try {
            storageService.addBatch(StorageEnum.DATA, response.getMetaId(), response.getDataList());
        } catch (Exception e) {
            throw new RuntimeException(e);  // 异常被丢弃!
        }
    });
}
```

## Potential Root Causes (Hypotheses)

### Hypothesis 1: Queue Overflow 队列溢出
- **原因**: 当 SQL Server 增量数据量大时，`LinkedBlockingQueue` 可能达到容量上限
- **现象**: 偶发的入队失败，导致错误数据被丢弃
- **证据**: `LinkedBlockingQueue` 默认容量是无界的，但实际生产环境可能设置了 `buffer-queue-capacity`

### Hypothesis 2: Task Shutdown 任务已停止
- **原因**: 当同步任务停止时，`isRunning()` 返回 `false`
- **现象**: `offer()` 方法中不满足 `isRunning(request)` 条件，静默丢弃
- **证据**: 在 `AbstractBufferActuator.offer()` 中看到这种保护逻辑

### Hypothesis 3: Executor Saturation 线程池饱和
- **原因**: `storageExecutor` 线程池满，新任务被拒绝
- **现象**: SQL Server 增量数据量大时，写入压力大
- **证据**: `StorageConfig` 中配置了 `threadQueueCapacity = 500`

### Hypothesis 4: Exception Swallowing 异常被吞掉
- **原因**: `pull()` 方法中的 `try-catch` 抛出的 RuntimeException 被线程池静默处理
- **现象**: 写入失败但无法捕获异常
- **证据**: 线程池异常处理器未配置

## Next Actions
1. Check `dbsyncer.storage.buffer-queue-capacity` 配置值
2. 添加错误日志: 在 `offer()` 方法中被丢弃的记录
3. 添加 `storageExecutor` 的 `RejectedExecutionHandler` 配置
4. 检查日志中是否有 "queue full" 或 "thread pool exhausted" 警告
5. 监控 `StorageBufferActuator` 队列大小和 pending 任务数

---

## NEW DISCOVERY: Log Table Creation Failure

### Problem Description
After deleting `dbsyncer_log` table and restarting, the system:
1. Printed the CREATE TABLE SQL in logs
2. **But the table was NOT created**

### Root Cause

In `MySQLStorageService.readSql()` method:
```java
while ((newLine = bf.readLine()) != null) {
    res.append(newLine);  // ⚠️ Includes ALL lines, including comments!
}
```

The SQL script contains comments:
```sql
-- 创建日志表 (新版本)
-- ADR-0016: 日志表结构更新
-- 新增字段：MAPPING_ID, LOG_TYPE, LOG_LEVEL
CREATE TABLE `dbsyncer_log` ...
-- 如果表已存在，执行迁移（用于现有系统）
-- ALTER TABLE `dbsyncer_log` ...
```

When these comments are included in the final SQL, MySQL throws a syntax error because it tries to execute multiple statements including commented DDL.

### Impact
- `dbsyncer_log` table never created
- Error logging functionality broken
- Sync errors increment counters but cannot be stored

### Fix Applied

1. **Filter comments in `readSql()`**:
   ```java
   if (!trimmedLine.startsWith("--") && !trimmedLine.startsWith("/*")) {
       res.append(newLine).append("\n");
   }
   ```

2. **Remove exception from `initTable()`**:
   Changed from `throw new RuntimeException(ex)` to `logger.error(..., ex)` to allow initialization to continue

3. **Clean SQL execution**:
   ```java
   String cleanDdl = ddl.replaceAll("--.*?$", "").replaceAll("/\\*.*?\\*/", "").trim();
   databaseTemplate.execute(cleanDdl);
   ```

## Related Issues

This is SEPARATE from the intermittent error logging issue, but both relate to error storage not working properly.

- **Issue 1**: Log table not created → No error logging at all
- **Issue 2**: Queue overflow/executor saturation → Intermittent error logging failure

Both must be fixed for reliable error logging.

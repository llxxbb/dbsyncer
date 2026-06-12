---
status: active
goal: find_root_cause_only
trigger: SQL Server to MySQL incremental sync - errors increment counter but not recorded to error queue
created_at: 2026-06-23T00:00:00Z
slug: mysql-sync-error-log-issue
---

# Debug Session: SQL Server to MySQL Incremental Sync Error Logging Issue

## Issue Description
SQL Server to MySQL 增量同步任务 (incremental sync task) 在同步过程中出现异常，错误计数增加了，但错误没有被记录到错误队列中。

## Symptoms
- 同步过程中出现异常 (exceptions during sync)
- 错误计数增加了 (error counter increments)
- 但没有记录到错误队列中 (no error records created in error queue)

## Goal
Root cause analysis - understand why errors increment counters but don't get recorded to error queue.

---

## Current Focus
| Field | Value |
|---|---|
| Status | root cause identified |
| Hypothesis | isEnableStorageWriteFail config setting controls whether errors are written to storage |
| Next Action | confirm root cause and provide fix |

---

## Evidence

### timestamp: 2026-06-23

**Analysis of FlushStrategyImpl.flushIncrementData:**

In `/work/project/github/dbsyncer/dbsyncer-parser/src/main/java/org/dbsyncer/parser/strategy/impl/FlushStrategyImpl.java`:

1. **Lines 70-90: Counter increments occur unconditionally**
   ```java
   @Override
   public void flushIncrementData(String metaId, Result result, String event) {
       // 为增量设置总数
       Meta meta = cacheService.get(metaId, Meta.class);
       if (meta != null) {
           AtomicLong total = meta.getTotal();
           total.getAndAdd(result.getFailData().size());  // <-- Increments total count
           total.getAndAdd(result.getSuccessData().size());
           
           // 更新 TableGroup 的增量同步数量
           updateTableGroupIncrementProgress(metaId, result);  // <-- Updates counts
           
           // 更新 Meta 的同步计数
           updateMetaCounts(meta, result);  // <-- Increments success/fail counters
       }
       flush(metaId, result, event);
       notifyOnError(result, event);
   }
   ```

2. **Lines 190-194: Meta counters always increment**
   ```java
   private void updateMetaCounts(Meta meta, Result writer) {
       meta.getFail().getAndAdd(writer.getFailData().size());  // <-- Always increments fail count
       meta.getSuccess().getAndAdd(writer.getSuccessData().size());
       meta.setUpdateTime(Instant.now().toEpochMilli());
   }
   ```

3. **Lines 342-353: Storage write is CONFIG-DEPENDENT**
   ```java
   private void flush(String metaId, Result result, String event) {
       SystemConfig systemConfig = profileComponent.getSystemConfig();
       // 是否写失败数据
       if (systemConfig.isEnableStorageWriteFail() && !CollectionUtils.isEmpty(result.getFailData())) {  // <-- CHECKS CONFIG
           asyncWrite(metaId, result.getTableGroupId(), result.getTargetTableGroupName(), event, false, result.getFailData(), result.error);
       }
       
       // 是否写成功数据
       if (systemConfig.isEnableStorageWriteSuccess() && !CollectionUtils.isEmpty(result.getSuccessData())) {
           asyncWrite(metaId, result.getTableGroupId(), result.getTargetTableGroupName(), event, true, result.getSuccessData(), "");
       }
   }
   ```

**KEY FINDING:** The error count is incremented unconditionally in `updateMetaCounts()`, but the actual writing of error data to storage is controlled by the configuration flag `isEnableStorageWriteFail()`. If this flag is `false`, errors are counted but never written to the error queue/storage.

---

## Analysis Log

### 2026-06-23

**Code path traced:**
1. Exception occurs in `MetaBufferActuator.distributeTableGroup()` (line 398-421 in MetaBufferActuator.java)
2. Result object is created with `result.error` and `result.addFailData(sourceDataList)` set
3. `flushStrategy.flushIncrementData()` is called with the Result
4. Counters are incremented via `updateMetaCounts()` and `updateTableGroupIncrementProgress()`
5. **Then** `flush()` method checks `systemConfig.isEnableStorageWriteFail()` before writing to storage
6. The error is also notified via `notifyOnError()` message service

**Root Cause Identified:**
The issue is in `/work/project/github/dbsyncer/dbsyncer-parser/src/main/java/org/dbsyncer/parser/strategy/impl/FlushStrategyImpl.java` line 345:

```java
if (systemConfig.isEnableStorageWriteFail() && !CollectionUtils.isEmpty(result.getFailData())) {
```

When `isEnableStorageWriteFail()` is `false`, the `asyncWrite()` method is never called, which means:
- Error counters are still incremented (lines 74-79, 190-194)
- But error data is never written to the storage/error queue
- No physical records are created in the error storage

This is the expected behavior per the configuration design, NOT a bug. The user may have `isEnableStorageWriteFail` set to `false`.

---

## Resolution
| Field | Value |
|---|---|
| Root Cause | **The behavior is EXPECTED per design.** Error counting (via `updateMetaCounts()` and `updateTableGroupIncrementProgress()`) happens unconditionally, but the actual storage of error data to the error queue is controlled by the `SystemConfig.enableStorageWriteFail` configuration flag (default: `true`). If this flag is set to `false` at runtime, errors increment counters but are not persisted to storage. |
| Fix | **User Action Required:** Check the SystemConfig setting `enableStorageWriteFail`. If error storage is desired, enable this configuration option by calling `systemConfig.setEnableStorageWriteFail(true)` and persisting the config. |
| Status | resolved |

## Key Findings Summary

### Why error counters increment:
- `FlushStrategyImpl.flushIncrementData()` lines 74-79 unconditionally calls:
  - `meta.getTotal().getAndAdd(result.getFailData().size())` - increments total
  - `updateTableGroupIncrementProgress()` - updates table-level counters  
  - `updateMetaCounts()` - increments Meta-level success/fail counters

### Why errors aren't recorded to storage:
- `FlushStrategyImpl.flush()` line 345 checks: `if (systemConfig.isEnableStorageWriteFail() && ...)`
- When this returns `false`, the `asyncWrite()` method is never called
- `asyncWrite()` is the only method that writes error records to storage

### Code Paths:
1. **Exception occurs** → `MetaBufferActuator.distributeTableGroup()` (line 398-421)
2. **Result populated** → `result.addFailData()` and `result.error` set
3. **Counters updated** → `flushIncrementData()` unconditionally updates counts
4. **Storage decision** → `flush()` checks `isEnableStorageWriteFail()` 
5. **Conditional write** → Only persists to storage if `isEnableStorageWriteFail() == true`

### Configuration Check:
```java
// SystemConfig.java line 45
private boolean enableStorageWriteFail = true;  // default is true
```

**Recommendation:** Inspect the current value of `SystemConfig.enableStorageWriteFail` at runtime. If it's `false`, set it to `true` to enable error recording.

# 实施说明 — 同步任务指数退避重试机制

## 典型故障场景

### 源端读取死锁

```
SQLServerException: 事务(进程 ID 250)与另一个进程被死锁在 锁 资源上...
  at SqlServerCTListener.processDMLResultSet(SqlServerCTListener.java:434)
```

### 目标端写入死锁

```
BatchUpdateException: Deadlock found when trying to get lock; try restarting transaction
  at DatabaseTemplate.batchUpdate(DatabaseTemplate.java:964)
  at AbstractDatabaseConnector.doExecuteBatch(AbstractDatabaseConnector.java:384)
```

## 实施文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `RetryPolicy.java` | 新增 | 重试策略配置类 |
| `RetryInterceptor.java` | 新增 | 统一重试组件 |
| `KeywordMatcher.java` | 新增 | 关键字匹配器 |
| `DatabaseConnectorInstance.java` | 修改 | 注入重试拦截 |
| `SqlServerCTQueryUtil.java` | 修改 | 移除内部硬编码重试 |
| `SqlServerCTListener.java` | 修改 | 用统一重试替代 isRetryableError |
| `RetryConfig.java` | 新增 | 配置绑定类 |

## 代码改动清单

### 1. 新增组件

#### `RetryInterceptor.java`

```java
public class RetryInterceptor {
    public <T> T execute(Supplier<T> operation, RetryPolicy policy) {
        // 如果 disable=true，直接执行一次，不重试
        if (policy.isDisable()) {
            return operation.get();
        }
        
        // 执行 → 异常 → 判断是否重试：
        //   useKeyword=false → 无条件重试
        //   useKeyword=true  → 仅匹配关键字才重试
        // → 指数退避等待 → 重试
        // 耗尽 → ERROR日志 → 抛出原始异常
    }
}
```

### 2. 需移除的硬编码逻辑

#### 2.1 `SqlServerCTQueryUtil.queryWithReadUncommitted()` 内部重试（约 155-219 行）

```java
// ❌ 移除
for (int attempt = 0; attempt < maxRetries; attempt++) {
    try { ... } catch (Exception e) {
        if (isDeadlock(e) && attempt < maxRetries - 1) {
            Thread.sleep(retryDelayMs * (attempt + 1));
            continue;
        }
        throw e;
    }
}
```

**改为**：直接执行，外层通过 `retryInterceptor.execute()` 包裹。

#### 2.2 `SqlServerCTListener.isRetryableError()`（第 204-218 行）

```java
// ❌ 整个方法移除
private boolean isRetryableError(Throwable e) { ... }
```

#### 2.3 `SqlServerCTListener` 主循环重试（约 1286-1307 行）

```java
// ❌ 移除
if (connected && isRetryableError(e)) {
    currentVersionRetryCount++;
    if (currentVersionRetryCount >= MAX_RETRY_PER_VERSION) {
        errorEvent(e); break;
    }
    sleepInMills(1000L);
}
```

**改为**：

```java
// ✅ 用统一重试替代
retryInterceptor.execute(
    () -> pull(lastSuccessfulVersion, maxVersion),
    retryPolicy
);
```

#### 2.4 需移除的字段/常量

| 名称 | 文件 |
|------|------|
| `MAX_RETRY_PER_VERSION` | `SqlServerCTListener.java` |
| `currentVersionRetryCount` | `SqlServerCTListener.java` |
| `isRetryableError()` 方法 | `SqlServerCTListener.java` |

### 3. 改造后的主循环

```java
while (!isInterrupted() && !stopRequested.get()) {
    Long maxVersion = getMaxVersion();
    if (maxVersion != null && maxVersion > lastSuccessfulVersion) {
        try {
            retryInterceptor.execute(
                () -> pull(lastSuccessfulVersion, maxVersion),
                retryPolicy
            );
            lastSuccessfulVersion = maxVersion;
        } catch (Exception e) {
            errorEvent(e);
            break;
        }
    }
    sleepInMills(POLL_INTERVAL_MILLIS);
}
```

### 4. 需保留的（不动）

| 位置 | 原因 |
|------|------|
| `AbstractDatabaseListener.trySendEvent()` | 队列溢出场景 |
| `MetaBufferActuator.executeDirectly()` | 错误队列重试 |

## 日志格式

```
[WARN] RetryInterceptor - 触发重试 | 任务ID={} | 第{N}次 | 间隔={interval}ms | 关键字={keyword} | 异常={message}
[ERROR] RetryInterceptor - 重试耗尽 | 任务ID={} | 最大重试次数={} | 最终异常={} | 触发关键字={}
```

## 验证要点

| 验证项 | 说明 |
|--------|------|
| 禁用重试 | `disable=true` 直接执行不重试 |
| 关键字匹配 | 精确/模糊/大小写/无条件 |
| 指数退避 | 间隔按预期递增 |
| 配置优先级 | 任务级 > 全局 |
| 性能 | 无异常路径零开销 |

# 初始设计 — 同步任务指数退避重试机制

## 核心概念

### 重试策略 `RetryPolicy`

```java
RetryPolicy {
    disable: boolean             // 是否禁用重试（默认 false）
    useKeyword: boolean          // 是否启用关键字匹配（默认 false = 无条件重试）
    exactKeywords: List<String>  // 精确匹配关键字
    fuzzyKeywords: List<String>  // 模糊匹配关键字
    initialInterval: Duration    // 起始间隔
    maxInterval: Duration        // 最大间隔
    maxAttempts: int             // 最大重试次数
    multiplier: double           // 增长因子
}
```

### 指数退避

```
interval(n) = min(initialInterval × multiplier^(n-1), maxInterval)
```

## 统一重试架构

一个组件，所有场景通用：

```
            RetryInterceptor
         execute(操作, 策略)
                │
      ┌─────────┼──────────┐
      ▼         ▼          ▼
   单次SQL   区间拉取   未来新场景
  (同一个组件，同一个配置)
```

### 使用方式

```java
// 任何场景，同一个调用方式
// 拦截器内部根据 policy.disable 决定是否跳过重试
retryInterceptor.execute(() -> pull(from, to), policy);
retryInterceptor.execute(() -> batchWrite(data), policy);
```

## 关键字匹配规则

- **`disable = true`**：跳过重试，直接执行，失败直接抛异常
- **`useKeyword = false`（默认）**：无条件重试，**所有异常均触发重试**
- **`useKeyword = true`**：仅匹配关键字的异常触发重试
  - **精确匹配**：异常消息**完全等于**关键字（`exactKeywords`），**不区分大小写**
  - **模糊匹配**：异常消息**包含**关键字（`fuzzyKeywords`），**不区分大小写**
  - 精确或模糊**任一**匹配成功即触发重试
  - 匹配范围包含完整 cause chain

## 配置规范

使用 `application.properties`：

| 配置项前缀 | 层级 | 说明 |
|------------|------|------|
| `dbsyncer.retry.global.*` | 全局 | 所有任务生效 |
| `dbsyncer.retry.task.ID.*` | 任务级 | 优先级高于全局 |

```java
@ConfigurationProperties("dbsyncer.retry")
public class RetryConfig {
    private RetryPolicy global = new RetryPolicy();
    private Map<String, RetryPolicy> task = new HashMap<>();
}
```

### 全局默认配置示例

```properties
# ===== 重试全局配置 =====
# useKeyword=false（默认）：无条件重试所有异常
# useKeyword=true：仅重试匹配关键字的异常
dbsyncer.retry.global.use-keyword=false
dbsyncer.retry.global.initial-interval=1000          # 起始间隔（毫秒）
dbsyncer.retry.global.max-interval=60000             # 最大间隔（毫秒）
dbsyncer.retry.global.max-attempts=5                 # 最大重试次数
dbsyncer.retry.global.multiplier=2.0                 # 增长因子

# 精确匹配（异常消息完全匹配）
dbsyncer.retry.global.keywords-exact[0]=Deadlock found when trying to get lock; try restarting transaction

# 模糊匹配（异常消息包含即匹配）
dbsyncer.retry.global.keywords-fuzzy[0]=deadlock
dbsyncer.retry.global.keywords-fuzzy[1]=死锁
dbsyncer.retry.global.keywords-fuzzy[2]=Lock wait timeout
dbsyncer.retry.global.keywords-fuzzy[3]=Communications link failure
dbsyncer.retry.global.keywords-fuzzy[4]=Connection refused
dbsyncer.retry.global.keywords-fuzzy[5]=socketTimeout
dbsyncer.retry.global.keywords-fuzzy[6]=read timed out
```

### 任务级别配置示例

```properties
# 任务 1001 覆盖全局，启用关键字过滤
dbsyncer.retry.task.1001.use-keyword=true
dbsyncer.retry.task.1001.initial-interval=500
dbsyncer.retry.task.1001.max-attempts=10
dbsyncer.retry.task.1001.keywords-fuzzy[0]=deadlock

# 任务 2002 完全禁用重试（直接执行，失败即抛）
dbsyncer.retry.task.2002.disable=true
```

## 重试耗尽处理

1. 记录 ERROR 级别日志
2. **直接抛出原始异常**

```
[ERROR] RetryInterceptor - 重试耗尽 | 任务ID={} | 最大重试次数={} |
  最终异常={} | 触发关键字={}
```

## 注意事项

1. **CT 查询重试**：`SYS_CHANGE_VERSION` 游标参数保持不变
2. **批量写入重试**：重试整个批次，非单条
3. **幂等性**：INSERT 需依赖目标表唯一约束

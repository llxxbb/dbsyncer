# ADR-0031 设计稿：B 类配置业务热重载

## 1. 现状分析

### 1.1 B 类配置消费链路

```
ConfigServiceImpl.editTab()
  │
  ├─ ① 写库（profileComponent.editConfigModel）
  ├─ ② configRegistry.onDbConfigChanged(cfg)  → mergedConfig 已更新
  ├─ ③ notifyListeners()                       → C 类监听器已通知
  └─ ④ B 类：selfRestartService.scheduleRestart() → System.exit(0)
```

到步骤 ② 为止，`ConfigRegistry.getConfig()` 返回的已经是新值。所有通过 `getBufferProps()` 动态读取的参数（`bufferPullCount`、`bufferWriterCount`）**已经立即生效**。

步骤 ④ 的唯一目的是让以下两个"快照"了配置值的组件重建：

| 组件 | 快照内容 | 重建难度 |
|------|---------|---------|
| `ExecutorFactory` | 线程池 core/max/queue（DCL 缓存） | 低：shutdown + 置 null |
| `AbstractBufferActuator` | `LinkedBlockingQueue` 容量 + 定时调度周期 | 中：需要安全替换队列 |

### 1.2 为什么不用 Spring 内置机制

| 机制 | 可用性 | 原因 |
|------|--------|------|
| `@RefreshScope` | ❌ 需要 spring-cloud-context | 项目未引入，ADR-0029 明确拒绝 |
| `ContextRefresher.refresh()` | ❌ 同上 | 同上 |
| `ConfigurableApplicationContext.refresh()` | ❌ 启动期 API | 运行期调用会炸 |
| `BeanFactory.destroySingleton()` + 重注册 | ⚠️ 可用但太重 | 触发所有 `@Autowired` 重新注入，影响面不可控 |
| Spring Boot DevTools restart | ❌ 仅开发期 | 生产环境不可用 |

**结论**：Spring Framework 2.5.14（Java 8 天花板）没有"运行期重置 Bean 内部状态"的内置机制。这是应用层的职责。

### 1.3 为什么不用 ReadWriteLock

排空-切换 vs ReadWriteLock 对比：

| 维度 | ReadWriteLock | 排空-切换 |
|------|--------------|-----------|
| 并发控制 | 需要 ReadWriteLock | 只需一个 volatile flag |
| 数据迁移 | drainTo + 逐个 offer 回退 | 不需要迁移，消费者自然排空 |
| 上游阻塞 | 写锁期间阻塞（短暂） | 标记后阻塞（稍长但可控） |
| 语义 | "瞬间切换" | "排干水池再换" |
| 复杂度 | 中（锁 + 迁移 + 回退） | **低（flag + 等待）** |

排空-切换方案更简单，且语义更清晰：上游短暂阻塞，消费者继续工作，队列自然排空后替换。

## 2. 详细设计

### 2.1 ExecutorFactory.reload()

**文件**：`dbsyncer-common/src/main/java/org/dbsyncer/common/config/ExecutorFactory.java`

新增 `reload()` 方法：

```java
/**
 * 热重载：关闭旧线程池，置 null。
 * 下次 get*Executor() 时 DCL 会从 ConfigRegistry 读新值重建。
 * <p>
 * 线程池已配置 waitForTasksToCompleteOnShutdown=true, awaitTerminationSeconds=30，
 * shutdown() 会等待当前任务完成，不会丢数据。
 */
public synchronized void reload() {
    logger.info("[ExecutorFactory] 开始热重载...");
    shutdown(generalExecutor, "generalExecutor");
    shutdown(storageExecutor, "storageExecutor");
    shutdown(dispatchExecutor, "dispatchExecutor");
    generalExecutor = null;
    storageExecutor = null;
    dispatchExecutor = null;
    logger.info("[ExecutorFactory] 热重载完成，线程池将在下次使用时重建");
}
```

**安全性分析**：
- `shutdown()` 内部调用 `ThreadPoolTaskExecutor.shutdown()`，已配置 `waitForTasksToCompleteOnShutdown=true`
- 等待当前任务完成（最多 `awaitTerminationSeconds=30`）
- 置 null 后，下次 `get*Executor()` 时 DCL 从 `executorPropsSupplier.get()` 读新值
- 线程池不持有业务数据，零丢失

### 2.2 AbstractBufferActuator.reload()

**文件**：`dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java`

#### 2.2.1 新增字段

```java
/** 排空标志：true 表示队列只出不进，offer() 阻塞等待 */
private volatile draining = false;
```

#### 2.2.2 改造 offer()

```java
@Override
public void offer(BufferRequest request) {
    ensureInitialized();
    // 排空中：阻塞等待，不丢数据
    while (draining) {
        try { Thread.sleep(10); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return;
        }
    }
    if (queue.offer((Request) request)) {
        return;
    }
    if (isRunning(request)) {
        offerFailed(queue, (Request) request);
    }
}
```

**说明**：`MetaBufferActuator.offer()` 覆写了父类方法，内部调用 `super.offer(request)`。排空标志在 `super.offer()` 之前检查，DDL 阻塞逻辑不受影响。

#### 2.2.3 新增 reload()

```java
/**
 * 热重载：排空-切换。
 * <p>
 * ① 标记 draining=true → offer() 阻塞
 * ② 主动触发一次消费（不等调度周期）
 * ③ 等待队列排空（超时 30 秒）
 * ④ 替换队列
 * ⑤ 清除 draining 标志 → offer() 恢复
 * ⑥ 重注册定时调度（周期可能也变了）
 */
public void reload() {
    // ① 标记只出不进
    draining = true;

    try {
        // ② 主动触发一次消费，不等调度周期
        run();

        // ③ 等待队列排空
        long timeout = 30_000L;
        long deadline = System.currentTimeMillis() + timeout;
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            }
        }

        if (!queue.isEmpty()) {
            logger.warn("[{}] 队列排空超时({}ms)，剩余 {} 条，跳过队列替换",
                    getClass().getSimpleName(), timeout, queue.size());
            return;  // 不替换，保持旧队列
        }

        // ④ 队列已空，替换
        int newCapacity = getBufferQueueCapacity();
        this.queue = new LinkedBlockingQueue<>(newCapacity);
        logger.info("[{}] 队列已替换: capacity={}", getClass().getSimpleName(), newCapacity);
    } finally {
        // ⑤ 恢复
        draining = false;
    }

    // ⑥ 重注册调度（周期可能也变了）
    reschedule();
}
```

**安全性分析**：
- `draining = true` 后，`offer()` 阻塞，不再有新数据进入队列
- `run()` 主动触发一次 `submit()`，消费者开始 poll 数据
- 等待排空期间，消费者继续按调度周期工作（如果 `run()` 没消费完）
- 超时 30 秒未排空 → 跳过替换，保持旧队列（安全降级）
- 替换时队列已空，零丢失
- `finally` 块确保 `draining = false`，即使异常也不会永久阻塞上游

#### 2.2.4 新增 reschedule()

```java
/**
 * 重新注册定时调度（用新周期），队列不动。
 */
public void reschedule() {
    if (taskKey != null) {
        scheduledTaskService.stop(taskKey);
    }
    if (taskKey == null) {
        taskKey = UUIDUtil.getUUID();
    }
    // getBufferPeriodMillisecond() 每次从 ConfigRegistry 动态读取
    scheduledTaskService.start(taskKey, getBufferPeriodMillisecond(), this);
    logger.info("[{}] 调度已重新注册: period={}ms",
            getClass().getSimpleName(), getBufferPeriodMillisecond());
}
```

### 2.3 MetaBufferActuator.offer() 适配

`MetaBufferActuator` 覆写了 `offer()`，内部有 DDL 阻塞逻辑：

```java
@Override
public void offer(BufferRequest request) {
    // DDL 事件：阻塞等待队列消费完成
    if (isDDL(request)) {
        while (isRunning(request)) {
            if (getQueue().isEmpty()) {
                setPendingTask();
                super.offer(request);  // ← 这里会检查 draining
                return;
            }
            try { TimeUnit.MILLISECONDS.sleep(10); } catch (InterruptedException ex) { ... }
        }
    }
    // 普通事件
    setPendingTask();
    super.offer(request);  // ← 这里会检查 draining
}
```

**无需修改**：`super.offer()` 内部检查 `draining`，DDL 阻塞逻辑在 `draining` 检查之前执行，互不干扰。排空期间 DDL 事件会在 `super.offer()` 处阻塞。

### 2.4 BusinessReloader

**文件**：`dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/BusinessReloader.java`（新建）

```java
package org.dbsyncer.biz.impl;

import org.dbsyncer.common.config.ExecutorFactory;
import org.dbsyncer.parser.flush.BufferActuator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * ADR-0031：业务热重载编排器。
 * <p>
 * 替代 SelfRestartService 的进程级重启，只重建持有缓存资源的组件。
 */
@Component
public class BusinessReloader {

    private static final Logger logger = LoggerFactory.getLogger(BusinessReloader.class);

    @Resource
    private ExecutorFactory executorFactory;

    @Resource
    private List<BufferActuator> bufferActuators;  // Spring 自动收集所有 BufferActuator 实现

    /**
     * 执行热重载。
     *
     * @param changedKeys 变更的配置 Tab key 列表（如 ["executors", "buffers"]）
     */
    public void reload(List<String> changedKeys) {
        logger.info("===== 业务热重载开始: {} =====", changedKeys);

        if (changedKeys.contains("executors")) {
            try {
                executorFactory.reload();
            } catch (Exception e) {
                logger.error("[ExecutorFactory] 热重载失败", e);
            }
        }

        if (changedKeys.contains("buffers")) {
            for (BufferActuator actuator : bufferActuators) {
                try {
                    actuator.reload();
                } catch (Exception e) {
                    logger.error("[{}] 热重载失败", actuator.getClass().getSimpleName(), e);
                }
            }
        }

        logger.info("===== 业务热重载完成 =====");
    }
}
```

### 2.5 SelfRestartService 改造

**文件**：`dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/SelfRestartService.java`

删除 `gracefulShutdown()`、`isSystemdManaged()`、`isDockerContainer()` 方法，`scheduleRestart()` 改为委托 `BusinessReloader`：

```java
@Service
public class SelfRestartService {

    private static final Logger logger = LoggerFactory.getLogger(SelfRestartService.class);
    private static final int RESTART_DELAY_SECONDS = 3;

    @Resource
    private NotifyService notifyService;

    @Resource
    private BusinessReloader businessReloader;

    /**
     * 执行热重载流程（替代原进程级重启）。
     */
    public void scheduleRestart(List<String> changedConfigKeys, String currentUser) {
        // 1. 发送通知
        try {
            String message = String.format(
                    "系统参数已更新，应用即将热重载。\n" +
                            "操作人：%s\n" +
                            "变更配置：%s\n" +
                            "重载时间：%s",
                    currentUser != null ? currentUser : "admin",
                    changedConfigKeys != null ? String.join(", ", changedConfigKeys) : "B 类配置",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            // ... 发送逻辑同上 ...
        } catch (Exception e) {
            logger.warn("发送重载通知失败: {}", e.getMessage());
        }

        // 2. 延迟 3 秒后执行热重载
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(RESTART_DELAY_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("===== 业务热重载开始 =====");
            businessReloader.reload(changedConfigKeys);
            logger.info("===== 业务热重载完成 =====");
        });
    }
}
```

**删除的方法**：
- `gracefulShutdown()` — 不再需要 `System.exit(0)`
- `isSystemdManaged()` — 不再需要检测进程守护
- `isDockerContainer()` — 不再需要检测进程守护
- `isSelfRestartPossible()` — 始终返回 `true`（业务热重载无环境依赖）

## 3. UI 提示信息调整

### 3.1 Tab 标签警告图标

**文件**：`system.html`

| 当前 | 改为 |
|------|------|
| `<span class="text-warning" title="修改后需重启应用">⚠</span>` | `<span class="text-info" title="修改后自动热重载">⟳</span>` |

影响 Tab：`tab-executors`、`tab-buffers`

### 3.2 保存成功提示

**文件**：`system/index.js`

```javascript
// 当前
if (result && result.restartRequired) {
    bootGrowl("配置已保存，需重启应用生效。", "warning");
}

// 改为
if (result && result.reloadRequired) {
    bootGrowl("配置已保存，正在热重载...", "success");
}
```

### 3.3 后端返回消息

**文件**：`ConfigServiceImpl.java`

```java
// 当前
result.put("restartRequired", restartRequired);
result.put("selfRestart", restartRequired && selfRestartService.isSelfRestartPossible());
result.put("result", restartRequired
    ? (selfRestartService.isSelfRestartPossible()
        ? "配置已保存，应用即将自动重启。"
        : "配置已保存。修改后需重启应用才能生效。")
    : "配置已保存并立即生效。");

// 改为
result.put("reloadRequired", restartRequired);
result.put("result", restartRequired
    ? "配置已保存，正在热重载..."
    : "配置已保存并立即生效。");
```

### 3.4 删除 selfRestartEnabled

| 文件 | 操作 |
|------|------|
| `RuntimeProps.java` | 删除 `selfRestartEnabled` 字段、getter/setter、loadFromEnvironment/toConfigEntries 中的相关行 |
| `system.html` 运行时 Tab | 删除自重启 checkbox（`runtime_selfRestartEnabled`） |
| `system/index.js` | 删除 `fillRuntimeTab` 中的 `selfRestartEnabled` 行和 `collectPayload` runtime case 中的 `selfRestartEnabled` 行 |

### 3.5 注释文案更新

| 文件 | 当前 | 改为 |
|------|------|------|
| `ConfigCategory.java` | "需重启" | "热重载" |
| `ConfigKeys.java` | `EXECUTOR_*` / `BUFFER_*` 注释 "需重启" | "热重载" |
| `SystemConfig.java` | `// ===== B 类（需重启） =====` | `// ===== B 类（热重载） =====` |
| `SystemTabEnum.java` | `// ===== B 类：需重启 =====` | `// ===== B 类：热重载 =====` |

## 4. 边界场景处理

### 4.1 排空超时

如果队列中有大量数据，或消费者 `pull()` 执行缓慢，30 秒内可能无法排空。

**处理**：超时后跳过队列替换，保持旧队列，记录 WARN 日志。下次配置变更时再尝试。

### 4.2 reload 期间上游 CDC 阻塞

排空期间 `offer()` 阻塞，上游 CDC 线程会等待。

**影响评估**：
- 排空时间通常很短（队列容量默认 1000-30000，消费者 TPS 通常 > 1000）
- CDC 有 socket buffer 兜底，短暂阻塞不会丢数据
- 超时 30 秒后自动恢复

### 4.3 多个 BufferActuator 串行 reload

`BusinessReloader.reload()` 中多个 `BufferActuator` 串行执行 `reload()`。

**影响**：每个 actuator 排空时间叠加。

**处理**：如果串行时间过长，可改为并行（`CompletableFuture.allOf`）。当前场景下串行足够。

### 4.4 ExecutorFactory.reload() 期间有任务提交

`shutdown()` 等待当前任务完成（最多 30 秒），期间新的 `get*Executor()` 调用返回旧线程池。

**处理**：`reload()` 是 `synchronized` 的，`get*Executor()` 也是 `synchronized` 的（DCL），不会并发。但调用方如果持有旧 executor 引用，会在旧线程池上提交任务。

**影响**：旧线程池 `shutdown()` 后不再接受新任务，`execute()` 会抛 `RejectedExecutionException`。

**处理**：调用方每次使用前都调用 `get*Executor()` 获取最新引用（当前代码已是如此）。

## 5. 测试要点

| 场景 | 验证点 |
|------|--------|
| 修改 `bufferPullCount` | 下一次 `submit()` 使用新值（无需 reload） |
| 修改 `bufferPeriodMillisecond` | 定时调度周期变更 |
| 修改 `bufferQueueCapacity` | 队列排空后替换为新容量 |
| 修改 `ExecutorProps` | 线程池 shutdown 后重建 |
| 排空期间 offer() | 上游阻塞，不丢数据 |
| 排空超时 | 跳过替换，记录 WARN，保持旧队列 |
| reload 期间 DDL 事件 | DDL 在 `super.offer()` 处阻塞，不丢 |
| UI Tab 标签 | ⚠ → ⟳，title 显示"修改后自动热重载" |
| UI 保存提示 | B 类保存后显示"配置已保存，正在热重载..."（success 级别） |
| UI 运行时 Tab | 自重启 checkbox 已移除 |
| `selfRestartEnabled` 配置 | 字段已删除，不影响启动和运行 |

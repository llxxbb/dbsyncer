# ADR-0013 设计变更 v2

## 元信息

| 字段 | 值 |
|------|-----|
| 变更版本 | v2 |
| 作者 | 老李 |
| 日期 | 2026-06-02 |
| 基于版本 | design-base.md |

## 变更内容

### 新增：重试终止模式（Termination Mode）

RetryPolicy 增加 `terminationMode` 枚举，控制 `maxAttempts` 与 `maxDuration` 的终止行为：

```java
public enum TerminationMode {
    MAX_ATTEMPTS,      // 仅最大次数，忽略 maxDuration
    MAX_DURATION,      // 仅最大间隔（总耗时），忽略 maxAttempts
    WHICHEVER_FIRST    // 最大次数和最大间隔哪个先到就终止（默认）
}
```

#### `RetryPolicy` 变更

```java
RetryPolicy {
    disable: boolean              // 不变
    useKeyword: boolean           // 不变
    exactKeywords: List<String>   // 不变
    fuzzyKeywords: List<String>   // 不变
    initialInterval: Duration     // 不变，单次等待间隔
    maxInterval: Duration         // 不变，单次等待间隔上限
    maxAttempts: int              // 不变，最大重试次数
    multiplier: double            // 不变，增长因子
    terminationMode: TerminationMode  // ✅ 新增，默认 WHICHEVER_FIRST
    maxDuration: Duration         // ✅ 新增，重试总耗时上限
}
```

- `terminationMode` 默认 `WHICHEVER_FIRST`，保持与原设计一致的行为
- **`maxDuration` 作为默认生效的约束条件**：全局配置有缺省值（如 5 分钟），不显式配置时也参与终止判断
- 新增 `maxDuration` 字段，与 `maxInterval` 区分：
  - `maxInterval`：单次等待间隔上限（指数退避封顶值）
  - `maxDuration`：重试总耗时上限（从首次执行开始计时），**全局有缺省值，默认参与终止判断**

#### 终止判断逻辑

```
WHICHEVER_FIRST（默认）：
  attempt >= maxAttempts || elapsed >= maxDuration → 停止

MAX_ATTEMPTS：
  attempt >= maxAttempts → 停止（不检查 elapsed）

MAX_DURATION：
  elapsed >= maxDuration → 停止（不检查 attempt）
```

#### 配置示例

```properties
# 模式：哪个先到就终止（默认）
dbsyncer.retry.global.termination-mode=WHICHEVER_FIRST
dbsyncer.retry.global.max-attempts=5
dbsyncer.retry.global.max-duration=300000     # 5 分钟总耗时上限

# 模式：仅最大次数
dbsyncer.retry.task.1001.termination-mode=MAX_ATTEMPTS
dbsyncer.retry.task.1001.max-attempts=10

# 模式：仅最大间隔（总耗时 2 分钟内无限重试）
dbsyncer.retry.task.2002.termination-mode=MAX_DURATION
dbsyncer.retry.task.2002.max-duration=120000
```

### 原因

用户场景多样化：
- **开发调试**：只想限制次数，方便观察每次重试效果（MAX_ATTEMPTS）
- **实时性要求高**：总耗时不能超预算，次数不限（MAX_DURATION）
- **生产默认**：双重保护，避免无限制重试（WHICHEVER_FIRST）

## 影响评估

- [ ] 前端需要调整（配置页面增加模式选择 + maxDuration 字段）
- [x] 后端需要调整（RetryPolicy + RetryInterceptor）
- [ ] 数据库变更
- [ ] 需要过渡期（否，向后兼容，默认值保持原行为）

## 审查确认

- [ ] 已通知相关人员
- [ ] 无需重新审查

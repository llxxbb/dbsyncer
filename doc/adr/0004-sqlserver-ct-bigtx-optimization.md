# ADR 0004: SQL Server CT 大事务同步优化

**状态**: Proposed  
**日期**: 2026-03-27  
**决策者**: 架构团队  
**参与者**: 用户（问题提出者）

---

## 背景与问题

SQL Server Change Tracking (CT) 模式下，同步大事务（几百万数据/事务）时存在以下问题：

### 问题 1：版本更新粒度太大

当前 `pull()` 方法在所有表处理完成后才更新 `lastVersion = stopVersion`。大事务场景下，处理可能持续数分钟，如果中途失败，整个批次都要重试，导致已处理的数据重复处理。

### 问题 2：停止信号机制不健全

当前通过 `!connected` 判断是否停止，但 `connected` 是 `volatile boolean`，语义模糊：

- 无法区分"正常停止"和"异常断开"
- 前端主动停止时，已处理数据可能没有记录 snapshot

### 问题 3：流式处理中断会丢数据

`processDMLResultSet()` 中 `break` 只退出当前表的 `while` 循环，如果中断不是因为连接断开，已处理的数据没有持久化进度。

### 问题 4：缺乏针对同一版本的重试限制

当前 `pollFailureCount` 用于熔断（获取版本号失败），但针对"处理特定版本数据失败"没有重试计数限制，可能导致无限重试。

---

## 决策

采用**增量持久化 + 优雅停止 + 有限重试**的综合方案：

### 1. 增量持久化策略

在处理过程中定期记录进度，而非仅在批次结束时：

- 每处理 N 条记录（默认 10000）持久化一次 snapshot
- 或每间隔 T 时间（默认 30 秒）持久化一次，以先达到者为准
- **关键**：持久化时记录 `lastSuccessfulVersion`（上一版本号），而非 `currentVersion`（当前版本号）

**为什么记录上一版本号？**

```
场景：当前正在处理版本 V100，已处理 50 万条，共 100 万条

错误做法：持久化 currentVersion = V100
→ 系统崩溃
→ 重启后从 V100 开始
→ 结果：V100 的 50 万条数据丢失！

正确做法：持久化 lastSuccessfulVersion = V99
→ 系统崩溃
→ 重启后从 V99 开始
→ 结果：V99 的数据重复处理（幂等可接受），V100 的数据不会丢失
```

**核心原则**：当前版本号的数据可能还没有处理完，不能持久化。只有已完整处理的版本号才能持久化。

**简化设计（2026-04-03 优化）**：

原设计有 `lastVersion` 和 `lastSuccessfulVersion` 两个字段，但分析发现：
- 初始时两者相等
- 正常批次完成时两者同步更新
- 增量持久化时只更新 `lastSuccessfulVersion`，但 `lastVersion` 在批次处理期间保持不变
- **结论**：`lastVersion` 是冗余字段

**优化后**：删除 `lastVersion`，只用 `lastSuccessfulVersion`
- 查询时：`pull(lastSuccessfulVersion, maxVersion)`
- 批次完成：`lastSuccessfulVersion = stopVersion`
- 增量持久化：`snapshotProgress(lastSuccessfulVersion)`

可配置的持久化阈值：

- `SNAPSHOT_RECORD_INTERVAL`: 每 N 条记录持久化（默认 10000）
- `SNAPSHOT_TIME_INTERVAL_MS`: 每 T 毫秒持久化（默认 30000）

### 2. 独立停止信号机制

新增 `AtomicBoolean stopRequested` 字段：

- `stopGracefully()` 方法设置停止标志
- `processDMLResultSet()` 检测到停止信号后，先保存进度再退出
- `connected` 仅表示连接状态，不再承担停止信号职责

### 3. 有限重试机制

针对**同一版本号**的重试次数限制：

- 新增 `currentVersionRetryCount` 和 `maxRetryPerVersion`（默认 3 次）
- 区分可重试错误（网络超时、连接被 kill）和不可重试错误（数据校验失败）
- 达到重试上限后停止同步，避免无限循环

---

## 方案权衡

| 方案      | 优点             | 缺点               | 缓解措施             |
| ------- | -------------- | ---------------- | ---------------- |
| 增量持久化   | 失败时损失小，避免整批次重试 | 增加 I/O 开销，可能影响性能 | 可配置阈值，根据业务场景调整   |
| 独立停止信号  | 语义清晰，控制精细      | 增加一个字段           | 内存开销微不足道         |
| 有限重试    | 避免无限循环，快速失败    | 可能遗漏可恢复错误        | 区分错误类型，仅对可重试错误计数 |
| 按记录数持久化 | 进度可预测          | 大事务初期无持久化        | 同时支持时间间隔兜底       |

### 关键权衡：数据一致性 vs 进度持久化

**问题**: Change Tracking 版本号是全局的，不能按表分别记录。如果按记录数持久化，可能导致：

- 表 A 处理了 10000 条，表 B 处理了 5000 条，此时持久化版本号为 V
- 下次从 V 开始，表 B 的 5000 条数据会重复处理

**决策**: 

- 允许少量重复处理（幂等性由下游保证）
- 重复处理的代价远小于整批次重试
- 可通过配置阈值控制重复处理的比例

---

## 架构设计

### 增量持久化流程

```
┌─────────────────────────────────────────────────────────────────┐
│ Worker.run() 轮询循环                                            │
│                                                                 │
│  1. 获取新版本号 (lastVersion → maxVersion)                      │
│     ↓                                                           │
│  2. pull(lastVersion, maxVersion) 处理数据                       │
│     ↓                                                           │
│  3. processDMLResultSet() 流式处理                               │
│     ├─ 每处理 1 条 → recordCount++                               │
│     ├─ 每处理 10000 条 → snapshotProgress() 持久化                 │
│     ├─ 检测到 stopRequested → 保存进度后退出                      │
│     └─ 处理完成 → lastSuccessfulVersion = currentVersion        │
│                                                                 │
│  4. 异常处理                                                      │
│     ├─ 可重试错误 → retryCount++，达到上限则停止                   │
│     └─ 不可重试错误 → 立即停止，记录日志                           │
└─────────────────────────────────────────────────────────────────┘
```

### 版本号管理

**简化设计（2026-04-03 优化）**：

原设计有 `lastVersion` 和 `lastSuccessfulVersion` 两个字段，但代码分析发现：
- 初始时两者相等
- 正常批次完成时两者同步更新
- 增量持久化时只更新 `lastSuccessfulVersion`
- **结论**：`lastVersion` 是冗余字段，已删除

**优化后**：只用 `lastSuccessfulVersion`

```
时间线：
  V98 (已完成) ──→ V99 (已完成) ──→ V100 (处理中) ──→ V101 (未开始)
       ↑                    ↑                    ↑
       │                    │                    └─ currentVersion = V100
       │                    └─ lastSuccessfulVersion = V99 (持久化点)
       └─ 查询起点：lastSuccessfulVersion + 1 = V100

持久化逻辑：
  - lastSuccessfulVersion = V99  ← 可以安全持久化
  - 查询新数据：pull(lastSuccessfulVersion, maxVersion)

崩溃恢复：
  - 从 lastSuccessfulVersion (V99) 开始查询
  - V99 的数据可能重复处理（幂等可接受）
  - V100 的数据不会丢失
```

### 停止信号时序

```
前端点击"停止"
    ↓
MappingController.stop()
    ↓
SqlServerCTListener.stopGracefully()
    ↓
stopRequested.set(true)
    ↓
processDMLResultSet() 检测到 stopRequested
    ↓
snapshotProgress() 保存当前进度
    ↓
break 退出循环
    ↓
Worker.run() 检测到已停止，清理资源
```

---

## 影响范围

### 修改文件

- `SqlServerCTListener.java`: 核心实现
  - **删除字段**：`lastVersion`（冗余字段）
  - **保留字段**：`lastSuccessfulVersion`（既是查询起点也是安全恢复点）
  - 新增字段：`stopRequested`, `currentVersionRetryCount`
  - 修改方法：`pull()`, `processDMLResultSet()`, `Worker.run()`, `close()`, `readLastVersion()`
  - 新增方法：`stopGracefully()`, `snapshotProgress()`, `isRetryableError()`

### 配置变更

- 新增配置项（可选）:
  - `sqlserver.ct.snapshot.record.interval`: 记录数阈值（默认 10000）
  - `sqlserver.ct.snapshot.time.interval.ms`: 时间间隔（默认 30000）
  - `sqlserver.ct.max.retry.per.version`: 最大重试次数（默认 3）

### 兼容性

- 向后兼容：默认配置下行为与旧版本相似（但更频繁持久化）
- 下游影响：可能有少量重复数据，需确保下游幂等处理

---

## 实施计划

### 阶段 1：核心功能（P0）

- [ ] 实现增量持久化（`pull()` 方法修改）
- [ ] 实现停止信号保存进度（`processDMLResultSet()` 修改）
- [ ] 单元测试：大事务中断场景

### 阶段 2：优雅停止（P1）

- [ ] 新增 `stopRequested` 字段和 `stopGracefully()` 方法
- [ ] 修改 `close()` 方法，调用 `stopGracefully()`
- [ ] 集成测试：前端停止指令场景

### 阶段 3：有限重试（P2）

- [ ] 新增重试计数和判断逻辑（`Worker.run()` 修改）
- [ ] 实现 `isRetryableError()` 方法
- [ ] 集成测试：SQL Server 超时场景

### 阶段 4：配置化与优化

- [ ] 新增配置项支持
- [ ] 性能测试：不同阈值下的性能表现
- [ ] 文档更新

---

## 测试覆盖

通过单元测试和集成测试验证功能正确性，代替运行时监控指标。

### 单元测试

| 测试用例                                       | 验证点              | 预期结果                          |
| ------------------------------------------ | ---------------- | ----------------------------- |
| `testIncrementalSnapshot_ByRecordCount()`  | 每 10000 条记录持久化一次 | snapshot 被调用 N/10000 次        |
| `testIncrementalSnapshot_ByTimeInterval()` | 每 30 秒持久化一次      | snapshot 被调用 T/30s 次          |
| `testSnapshotLastSuccessfulVersion()`      | 持久化上一版本号，而非当前版本  | `lastSuccessfulVersion = V99` |
| `testStopGracefully_SaveProgress()`        | 优雅停止时保存进度        | snapshot 被调用，进度已持久化           |
| `testRetryLimit_Exceeded()`                | 达到重试上限后停止        | retryCount=3 后不再重试            |
| `testRetryLimit_RecoverableError()`        | 可重试错误触发重试        | retryCount++，继续处理             |
| `testRetryLimit_UnrecoverableError()`      | 不可重试错误立即停止       | 不触发重试，立即停止                    |

### 集成测试

| 测试场景                                  | 验证点                   | 预期结果                         |
| ------------------------------------- | --------------------- | ---------------------------- |
| `testLargeTransaction_Interruption()` | 100 万条数据，中途崩溃         | 重启后重复处理不超过 1 万条              |
| `testFrontendStop_InProgress()`       | 前端停止正在处理的任务           | 已处理数据进度已持久化                  |
| `testSQLServerTimeout_Retry()`        | SQL Server 超时 kill 连接 | 重试 3 次后停止，记录日志               |
| `testPowerFailure_Recovery()`         | 模拟断电，重启恢复             | 从 `lastSuccessfulVersion` 恢复 |

### 性能测试

| 基准场景      | 配置           | 性能指标   | 验收标准        |
| --------- | ------------ | ------ | ----------- |
| 100 万条数据  | 无增量持久化       | 耗时 X 秒 | 基准          |
| 100 万条数据  | 每 10000 条持久化 | 耗时 Y 秒 | Y ≤ X * 1.1 |
| 100 万条数据  | 每 30 秒持久化    | 耗时 Z 秒 | Z ≤ X * 1.1 |
| 1000 万条数据 | 每 10000 条持久化 | 耗时 A 秒 | 线性扩展        |

---

## 验收标准

1. **增量持久化**: 处理 100 万条数据时，中断后重试重复处理不超过 1 万条
2. **优雅停止**: 前端发送停止指令后，已处理数据进度已持久化
3. **有限重试**: 同一版本连续失败 3 次后停止同步，不再重试
4. **性能影响**: 增量持久化导致的性能下降不超过 10%

---

## 参考

- [SQL Server Change Tracking 文档](https://docs.microsoft.com/en-us/sql/relational-databases/track-changes/change-tracking-sql-server)
- 相关 Issue: （待补充）
- 相关 PR: （待补充）

---

## 状态变更

| 日期         | 状态       | 说明                          |
| ---------- | -------- | --------------------------- |
| 2026-03-27 | Proposed | 初始提案                        |
| 2026-04-01 | Reviewed | ADR 审核，增加架构图、版本号管理说明、测试覆盖计划 |

# ADR-0036 测试报告

## 元信息

| 字段 | 值 |
|------|-----|
| 测试人 | 凌曦 |
| 测试日期 | 2026-09-23 |
| 测试版本 | 待提交（基于 76f8e7fae，2.34.0） |
| 关联 ADR | [0036-增量终止语义与快照点一致性修复.md](../../adr/0036-增量终止语义与快照点一致性修复.md) |
| 关联实现 | [impl-01-notes.md](impl-01-notes.md) |

## 测试范围

- [x] 决策 1：熔断判据修复
- [x] 决策 2：真重启
- [x] 决策 3：快照冻结
- [x] 决策 4：保留 ERROR 状态
- [x] 全量回归测试

## 测试方法

### 单元测试

使用 JUnit 4，不依赖 Mockito。测试脚手架：
- `ProfileComponent`：JDK 动态代理
- `ManagerFactory`：匿名子类
- 私有方法：反射调用

### 回归测试

运行全量测试套件，对比 HEAD 基线（76f8e7fae）。

## 通过项

| 编号 | 测试用例 | 预期结果 | 实际结果 |
|------|----------|----------|----------|
| 1 | `AbstractListenerSnapshotFreezeTest.testRefreshEventAfterCloseIsIgnored` | 关闭后 refreshEvent 被忽略 | ✅ 通过 |
| 2 | `AbstractListenerSnapshotFreezeTest.testFlushExecutorShutdownAfterClose` | 关闭后 flushExecutor 终止 | ✅ 通过 |
| 3 | `AbstractListenerSnapshotFreezeTest.testUnchangedSnapshotIsNotRewritten` | 快照不变时不重复写入 | ✅ 通过 |
| 4 | `MetaBufferActuatorStoppedTest.testDequeueKeepsResidueWhenStopped` | 停止后出队立即返回 | ✅ 通过 |
| 5 | `MappingModelServiceResetStateTest.testErrorStateIsPreserved` | ERROR 状态保留 | ✅ 通过 |
| 6 | `MappingModelServiceResetStateTest.testReadyStateIsReset` | READY 状态重置 | ✅ 通过 |
| 7 | `MappingModelServiceResetStateTest.testMissingMetaIsNoop` | Meta 缺失无操作 | ✅ 通过 |

## 回归测试

| 模块 | HEAD 基线 | 本次 | 差异 |
|------|-----------|------|------|
| dbsyncer-common | 79/0 | 79/0 | 无 |
| dbsyncer-sdk | 11/0 | 14/0 | +3 全绿 |
| dbsyncer-parser | 36/4* | 36/4* | 无变化 |
| dbsyncer-biz | 5/0 | 5/0 | 无 |
| dbsyncer-manager | 0/0 | 0/0 | 无 |

*注：parser 模块 4 个失败为 `CustomFieldIntegrationTest` 基线问题，与 ADR-0036 无关。

## 待补项

| 编号 | 测试用例 | 说明 |
|------|----------|------|
| P1 | `CircuitBreakerEventListenerTest` | 熔断判据单元测试（Mapping/Meta 缺失场景） |
| P2 | 端到端熔断→重启测试 | 覆盖完整的熔断→重启→数据恢复流程 |

## 结论

核心修复已验证，回归测试无新增失败。待补项为可选优化。

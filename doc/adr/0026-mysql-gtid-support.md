# ADR 26: MySQL 主备切换 GTID 支持方案

**状态**: Accepted  
**日期**: 2026-08-17  
**决策者**: @lxb
**前置 ADR**: 无

---

## 背景

### 问题场景

在 MySQL 主备切换（Failover）场景下，当前使用 binlog filename + position 的方案存在以下问题：

1. **binlog 文件名不一致**：主库和备库的 binlog 文件名可能不同
   ```
   主库：mysql-bin.000001, position=1234
   备库：mysql-bin.000005, position=5678

   主库故障 → 切换到备库
   ↓
   尝试从备库的 mysql-bin.000001:1234 开始同步
   ↓
   ❌ 备库报错：binlog 文件不存在
   ```

2. **时间戳定位不精确**：基于时间点的方案存在秒级精度问题，可能跳过或重复处理事件

3. **实现复杂度高**：需要额外的 binlog 解析和 position 映射逻辑

### GTID 方案核心优势

**GTID（Global Transaction Identifier）** 是 MySQL 5.6+ 引入的全局事务标识符：

- **全局唯一**：每个事务有唯一的 GTID，格式 `UUID:GTID_SET`
- **主备共享**：主备库共享同一个 GTID 集合，不受 binlog 文件名影响
- **精确恢复**：GTID 可以精确定位到事务级别，无需依赖 binlog 位置

### 适用版本

- MySQL 5.6+
- MySQL 5.7+
- MySQL 8.0+
- MariaDB 全系列（代码自动适配 GTID 格式差异）

---

### 特殊说明

主备切换非本项目职能，此项目仅通过编辑连接器来实现主备切换。

## 决策

### 1. 模式选择策略：互斥 + 自动切换

**原则**：GTID 和 fileName+position 互斥，通过观察 binlog 流自动选择。

**方案**：
1. 连接后观察 binlog 流中是否有 GTID 事件
2. 有 GTID 事件 → 使用 GTID 模式
3. 无 GTID 事件 → 使用 fileName+position 模式
4. 运行中持续累积 GTID set，**下次重连时**自动切换模式

**不进行任何服务端参数预检**（如 `gtid_mode`、`server_id`、`enforce_gtid_consistency` 等均不检查）。

### 2. 存量任务自动升级/降级

**方案**：存量任务在 fileName+position 模式下运行，观察流中是否有 GTID 事件。

| 场景 | 行为 |
|------|------|
| 流中有 GTID 事件 | 累积 GTID set，下次重连自动切换到 GTID 模式 |
| 流中无 GTID 事件 | 保持 fileName+position 模式 |

**零风险**：当前连接仍用 fileName+position，仅观察。只有重连时切换模式。

### 3. Snapshot 统一格式

**方案**：同时存储 `fileName`、`position`、`gtid` 三个字段，XID 事件时全部写入。

**格式**：
```json
{
  "fileName": "mysql-bin.000001",
  "position": 1234,
  "gtid": "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-23"
}
```

**写入时机**：仅在 XID 事件（事务提交）时写入。未提交/失败的事务不计入 snapshot，重连时自动重跑。

**关键规则**：每次写入时，如当前无 GTID（服务器不支持），`gtid` 字段**必须显式写为空字符串**，而非保留旧值。

```
// 正确做法：每次 XID 都写入三个字段
snapshot.put("fileName", binlogFilename);
snapshot.put("position", String.valueOf(nextPosition));
snapshot.put("gtid", gtidSet != null ? gtidSet.toString() : "");
```

**原因**：若 `gtid` 保留旧值，listener 重启后可能读到过期的 GTID 值，尝试用 GTID 模式连接不支持 GTID 的服务器，导致启动失败。写空字符串确保重启时自动降级到 fileName+position。

### 4. MariaDB 支持

**方案**：直接沿用 MySQL 同一套逻辑，代码自动适配。

**说明**：
1. `BinaryLogRemoteClient` 已自动检测数据库类型（`isMariaDB`）
2. 自动识别 MySQL GTID（`UUID:1-5`）和 MariaDB GTID（`0-1-1`）格式
3. 无需额外配置或校验

### 5. 错误恢复

**策略**：无 XID 不写入 snapshot，重连时重跑。

- 事务成功 → XID 事件 → 写入 snapshot → 重连时跳过 ✅
- 事务失败 → 无 XID → 不计入 snapshot → 重连时重跑 ✅

---

## 实现要点

### 代码变更清单

| 文件 | 变更 |
|------|------|
| `MySQLListener.java` | 添加 GTID 模式选择逻辑，修改 snapshot 写入逻辑 |
| `BinaryLogRemoteClient.java` | 始终启用 GTID 观察，累积 GTID 事件 |

### MySQLListener 变更

1. `run()` 方法：根据 snapshot 中 `gtid` 字段选择 GTID 模式或 fileName+position 模式
2. `refreshSnapshot()` 方法：增加 `gtid` 字段写入
3. 重连逻辑：优先使用 snapshot 中的 gtid 字段

### BinaryLogRemoteClient 变更

1. `openChannel()` 方法：始终初始化 GTID set（根据 `isMariaDB` 选择类型）
2. GTID 观察内置，无需开关，零开销

### 向后兼容

- 旧 snapshot 无 `gtid` 字段 → 自动降级为 fileName+position
- 用户可通过"重置位点"手动触发 GTID 模式

---

## 未实现的决策（待日后讨论）

- 无
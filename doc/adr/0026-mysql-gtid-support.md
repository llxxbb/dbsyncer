# ADR 26: MySQL 主备切换 GTID 支持方案

**状态**: Accepted  
**日期**: 2026-08-17  
**最后修订**: 2026-08-20（并入原 ADR-0027「GTID 集合服务端初始化」）  
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

---

## 修订背景（2026-08-20）：GTID 模式位点失效（错误码 1236）

### 问题现象

GTID 模式连接时报错：

```
com.github.shyiko.mysql.binlog.network.ServerException:
The slave is connecting using CHANGE MASTER TO MASTER_AUTO_POSITION = 1,
but the master has purged binary logs containing GTIDs that the slave requires.
```

错误码 1236，主库表示从库所需的 GTID 对应 binlog 已被清理。

### 根因分析

本 ADR 初版设计为「空集观察、下次重连切换」：

1. 连接时 `gtidSet` 初始化为**空集**
2. 从 binlog 流中观察并累积 GTID 事件（仅当前会话的 delta）
3. 第一个 XID 时将小 delta 集持久化到 snapshot
4. 任何重连/任务重启时，以该小集作为 GTID 模式输入

真实 MySQL 备库的 `gtid_executed` 覆盖完整历史，而 dbsyncer 的「观察集」只有会话 delta（如 `uuid:1103174135`）。主库据此判定需要补发其全部历史 GTID 中除这几笔外的所有事务——其中任何已 purge 的 binlog 即触发 1236。

### 故障链路放大

`ServerException extends IOException`，`listenForEventPackets` 的 `catch (IOException)` 将其当作网络错误 → `connectedError = true` → keepalive 用同样的小集无限重连 → 死循环。

---

## 决策

### 1. 模式选择策略：互斥 + 服务端初始化

**原则**：GTID 和 fileName+position 互斥；首次连接在服务端支持 GTID 时，直接以服务端完整 executed 集初始化。

**方案**：
1. 首次连接（snapshot 无 gtid 检查点）：从 `SHOW MASTER STATUS` / `SHOW BINARY LOG STATUS` 的 `Executed_Gtid_Set` 列初始化 `gtidSet` → GTID 模式从首次连接即可用
2. 已有 snapshot 检查点的连接：snapshot 的 gtid 值优先，不做服务端初始化
3. 运行中持续观察并累积 binlog 流中的 GTID 事件
4. GTID / fileName+position 互斥使用，重连时自动选择模式

**不进行任何服务端参数预检**（如 `gtid_mode`、`server_id`、`enforce_gtid_consistency` 等均不检查）；`Executed_Gtid_Set` 列取自状态查询（与 File/Position 同一行返回），不属于参数预检。

### 2. GTID 集合服务端初始化

**方案**：

- `openChannel()` 中，当 `gtidSet` 为空且 `binlogFilename` 未设置时，读取 `SHOW MASTER STATUS` / `SHOW BINARY LOG STATUS` 的 `Executed_Gtid_Set` 列（第 5 列，与 File/Position 同一行返回：原子快照，无额外往返、无 TOCTOU），作为初始化值
- 若 `binlogFilename` 已由 snapshot 设置（有 position 检查点但无 gtid 的任务重启场景），单独查询一次 `Executed_Gtid_Set` 初始化（不改写已有 position）
- **绝不覆盖**非空集合（snapshot 检查点的 gtid 优先）

**Fail-open 原则**：

- `Executed_Gtid_Set` 列不存在（旧版本）或为空（`gtid_mode=OFF`）→ 不初始化，维持 position 模式
- 解析失败（畸形值）→ warn 日志，不初始化，不阻断连接
- MariaDB 10.0+ 的 `SHOW MASTER STATUS` 同样返回 `Executed_Gtid_Set` 列；5.5 无该列 → 降级为 position 模式

### 3. 存量任务自动升级/降级

**方案**：存量任务在 fileName+position 模式下运行，观察流中是否有 GTID 事件。

| 场景 | 行为 |
|------|------|
| 流中有 GTID 事件 | 累积 GTID set，下次重连自动切换到 GTID 模式 |
| 流中无 GTID 事件 | 保持 fileName+position 模式 |
| 重启时仅 position 检查点（无 gtid） | 以服务端 `Executed_Gtid_Set` 初始化，GTID 模式直接可用 |

**零风险**：当前连接仍用 fileName+position，仅观察。只有重连时切换模式。

### 4. Snapshot 统一格式

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

### 5. MariaDB 支持

**方案**：直接沿用 MySQL 同一套逻辑，代码自动适配。

**说明**：
1. `BinaryLogRemoteClient` 已自动检测数据库类型（`isMariaDB`）
2. 自动识别 MySQL GTID（`UUID:1-5`）和 MariaDB GTID（`0-1-1`）格式
3. GTID 集合初始化查询同样按 `isMariaDB` 选择 `MariadbGtidSet` / `GtidSet` 解析
4. 无需额外配置或校验

### 6. 错误恢复

**策略**：无 XID 不写入 snapshot，重连时重跑。

- 事务成功 → XID 事件 → 写入 snapshot → 重连时跳过 ✅
- 事务失败 → 无 XID → 不计入 snapshot → 重连时重跑 ✅

**1236 错误分流**（1236 是服务端位点错误，不是网络故障，不应走网络重连逻辑）：

| 1236 子类型 | 处理方式 | 理由 |
|---|---|---|
| `purged binary logs`（GTID 位点过期） | 停止任务，`lifecycleListeners.onException` → `errorEvent + close()` | 用户显式决策全量重同步，不静默丢失中间数据 |
| `bogus data`（主库重启导致位点无效） | `resetBinlogPosition()` + 重连 | GTID 模式下天然免疫（GTID 跨主库重启）；position 模式下跳最新位点 |
| 其他 1236（file 不存在等） | 停止任务，`onException` | 默认 fatal，防无限循环 |
| 非 1236 IOException | 维持原网络重连逻辑 | 真正的网络故障 |

> 修正原因：`ServerException extends IOException`，初版实现中 1236 错误落入通用网络重连 catch，导致无限重连死循环；现于网络路径之前显式分流。

---

## 实现要点

### 代码变更清单

| 文件 | 变更 |
|------|------|
| `MySQLListener.java` | 添加 GTID 模式选择逻辑，修改 snapshot 写入逻辑 |
| `BinaryLogRemoteClient.java` | GTID 观察与累积；新增 `fetchExecutedGtidSet()` / `initGtidSetIfEmpty()` 服务端初始化；`openChannel()` 初始化逻辑；`fetchBinlogFilenameAndPosition()` 读取第 5 列；`listenForEventPackets` catch 块分流 1236 |
| `BinaryLogRemoteClientGtidInitTest.java` | 新增单元测试：空集初始化、非空集保护、fail-open、1236 子类型检测 |

### MySQLListener 变更

1. `run()` 方法：根据 snapshot 中 `gtid` 字段选择 GTID 模式或 fileName+position 模式
2. `refreshSnapshot()` 方法：增加 `gtid` 字段写入
3. 重连逻辑：优先使用 snapshot 中的 gtid 字段

### BinaryLogRemoteClient 变更

1. `openChannel()` 方法：`gtidSet` 为空时以服务端 `Executed_Gtid_Set` 初始化（根据 `isMariaDB` 选择 `MariadbGtidSet` / `GtidSet`）
2. `fetchBinlogFilenameAndPosition()` 方法：读取第 5 列 `Executed_Gtid_Set`，空集时初始化
3. `listenForEventPackets()` 方法：1236 子类型分流处理
4. GTID 观察内置，无需开关，零开销

### 向后兼容

- 旧 snapshot 无 `gtid` 字段 → 自动降级为 fileName+position；重启时以服务端 `Executed_Gtid_Set` 初始化
- 旧 snapshot 含小 gtid 集（修复前的会话 delta 累积）：重启后首次重连触发 1236-purge → 任务停止并报错，用户按提示处理（重新同步）
- 非 GTID 服务器（`gtid_mode=OFF`）：行为不变（position 模式）
- 用户可通过"重置位点"手动触发 GTID 模式

### 混合任务

增量从全量开始位点回放 delta：`gtidSet` 以增量启动时刻的 executed 集初始化，position 流照常回放全量期 delta 事件，集合保持完整。若该位点 binlog 已被 purge → 1236 走对应分支，不再无限循环。

### 7. 混合模式 GTID 初始化修复（2026 修订）

**背景**：`MySQLConnector.getPosition()` 原先使用 JDBC 查询 `SHOW MASTER STATUS`，仅返回 `File` 和 `Position`，缺少 `Executed_Gtid_Set`。混合模式下 `recordIncrementStartPoint()` 调用该方法时，snapshot 中无 gtid 字段，导致增量启动时无法直接使用 GTID 模式，需依赖 `BinaryLogRemoteClient.openChannel()` 中的服务端初始化逻辑补充。

**问题**：
1. 快照数据缺少 GTID，混合模式任务启动后需额外查询补充
2. `BinaryLogRemoteClient` 内部有两处分散的 `SHOW MASTER STATUS` 查询（`fetchBinlogFilenameAndPosition` 和 `fetchExecutedGtidSet`）
3. MySQL 8.4 起 `SHOW MASTER STATUS` 已移除，JDBC 查询未处理版本差异

**方案**：
1. `MySQLConnector.getPosition()` 保持 JDBC 轻量查询，补上 `Executed_Gtid_Set` 列，返回完整位点 `{fileName, position, gtid}`
2. 合并 `BinaryLogRemoteClient` 的 `fetchBinlogFilenameAndPosition()` 和 `fetchExecutedGtidSet()` 为统一的 `fetchServerStatus()` 方法
3. 简化 `openChannel()`：直接调用 `fetchServerStatus()`，智能更新（仅在未设置时写入，不覆盖 snapshot 检查点）
4. 两处查询均处理 MySQL 8.4 的 `SHOW BINARY LOG STATUS` 语法

**职责划分**：
- `MySQLConnector.getPosition()`：JDBC 轻量查询，用于快照获取（`recordIncrementStartPoint`）
- `BinaryLogRemoteClient.fetchServerStatus()`：binlog 协议，增量同步内部统一查询

**调用链**：
```
recordIncrementStartPoint()
  └── MySQLConnector.getPosition()
        └── JDBC: SHOW MASTER STATUS / SHOW BINARY LOG STATUS
        └── 返回 {fileName, position, gtid}
  └── meta.recordIncrementStartPoint() → snapshot

MySQLListener.run()
  └── 读 snapshot → client.setGtidSet() / setBinlogFilename()
  └── client.connect()
        └── openChannel() → fetchServerStatus()（仅存量任务补充查询）
```

**优势**：
- JDBC 轻量查询获取快照，不启动 binlog 消费
- 增量同步内部统一为 `fetchServerStatus()`，删除重复逻辑
- 两处查询均处理 MySQL 8.4 语法差异

---

## 未实现的决策（待日后讨论）

- 无

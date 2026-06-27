# ADR-0019 Model 层贫血化 — 详细验证方案

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | v1.1 |
| 作者 | @opencode |
| 日期 | 2026-06-27 |
| 关联 ADR | [ADR-0019](../../adr/0019-Model层贫血化重构.md) |

---

## 一、需转移的调用点全景图

### 1.1 Meta.java — ProfileComponent 依赖（8 个方法，11 个调用点）

| # | 方法 | 调用类型 | 调用点 | 承接 Service |
|---|------|--------|--------|-------------|
| 1 | `Meta.clear()` | 持久化 | L99: `editConfigModel(this)` | MetaService.clear() |
| 2 | `Meta.partialClear()` | 查询 | L123: `getTableGroupAll(mappingId)` | MetaService.partialClear() |
| 3 | `Meta.partialClear()` | 持久化 | L141: `editConfigModel(this)` | MetaService.partialClear() |
| 4 | `Meta.updateFullTotal()` | 查询 | L202: `getTableGroupAll(mappingId)` | MetaService.updateFullTotal() |
| 5 | `Meta.updateFullTotal()` | 持久化 | L210: `editConfigModel(this)` | MetaService.updateFullTotal() |
| 6 | `Meta.updateSnapshot()` | 持久化 | L248: `editConfigModel(this)` | MetaService.updateSnapshot() |
| 7 | `Meta.updateSyncPhase()` | 持久化 | L299: `editConfigModel(this)` | MetaService.updateSyncPhase() |
| 8 | `Meta.recordIncrementStartPoint()` | 持久化 | L324: `editConfigModel(this)` | MetaService.recordIncrementStartPoint() |
| 9 | `Meta.create()` | 构造器 | L414: `new Meta(profileComponent)` | MetaService.create() |
| 10 | `Meta.create()` | 持久化 | L422: `addConfigModel(meta)` | MetaService.create() |
| 11 | `Meta.create()` | 持久化 | L423: `editConfigModel(mapping)` | MetaService.create() |

### 1.2 Mapping.java — ProfileComponent 依赖（3 个方法，3 个调用点）

| # | 方法 | 调用类型 | 调用点 | 承接 Service |
|---|------|--------|--------|-------------|
| 12 | `Mapping.getMeta()` | 查询 | L209: `getMeta(this.metaId)` | MappingService.getMeta() |
| 13 | `Mapping.resetMetaState()` | 查询+持久化 | L214: `getMeta/ editConfig` | MappingService.resetMetaState() |
| 14 | `Mapping.updateMata()` | 查询+持久化 | L220-222: `getMeta/ editConfig` | MappingService.updateMata() |

### 1.3 TableGroup.java — ProfileComponent + ParserComponent 依赖（5 个方法，8 个调用点）

| # | 方法 | 组件 | 调用类型 | 调用点 | 承接 Service |
|---|------|------|---------|--------|-------------|
| 15 | `TableGroup.initTableGroup()` | ProfileComponent | 赋值 | L400: `this.profileComponent=` | TableGroupService.init() |
| 16 | `TableGroup.initTableGroup()` | ProfileComponent | 赋值 | L399: `this.parserComponent=` | TableGroupService.init() |
| 17 | `TableGroup.initTableGroup()` | ProfileComponent | 查询 | L401: `getMapping(mappingId)` | TableGroupService.init() 参数传入 |
| 18 | `TableGroup.initTableGroup()` | ProfileComponent | 持久化 | L406: `editConfigModel(this)` | TableGroupService.init() |
| 19 | `TableGroup.initCommand()` | ProfileComponent | 查询 | L413-414: `getConnector(connectorId)` | TableGroupService.initCommand() 参数传入 |
| 20 | `TableGroup.create()` | 静态工厂 | 持久化 | L639-640: profileComponent/parserComponent 注入 | TableGroupService.create() |
| 21 | `TableGroup.create()` | ProfileComponent | 持久化 | L654: `addTableGroup(newTableGroup)` | TableGroupService.create() |
| 22 | `TableGroup.migrateVersion()` | Profile/Parser | 查询+持久化 | L668-678: `getMapping/getConnector/getMetaInfo/ editTableGroup` | TableGroupService.migrateVersion() |

### 1.4 Summary

| 调用类型 | 总次数 | ProfileComponent | ParserComponent |
|---------|--------|-----------------|-----------------|
| **editConfigModel（持久化）** | 11 | 11 | 0 |
| **addConfigModel/addTableGroup（新增）** | 3 | 3 | 0 |
| **getXxx（查询）** | 7 | 6 | 1 |
| **构造器/静态工厂注入** | 3 | 3 | 3 |
| **总计** | **24** | **23** | **4** |

---

## 二、验证方案设计

### 2.1 验证标准总览

```
验证标准分为 4 层：
  Level 1: 静态检查 — 编译通过、无遗漏调用点
  Level 2: 单元测试 — 每个转移方法独立验证
  Level 3: 集成测试 — 端到端调用链完整
  Level 4: 回归测试 — 系统整体行为不变
```

---

### Level 1: 静态检查（编译期）

#### 检查项 1.1：Model 中不再包含组件引用

```bash
# Meta.java 不应再有 profileComponent 字段
grep -n "profileComponent\|parserComponent" \
    dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Meta.java

# Mapping.java 不应再有 profileComponent 字段
grep -n "profileComponent" \
    dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Mapping.java

# TableGroup.java 不应再有 profileComponent/parserComponent 字段
grep -n "profileComponent\|parserComponent" \
    dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/TableGroup.java
```

**预期**：全部为空

#### 检查项 1.2：Service 层承接方法存在

| 承接 Service | 方法 | 验证标准 |
|-------------|------|---------|
| `MetaService` | `clear(Meta)` | 存在且功能等价 |
| `MetaService` | `partialClear(Meta, Set<String>)` | 存在且功能等价 |
| `MetaService` | `updateFullTotal(Meta)` | 存在且功能等价 |
| `MetaService` | `updateSnapshot(Meta, Map)` | 存在且功能等价 |
| `MetaService` | `updateSyncPhase(Meta, SyncPhaseEnum)` | 存在且功能等价 |
| `MetaService` | `recordIncrementStartPoint(Meta, Map)` | 存在且功能等价 |
| `MetaService` | `create(Mapping)` | 存在且功能等价 |
| `MappingService` | `getMeta(Mapping)` | 存在 |
| `MappingService` | `resetMetaState(Mapping)` | 存在且功能等价 |
| `MappingService` | `updateMata(Mapping)` | 存在且功能等价 |
| `TableGroupService` | `init(TableGroup, ParserComponent, ProfileComponent)` | 存在（参数注入） |
| `TableGroupService` | `initCommand(TableGroup, ProfileComponent)` | 存在 |
| `TableGroupService` | `create(String, String, ...)` | 存在 |
| `TableGroupService` | `migrateVersion(TableGroup, ...)` | 存在 |

#### 检查项 1.3：所有调用方已切换到新方法

```bash
# 检查所有直接调用 Meta.profileComponent 的地方
grep -rn "meta\.clear()\|meta\.partialClear()\|meta\.updateFullTotal()" \
    --include="*.java" dbsyncer-parser/src/main/java/

# 预期：只存在通过 Service 的调用，不存在 Model 的直接调用
```

---

### Level 2: 单元测试（方法级）

#### 2.1 Meta 方法测试矩阵

| # | 测试方法 | 测试目标 | 验证点 |
|---|---------|---------|--------|
| UT-1 | `testMetaClear()` | MetaService.clear() | 1. Meta 状态重置 2. 持久层数据已更新 3. Meta.profileComponent == null |
| UT-2 | `testMetaPartialClear()` | MetaService.partialClear() | 1. 指定表组的状态被清理 2. 其他表组不受影响 3. Meta 已持久化 |
| UT-3 | `testMetaUpdateFullTotal()` | MetaService.updateFullTotal() | 1. 正确统计所有表组数据条数 2. Meta.total 已更新 3. update_time 已更新 |
| UT-4 | `testMetaUpdateSnapshot()` | MetaService.updateSnapshot() | 1. snapshot 内容正确 2. Meta 已持久化 |
| UT-5 | `testMetaUpdateSyncPhase()` | MetaService.updateSyncPhase() | 1. syncPhase 正确切换 2. 持久化生效 |
| UT-6 | `testMetaRecordIncrementStartPoint()` | MetaService.recordIncrementStartPoint() | 1. position 记录到 snapshot 2. isIncrementStartPointRecorded = true |
| UT-7 | `testMetaCreate()` | MetaService.create() | 1. Meta 对象创建 2. metaId 生成 3. profileComponent.addConfigModel() 被调用 |

#### 2.2 Mapping 方法测试矩阵

| # | 测试方法 | 测试目标 | 验证点 |
|---|---------|---------|--------|
| UT-8 | `testMappingGetMeta()` | MappingService.getMeta() | 1. 返回正确的 Meta 对象 2. metaId 匹配 |
| UT-9 | `testMappingResetMetaState()` | MappingService.resetMetaState() | 1. Meta 状态重置 2. Mapping 已持久化 |
| UT-10 | `testMappingUpdateMata()` | MappingService.updateMata() | 1. Meta 属性更新 2. Mapping 已持久化 |

#### 2.3 TableGroup 方法测试矩阵

| # | 测试方法 | 测试目标 | 验证点 |
|---|---------|---------|--------|
| UT-11 | `testTableGroupInit()` | TableGroupService.init() | 1. parserComponent/profileComponent 正确注入 2. Meta 查询正确 3. 持久化已调用 |
| UT-12 | `testTableGroupInitCommand()` | TableGroupService.initCommand() | 1. 连接器配置正确获取 2. syncRows 正确构建 |
| UT-13 | `testTableGroupCreate()` | TableGroupService.create() | 1. TableGroup 创建 2. addTableGroup() 已调用 3. migrateVersion() 触发正确 |
| UT-14 | `testTableGroupMigrateVersion()` | TableGroupService.migrateVersion() | 1. 版本迁移逻辑正确 2. 主键从源表继承 3. editTableGroup() 已调用 |

---

### Level 3: 集成测试（调用链级）

验证每个调用链从 Controller → Service → Model 的完整性：

#### 3.1 核心业务流程测试

| # | 集成测试名称 | 调用链 | 验证点 |
|---|------------|--------|--------|
| IT-1 | **创建 Mapping** | Controller.createMapping() → MappingService.create() → Mapping → MetaService.create() | 1. Mapping 持久化 2. Meta 持久化 3. ID 关联正确 |
| IT-2 | **启停任务** | Controller.startTask() → MetaService.updateSyncPhase(Meta, START) → Meta | 1. syncPhase 状态正确 2. listener 启动 3. Meta 持久化 |
| IT-3 | **全量同步** | ParserComponentImpl.executeTableGroup() → MetaService.updateFullTotal() → Meta | 1. total 正确统计 2. Meta 持久化 |
| IT-4 | **增量同步** | MetaBufferActuator → MetaService.recordIncrementStartPoint() → Meta | 1. offset 正确记录 2. Meta 持久化 |
| IT-5 | **任务重置** | Controller.resetTask() → MetaService.clear() → Meta | 1. Meta 状态清零 2. 表组状态清零 3. 持久化正确 |
| IT-6 | **字段映射编辑** | Controller.editFieldMapping() → TableGroupService.edit() → TableGroup | 1. fieldMapping 更新 2. 持久化正确 |
| IT-7 | **Connector 写入** | MetaBufferActuator → ConnectorFactory.writeBatch() → SyncRow → Connector.insert() | 1. SyncRow 包含 mapping/tableGroup 2. Connector 正确访问配置 |
| IT-8 | **任务迁移版本** | System bootstrap → TableGroupService.migrateVersion() → TableGroup | 1. 旧表组兼容 2. 主键正确继承 |

#### 3.2 调用方变更追踪

```bash
# 原调用方
grep -rn "meta\.clear()\|meta\.partialClear()\|meta\.updateSnapshot()" \
    --include="*.java" dbsyncer-parser/ dbsyncer-biz/ dbsyncer-web/
# 预期：全部改为通过 Service 调用
```

---

### Level 4: 回归测试（系统级）

| # | 回归测试场景 | 覆盖业务 | 验收标准 |
|---|-------------|---------|---------|
| RT-1 | MySQL → MySQL 全量迁移 | Meta.total/success/fail 统计 | 1. 数据条数一致 2. success 计数正确 3. Meta 状态为 COMPLETED |
| RT-2 | MySQL → MySQL 增量实时同步 | Meta.incrementStartPointRecorded | 1. 增量 offset 正确记录 2. 中断恢复数据一致 |
| RT-3 | MySQL → Kafka 数据同步 | SyncRow 含 mapping/tableGroup | 1. Kafka 连接器正确读取配置 2. 数据写入正确 |
| RT-4 | SQL Server CT 大事务同步 | Meta.updateSnapshot() | 1. snapshot 正确保存事务状态 2. 断点续传 3. 数据完整 |
| RT-5 | 表组主键变更 | TableGroup.migrateVersion() | 1. 旧版本表组兼容 2. 主键正确继承 3. 同步不报错 |
| RT-6 | 重置任务后重启 | Meta.partialClear() | 1. 指定表组清零 2. 其他表组不受影响 3. 任务可正常重启 |
| RT-7 | 字段差异检测与修复 | TableGroup.initCommand() | 1. 连接器配置正确 2. syncRows 正确构建 |
| RT-8 | 并发任务执行 | Meta 并发更新安全 | 1. 多个 task 并发执行 2. Meta 数据不串 3. 计数准确 |

---

## 三、验收 Gate

### 3.1 通过标准（全部满足才能合并）

| Gate | 检查项 | 通过条件 |
|------|--------|---------|
| **G1** | 编译通过 | `mvn clean compile` exit 0 |
| **G2** | Level 1 静态检查 | Model 层无 profileComponent/parserComponent 引用 |
| **G3** | Level 2 单元测试 | 全部 14 个新测试通过，覆盖率 > 90% |
| **G4** | Level 3 集成测试 | 全部 8 个端到端场景通过 |
| **G5** | Level 4 回归测试 | 全部 8 个场景通过，数据一致性 100% |
| **G6** | 无功能回退 | 所有现有 integration test 通过 |
| **G7** | 性能无退化 | TPS 波动 < 10%，内存占用 < 10% |

### 3.2 关键检查脚本

```bash
#!/bin/bash
# adr-0019-validation.sh - 一键验证脚本

set -e

echo "=== G1: 编译检查 ==="
mvn clean compile -DskipTests -q

echo "=== G2: Model 层静态检查 ==="
echo "检查 Meta.java..."
if grep -q "profileComponent\|parserComponent" \
    dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Meta.java; then
    echo "❌ Meta.java 仍有组件依赖！"
    exit 1
fi

echo "检查 Mapping.java..."
if grep -q "profileComponent" \
    dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Mapping.java; then
    echo "❌ Mapping.java 仍有组件依赖！"
    exit 1
fi

echo "检查 TableGroup.java"
if grep -q "profileComponent\|parserComponent" \
    dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/TableGroup.java; then
    echo "❌ TableGroup.java 仍有组件依赖！"
    exit 1
fi

echo "=== G3-G5: 测试执行 ==="
mvn test -pl dbsyncer-parser

echo "=== G6-G7: 集成测试 ==="
mvn verify

echo "✅ 所有检查通过！"
```

---

## 四、风险预案

| 风险 | 影响 | 预案 |
|------|------|------|
| Service 层漏接某方法 | 编译失败 | G2 静态检查发现 |
| 调用方未完全切换 | 运行时 NPE | G4 集成测试发现 |
| 并发安全性问题 | 生产环境数据错乱 | G5 并发场景测试覆盖 |
| 性能退化 | TPS 下降 | G7 性能对比验证 |
| 字段映射逻辑变化 | 数据同步错误 | RT-7 字段差异测试 |

---

## 五、变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-06-27 | v1.0 | 初始版本 | @opencode |
| 2026-06-27 | v1.1 | 补充调用点全景图、4 层验证标准 | @opencode |

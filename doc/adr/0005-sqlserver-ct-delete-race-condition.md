# ADR 05: SQL Server CT 数据删除竞态问题解决方案

## 状态

**已接受** (Accepted)

**日期**: 2026-04-24

**最后更新**: 2026-04-26

---

## 问题背景

在 SQL Server CT（Change Tracking，变更追踪）数据库同步场景中，当前批次的 insert 或 update 可能会失效（抛异常：null 值），其原因是该记录在 sql server 端后续处理中删除了（会体现在后续批次中），导致 left join 源表数据为 null。

## 核心方案：异常捕获 + 批次过滤 + 重做

**流程**：
```
1. 执行 SQL（零检测）
   └─ 有失败 → 抛异常
   ↓
2. 异常捕获 → CT 删除检测
   ├─ CT 删除（非主键全 null）→ 视为成功，记录日志
   └─ 其他异常 → 需要重试
   ↓
3. 重试其他异常数据（最多 3 次）
   ├─ 成功 → 加入成功数据
   └─ 再次失败 → 递归重试（直到 3 次）
   ↓
4. 返回结果（成功数据 + 失败数据）
```

**优点**：
- ✅ 零检测：正常流程不检测
- ✅ 零查询：不需要额外数据库查询
- ✅ 实时性高：批次内完成
- ✅ 业务逻辑正确：CT 删除视为成功，其他异常重试
- ✅ 确保重试：其他异常数据一定会被重试（最多 3 次）

---

## 核心实现

### 判定条件

| 场景 | 非主键字段值 | null 数量 | 处理方式 |
|------|------------|---------|---------|
| INSERT/UPDATE | 有值 | 0 | 正常 |
| DELETE | 全 null | 非主键数量 | 移除 + 成功数包含 |
| 其他异常 | 部分 null | < 非主键数量 | 重试 |

### 核心代码（通用架构）

**父类 `AbstractDatabaseConnector` 提供通用异常处理**：

```java
/**
 * 通用写入异常处理方法（适用于所有 Connector：SQL Server、MySQL、PostgreSQL、Oracle 等）
 */
protected Result handleWriteException(PluginContext context, List<Map> targetData, List<Field> fields, Exception e) {
    Result result = new Result();
    
    // CT 删除检测：区分 CT 删除和其他异常
    List<Map> ctDeleteData = new ArrayList<>();
    List<Map> otherFailData = new ArrayList<>();
    
    for (Map dataItem : targetData) {
        if (CtDeleteDetector.isDeletedFromCT(dataItem, fields)) {
            ctDeleteData.add(dataItem);  // 非主键字段全为 null → CT 删除
        } else {
            otherFailData.add(dataItem);  // 其他异常
        }
    }
    
    // CT 删除数据 → 视为成功
    if (!ctDeleteData.isEmpty()) {
        logger.info("[CT 删除] 表{}，操作{}，{}条数据已被物理删除，视为成功", ...);
        if (staticLogService != null) {
            for (Map ctData : ctDeleteData) {
                staticLogService.log(LogType.MappingLog.CONFIG, ...);
            }
        }
        result.addSuccessData(ctDeleteData);
    }
    
    // 其他异常数据 → 视为失败，需要重试
    if (!otherFailData.isEmpty()) {
        logger.warn("[其他异常] 表{}，操作{}，{}条数据失败，需要重试", ...);
        if (staticLogService != null) {
            for (Map otherData : otherFailData) {
                staticLogService.log(LogType.MappingLog.CONFIG, ...);
            }
        }
        result.error = e.getMessage();
        result.addFailData(otherFailData);
    }
    
    return result;
}
```

**子类 Connector 复用（以 SqlServerConnector 为例）**：

```java
@Override
public Result upsert(DatabaseConnectorInstance connectorInstance, PluginContext context) {
    return executeBulkOperation(connectorInstance, context,
            (connection, schemaName, enableIdentityInsert) ->
                    bulkUpsert(connection, context, ...));
}

private Result executeBulkOperation(...) {
    try {
        return bulkOperation.execute(...);
    } catch (Exception e) {
        // 复用父类通用异常处理方法
        return handleWriteException(context, context.getTargetList(), context.getTargetFields(), e);
    }
}
```

**架构优势**：
- ✅ **一次实现，所有 Connector 复用**：MySQL、PostgreSQL、Oracle、SQLite 自动继承
- ✅ **符合开闭原则**：新增 Connector 无需修改 CT 删除逻辑
- ✅ **符合依赖倒置**：CT 检测逻辑在 SDK 层，不依赖具体 Connector 实现

---

## 实施要点

**必须实现**：
1. ✅ 零检测：正常流程直接执行
2. ✅ 异常捕获：所有失败都会抛异常
3. ✅ 内存检测：`null 数量 == 非主键数量`
4. ✅ 批次过滤：CT 删除数据从批次移除，其他异常数据重试
5. ✅ 批次重做：其他异常数据重新执行
6. ✅ 全 CT 删除：execute 为 null，成功数应包含全部数据
7. ✅ 持久化日志：使用 LogService 记录 CT 删除和重试数据

**实现方式**：
- 复用现有异常机制，无需特殊异常定义
- 核心是处理逻辑，不是异常类型
- **CT 删除数据移除，其他异常重试**
- LogService 通过方法参数直接传递
- 日志类型：`LogType.MappingLog.CONFIG`

**关键**：批次过滤在**异常捕获**中执行，返回重做结果的 execute[]

---

## LogService 架构设计

### 模块依赖关系

```
dbsyncer-common (基础层)
    ↓
dbsyncer-sdk (SDK 层 - 定义 LogService 接口)
    ↓
dbsyncer-connector-* (连接器实现 - 使用 LogService)
    ↓
dbsyncer-parser (解析层 - 实现 LogServiceImpl)
    ↓
dbsyncer-storage (存储层 - LogServiceImpl 依赖)
```

### LogService 传递流程（最终方案：全局单例）

```java
// 1. MetaBufferActuator 初始化时设置全局 LogService
@PostConstruct
public void init() {
    if (null != logService) {
        AbstractDatabaseConnector.setStaticLogService(logService);
    }
}

// 2. AbstractDatabaseConnector 使用静态字段
protected static LogService staticLogService;

// 3. handleCtDeleteScenario 直接使用 staticLogService
private int[] handleCtDeleteScenario(...) {
    if (null != staticLogService) {
        staticLogService.log(LogType.MappingLog.CONFIG, "...");
    }
}
```

### 为什么这样设计？

**问题**：LogService 是 Spring 单例 Bean，每次调用都传递太繁琐。

**解决方案**：全局静态字段，一次初始化，长期使用。

**优点**：
1. ✅ 符合 Spring 单例模式
2. ✅ 代码更简洁（无需每次传递参数）
3. ✅ 性能更好（无需重复设置）
4. ✅ 生命周期清晰（应用启动时设置一次）
5. ✅ 依赖方向正确（sdk 定义接口，parser 实现）
6. ✅ 避免循环依赖（connector-base 不依赖 parser）
7. ✅ 符合 SOLID 原则（依赖倒置）

**兼容层**：
- `org.dbsyncer.parser.LogType` 保留作为兼容层（@Deprecated）
- 继承 `org.dbsyncer.sdk.spi.LogType`，确保旧代码可用

---

## 提交记录

| 提交哈希 | 说明 |
|---------|------|
| `a22b20ec` | refactor: LogService 全局单例，一次初始化长期使用 |
| `d0566f9b` | refactor: 移动 LogService 和 LogType 到 dbsyncer-sdk 模块 |
| `ad5f3b12` | docs: 更新 ADR 05 文档，记录 LogService 架构重构 |
| `待提交 -1` | refactor: 提取通用 `handleWriteException` 方法，所有 Connector 复用 CT 删除处理逻辑 |
| `待提交 -2` | fix: SqlServerConnector 确保其他异常数据重试（最多 3 次） |

---

*最后更新：2026-04-27*

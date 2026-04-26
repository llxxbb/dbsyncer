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
2. 异常捕获 → 批次过滤
   ├─ CT 删除（整行 null）→ 从批次移除（成功数包含）
   └─ 其他异常 → 重试
   ↓
3. 重试失败 → 抛出异常
```

**优点**：
- 零检测：正常流程不检测
- 零查询：不需要额外数据库查询
- 实时性高：批次内完成
- 业务逻辑正确：CT 删除数据移除，其他异常重试

---

## 核心实现

### 判定条件

| 场景 | 非主键字段值 | null 数量 | 处理方式 |
|------|------------|---------|---------|
| INSERT/UPDATE | 有值 | 0 | 正常 |
| DELETE | 全 null | 非主键数量 | 移除 + 成功数包含 |
| 其他异常 | 部分 null | < 非主键数量 | 重试 |

### 核心代码

```java
private Result executeWriter(...) {
    try {
        // 1. 执行 SQL（零检测）
        int[] execute = connectorInstance.execute(...);
        
    } catch (Exception e) {
        // 2. 异常捕获 → 批次过滤 + CT 删除场景处理 + 重做
        execute = handleCtDeleteScenario(result, data, fields, connectorInstance, executeSql, context, logService, e);
    }
    
    // 3. 统一计数（复用计数逻辑）
    if (null != execute) {
        for (...) {
            if (execute[i] == 1 || execute[i] == -2) {
                result.getSuccessData().add(data.get(i));
            }
        }
    }
    // 全 CT 删除 → execute 为 null，成功数应包含全部数据（待后续修正）
    
    return result;
}

/**
 * 处理 CT 删除场景：批次过滤 + 重做（其他异常）
 * @param logService 日志服务（持久化）
 * @return 重做结果的 execute[]，null 表示全 CT 删除（全部移除，成功数应包含）
 */
private int[] handleCtDeleteScenario(Result result, List<Map> data, List<Field> fields,
                                    DatabaseConnectorInstance connectorInstance, String executeSql, 
                                    PluginContext context, LogService logService, Exception e) {
    // 区分 CT 删除和其他异常
    List<Map> ctDeleteData = new ArrayList<>();
    List<Map> otherFailData = new ArrayList<>();
    
    for (Map dataItem : data) {
        if (CtDeleteDetector.isDeletedFromCT(dataItem, fields)) {
            ctDeleteData.add(dataItem);
        } else {
            otherFailData.add(dataItem);
        }
    }
    
    // 全 CT 删除 → 返回 null（全部移除，成功数应包含）
    if (data.size() == ctDeleteData.size()) {
        logger.info("[全 CT 删除] 表{}，全部数据已被物理删除，从批次移除", ...);
        
        // 持久化记录 CT 删除数据
        if (null != logService) {
            for (Map ctData : ctDeleteData) {
                logService.log(LogType.MappingLog.CONFIG, 
                    String.format("[CT 删除] 表{}，操作{}，数据：%s", ...));
            }
        }
        
        return null;
    }
    
    // CT 删除的数据 → 从批次移除
    // 其他异常的数据 → 批次重做
    if (!otherFailData.isEmpty()) {
        logger.warn("[重试] 表{}，操作{}，其他失败数据{}条，需要重试", ...);
        
        // 持久化记录重试数据
        if (null != logService) {
            for (Map otherData : otherFailData) {
                logService.log(LogType.MappingLog.CONFIG,
                    String.format("[重试] 表{}，操作{}，数据：%s", ...));
            }
        }
        
        try {
            return connectorInstance.execute(...);
        } catch (Exception reMakeE) {
            throw new RuntimeException("重试失败：" + reMakeE.getMessage(), reMakeE);
        }
    }
    
    // 只有 CT 删除，无其他异常 → 全部移除
    return null;
}
```

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

---

*最后更新：2026-04-26*

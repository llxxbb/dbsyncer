# ADR 05: SQL Server CT 数据删除竞态问题解决方案

## 状态

**已接受** (Accepted)

**日期**: 2026-04-24

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
   ├─ CT 删除（整行 null）→ 批次重做
   └─ 其他异常（部分字段异常）→ 抛出异常
   ↓
3. 批次重做后仍失败 → 抛出异常
```

**优点**：
- 零检测：正常流程不检测
- 零查询：不需要额外数据库查询
- 实时性高：批次内完成
- 业务逻辑正确：CT 删除重做，其他异常终止

---

## 核心实现

### 判定条件

| 场景 | 非主键字段值 | null 数量 | 处理方式 |
|------|------------|---------|---------|
| INSERT/UPDATE | 有值 | 0 | 正常 |
| DELETE | 全 null | 非主键数量 | 移除 + 批次重做 |
| 其他异常 | 部分 null | < 非主键数量 | 抛出异常 |

### 核心代码

```java
private Result executeWriter(...) {
    try {
        // 1. 执行 SQL（零检测）
        int[] execute = connectorInstance.execute(...);
        
        // 2. 处理执行结果
        if (null != execute) {
            for (...) {
                if (execute[i] == 1 || execute[i] == -2) {
                    result.getSuccessData().add(data.get(i));
                }
            }
        }
        
    } catch (Exception e) {
        // 3. 异常捕获 → 批次过滤 + CT 删除场景处理
        handleCtDeleteScenario(result, data, fields, connectorInstance, executeSql, context, e);
    }
    
    return result;
}

/**
 * 处理 CT 删除场景：批次过滤 + 重做
 */
private void handleCtDeleteScenario(Result result, List<Map> data, List<Field> fields,
                                    DatabaseConnectorInstance connectorInstance, String executeSql, 
                                    PluginContext context, Exception e) {
    // 区分 CT 删除和其他异常
    List<Map> ctDeleteData = new ArrayList<>();
    List<Map> otherFailData = new ArrayList<>();
    
    for (Map dataItem : data) {
        if (CtDeleteDetector.isDeletedFromCT(dataItem, fields)) {
            ctDeleteData.add(dataItem);
            logger.warn("[CT 删除] 记录 {} 已被物理删除，需要批次重做", getPkValues(dataItem));
        } else {
            otherFailData.add(dataItem);
        }
    }
    
    // 全 CT 删除 → 返回成功
    if (data.size() == ctDeleteData.size()) {
        logger.info("[全 CT 删除] 表{}，全部数据已被物理删除", context.getTargetTableName());
        return;
    }
    
    // CT 删除 → 批次重做
    if (!ctDeleteData.isEmpty()) {
        int retryCount = context.getRetryCount();
        if (retryCount >= 3) {
            throw new RuntimeException("批次重做达到上限（3 次）");
        }
        
        try {
            context.setRetryCount(retryCount + 1);
            int[] reMake = connectorInstance.execute(...);
            // 处理重做结果
        } catch (Exception reMakeE) {
            throw new RuntimeException("批次重做失败：" + reMakeE.getMessage(), reMakeE);
        }
    }
    
    // 其他异常 → 抛出异常
    if (!otherFailData.isEmpty()) {
        throw new RuntimeException("写入失败：部分数据异常 - " + otherFailData.size() + "条", e);
    }
}
```

---

## 实施要点

**必须实现**：
1. ✅ 零检测：正常流程直接执行
2. ✅ 异常捕获：所有失败都会抛异常
3. ✅ 内存检测：`null 数量 == 非主键数量`
4. ✅ 批次过滤：整行 null→重做，部分异常→抛出
5. ✅ 批次重做：CT 删除数据重新执行
6. ✅ 全 CT 删除：返回成功，不抛异常
7. ✅ 重试次数限制：3 次

**实现方式**：
- 复用现有异常机制，无需特殊异常定义
- 核心是处理逻辑，不是异常类型
- **CT 删除重做，其他异常抛出**

**关键**：批次过滤在**异常捕获**中执行

---

*最后更新：2026-04-24*

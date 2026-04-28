# ADR 06 实施方案

## 📋 目标
- SQL Server CT 写入重构：RIGHT JOIN + UNION ALL + U→I 转换
- 消除数据删除竞态问题
- 简化代码，去除异常检测逻辑

---

## ✅ 已实施

| 任务 | 状态 | 提交哈希 |
|------|------|---------|
| U → I 转换 (`convertOperation`) | ✅ | 2a27615e |
| RIGHT JOIN + UNION ALL | ✅ | 2a27615e |
| 按操作类型分流 (`processRow`) | ✅ | 2a27615e |
| 删除 `handleCtDeleteScenario` | ✅ | 2a27615e |
| 删除 `CtDeleteDetector.java` | ✅ | 2a27615e |
| 编译验证 | ✅ | BUILD SUCCESS |

---

## 📊 实施方案

### 1. U 操作转换为 I 操作

**文件**：`SqlServerCTListener.java`

```java
private String convertOperation(String operation) {
    if ("I".equals(operation)) {
        return ConnectorConstant.OPERTION_INSERT;
    } else if ("U".equals(operation)) {
        // ADR 06: U 操作转换为 I 操作，配合覆盖写入模式
        return ConnectorConstant.OPERTION_INSERT;
    } else if ("D".equals(operation)) {
        return ConnectorConstant.OPERTION_DELETE;
    }
    throw new IllegalArgumentException("Unknown operation: " + operation + ", expected I/U/D");
}
```

### 2. RIGHT JOIN + UNION ALL 单 SQL 查询

**文件**：`SqlServerTemplate.java`

```java
public String buildChangeTrackingDMLMainQuery(...) {
    return String.format(
        "SELECT " +
            "    CT.SYS_CHANGE_VERSION, " +
            "    CT.SYS_CHANGE_OPERATION, " +
            "    CT.SYS_CHANGE_COLUMNS, " +
            "    %s, " +  // 显式的列列表（I/U 操作数据）
            "    SI.schema_info AS " + CT_DDL_SCHEMA_INFO_COLUMN + " " +
            "FROM CHANGETABLE(CHANGES %s, ?) AS CT " +
            "RIGHT JOIN %s AS T WITH (NOLOCK) ON %s " +
            "CROSS APPLY %s SI " +
            "WHERE CT.SYS_CHANGE_VERSION > ? AND CT.SYS_CHANGE_VERSION <= ? " +
            "UNION ALL " +
            "SELECT " +
            "    CT.SYS_CHANGE_VERSION, " +
            "    CT.SYS_CHANGE_OPERATION, " +
            "    CT.SYS_CHANGE_COLUMNS, " +
            "    %s" +  // D 操作：冗余主键列为 NULL
            "    NULL " +  // T.* 为 NULL
            "FROM CHANGETABLE(CHANGES %s, ?) AS CT " +
            "WHERE CT.operation = 'D' " +
            "AND CT.SYS_CHANGE_VERSION > ? AND CT.SYS_CHANGE_VERSION <= ?",
        selectColumns,              // I/U 操作的列列表
        schemaTable,                // CHANGETABLE (主查询)
        schemaTable,                // JOIN table
        joinCondition,              // JOIN condition
        schemaInfoSubquery,         // 表结构信息子查询
        dOperationNullColumns + "NULL",  // D 操作：冗余主键列为 NULL
        schemaTable,                // CHANGETABLE (D 操作查询)
        selectColumns,              // D 操作：T.* 为 NULL
        schemaInfoSubquery          // D 操作：不需要表结构信息
    );
}
```

### 3. 按操作类型显式分流

**文件**：`processRow()` 方法

```java
// D 操作：只取主键值（CT 冗余列）
if ("D".equals(operation)) {
    List<Object> row = new ArrayList<>();
    for (String pkName : primaryKeys) {
        Integer ctPkIndex = primaryKeyToCTIndex.get(pkName);
        if (ctPkIndex != null) {
            row.add(rs.getObject(ctPkIndex));
        } else {
            logger.error("表 {} 的主键 {} 映射缺失，D 操作可能失败", tableName, pkName);
            throw new SqlServerException("表 " + tableName + " 的主键映射不完整，无法处理 D 操作");
        }
    }
    String operationCode = convertOperation(operation);
    return new CTEvent(tableName, operationCode, row, version, columnNames);
}

// I/U 操作：构建完整行数据
List<Object> row = new ArrayList<>();
for (int i = tStarStartIndex; i <= tStarEndIndex; i++) {
    if (columnsToSkip.contains(i)) continue;
    Object value = rs.getObject(i);
    String columnName = columnIndexToName.get(i);
    
    // 如果是主键列且值为 NULL，使用冗余的 CT_[pk] 值
    if (primaryKeySet.contains(columnName) && value == null) {
        Integer ctPkIndex = primaryKeyToCTIndex.get(columnName);
        if (ctPkIndex != null) {
            value = rs.getObject(ctPkIndex);
        }
    }
    row.add(value);
}

String operationCode = convertOperation(operation);
return new CTEvent(tableName, operationCode, row, version, columnNames);
```

### 4. 删除 handleCtDeleteScenario 和 CtDeleteDetector

- ✅ 删除 `AbstractDatabaseConnector.handleCtDeleteScenario()` 方法
- ✅ 删除 `CtDeleteDetector.java` 文件
- ✅ `executeWriter()` 简化，不再需要异常处理和递归重试

---

## 🎯 验收标准

1. ✅ SQL 改为 RIGHT JOIN + UNION ALL（单查询）
2. ✅ U 操作转换为 I 操作
3. ✅ D 操作按操作类型显式分流
4. ✅ 删除 handleCtDeleteScenario
5. ✅ 删除 CtDeleteDetector
6. ✅ 编译通过
7. ✅ 测试通过

---

## 📝 修改文件清单

| 文件 | 改动 | 说明 |
|------|------|------|
| `SqlServerTemplate.buildChangeTrackingDMLMainQuery()` | 修改 | LEFT JOIN → RIGHT JOIN + UNION ALL |
| `SqlServerCTListener.convertOperation()` | 修改 | U → I 转换 |
| `SqlServerCTListener.processRow()` | 修改 | 按操作类型显式分流 D 操作 |
| `AbstractDatabaseConnector.executeWriter()` | 简化 | 删除 try-catch + handleCtDeleteScenario |
| `AbstractDatabaseConnector.handleCtDeleteScenario()` | 删除 | 不再需要 |
| `CtDeleteDetector.java` | 删除 | 不再需要 |

---

## 🎉 实施完成

**提交哈希**: `2a27615e`  
**提交信息**: feat: ADR 06 - SQL Server CT 写入重构完成  
**日期**: 2026-04-28 07:15 UTC  

---

*最后更新：2026-04-28 07:30 UTC*

# ADR 0003: TableGroup 主键顺序修复

**日期**: 2026-03-30  
**状态**: 已采纳  
**作者**: 李  

## 背景

在 dbsyncer 项目中，`TableGroup` 类负责管理源表和目标表的映射关系。目标表主键配置通过 `targetTablePK` 字段存储（逗号分隔，按顺序）。

**核心原则**：主键顺序必须从数据库元数据查询获取，不能从 Field 列表的 `isPk` 标记推断。

## 问题

### 问题 1：从 Field.isPk 推断主键顺序（错误）

**位置**: `TableGroup.java:465-477`, `TableGroupChecker.java:83`

**错误实现**:
```java
// 错误：从 Field 列表过滤 isPk 标记
List<String> primaryKeys = sourceTable.getColumn().stream()
        .filter(Field::isPk)
        .map(Field::getName)
        .collect(Collectors.toList());
```

**问题根因**:
- `Field` 对象的 `isPk` 标记是布尔值，不包含顺序信息
- `Field` 列表的顺序是数据库返回的列顺序，**不是主键定义顺序**
- 例：复合主键 `(create_time, id)`，但字段列表顺序可能是 `[id, name, create_time]`
- 过滤结果：`[id, create_time]`（错误顺序）

### 问题 2：MetaInfo 未保存主键列表

**位置**: `AbstractDatabaseConnector.java:120`

**问题**:
- `getMetaInfo()` 方法查询了主键（`findTablePrimaryKeys()`），但只用于设置 `Field.isPk` 标记
- `MetaInfo` 对象没有保存有序主键列表
- 业务代码无法获取正确的主键顺序

### 问题 3：ADR 序号重复

**发现**:
- `0001-field-mapping-advanced-config.md` (2026-03-20)
- `0001-sqlserver-ct-bigtx-optimization.md` (2026-03-27) ← **重复！**

**已修正**: 重命名为 `0004-sqlserver-ct-bigtx-optimization.md`

## 选项分析

### 选项 1：从 Field.isPk 推断（错误）

**方案**: 维持当前实现，从 `Field` 列表过滤 `isPk` 标记。

**问题**:
- ❌ Field 列表顺序 ≠ 主键定义顺序
- ❌ 复合主键场景下顺序错误
- ❌ 无法修复

### 选项 2：从数据库元数据查询（推荐）

**方案**:
1. 在 `MetaInfo` 类中添加 `primaryKeys` 字段（有序列表）
2. 在 `AbstractDatabaseConnector.getMetaInfo()` 中保存查询到的主键列表
3. 业务代码从 `MetaInfo.getPrimaryKeys()` 获取有序主键列表

**优点**:
- ✅ 主键顺序来自数据库 `KEY_SEQ`，准确可靠
- ✅ 符合 JDBC 规范
- ✅ 所有调用方都能获取正确的主键顺序

**缺点**:
- 需要修改 `MetaInfo` 类和 `getMetaInfo()` 方法

## 决策

选择**选项 2：从数据库元数据查询**。

**修复内容**:

### 1. 修正 MetaInfo 类

```java
public class MetaInfo {
    // ... 现有字段 ...
    
    /**
     * 主键列表（按数据库定义的顺序）
     * 用于保持复合主键的正确顺序
     */
    private List<String> primaryKeys;

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public MetaInfo setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
        return this;
    }
}
```

### 2. 修正 AbstractDatabaseConnector.findTablePrimaryKeys()

**错误修复过程**：

**第一次修复（错误）**：手动按 KEY_SEQ 排序
```java
// 错误实现：多余的排序
List<Map.Entry<Integer, String>> pkList = new ArrayList<>();
while (rs.next()) {
    int keySeq = rs.getInt("KEY_SEQ");
    String columnName = rs.getString("COLUMN_NAME");
    pkList.add(new AbstractMap.SimpleEntry<>(keySeq, columnName));
}
pkList.sort(Comparator.comparingInt(Map.Entry::getKey));  // ← 多余！
```

**第二次修复（正确）**：直接按遍历顺序收集
```java
private List<String> findTablePrimaryKeys(DatabaseMetaData md, String catalog, String schema, String tableName) throws SQLException {
    ResultSet rs = null;
    try {
        rs = md.getPrimaryKeys(catalog, schema, tableName);
        // JDBC 规范保证：getPrimaryKeys() 返回的 ResultSet 已按 KEY_SEQ 升序排序
        // 直接按遍历顺序收集，保持数据库定义的主键顺序
        List<String> primaryKeys = new ArrayList<>();
        while (rs.next()) {
            primaryKeys.add(rs.getString("COLUMN_NAME"));
        }
        return primaryKeys;
    } finally {
        DatabaseUtil.close(rs);
    }
}
```

**教训**：JDBC 规范保证 `getPrimaryKeys()` 返回的 ResultSet 已经按 KEY_SEQ 升序排序。手动排序是多余的，还可能引入性能开销。

### 3. 修正 AbstractDatabaseConnector.getMetaInfo()

```java
// 在 getMetaInfo() 中保存主键列表
List<String> primaryKeys = new ArrayList<>();
connectorInstance.execute(databaseTemplate -> {
    SimpleConnection connection = databaseTemplate.getSimpleConnection();
    Connection conn = connection.getConnection();
    String catalog = conn.getCatalog();
    String schemaForPK = getSchema(connectorInstance.getConfig());
    String schemaNamePatternForPK = null == schemaForPK ? conn.getSchema() : schemaForPK;
    DatabaseMetaData metaData = conn.getMetaData();
    primaryKeys.addAll(findTablePrimaryKeys(metaData, catalog, schemaNamePatternForPK, tableNamePattern));
    return null;
});
return new MetaInfo()
        .setColumn(enhanceFields(connectorInstance, fields, tableNamePattern))
        .setPrimaryKeys(primaryKeys);
```

### 4. 修正 TableGroup.migrateVersion()

```java
public void migrateVersion() throws Exception {
    if (this.currentVersion == Version) return;

    if (currentVersion < Version) {
        // 原则：主键顺序必须从数据库元数据查询获取
        if (StringUtil.isBlank(this.targetTablePK) && profileComponent != null && mappingId != null) {
            Mapping mapping = profileComponent.getMapping(mappingId);
            if (mapping != null) {
                MetaInfo sourceMetaInfo = parserComponent.getMetaInfo(mapping.getSourceConnectorId(), sourceTable.getName());
                if (sourceMetaInfo != null && !CollectionUtils.isEmpty(sourceMetaInfo.getPrimaryKeys())) {
                    // getPrimaryKeys() 返回的是按数据库 KEY_SEQ 排序的主键列表
                    this.targetTablePK = String.join(",", sourceMetaInfo.getPrimaryKeys());
                }
            }
        }
    }

    this.currentVersion = Version;
    this.profileComponent.editTableGroup(this);
}
```

### 5. 修正 TableGroupChecker.checkAddConfigModel()

```java
// 保存主键配置到 tableGroup
// 原则：主键顺序应该从数据库元数据查询获取，而不是从 Field.isPk 标记推断
if (StringUtil.isNotBlank(targetTablePK)) {
    tableGroup.setTargetTablePK(targetTablePK);
} else {
    // 从源表元数据获取主键（通过查询数据库，保持主键顺序）
    MetaInfo sourceMetaInfo = parserComponent.getMetaInfo(mapping.getSourceConnectorId(), sourceTable);
    if (sourceMetaInfo != null && !CollectionUtils.isEmpty(sourceMetaInfo.getPrimaryKeys())) {
        // getPrimaryKeys() 返回的是按数据库 KEY_SEQ 排序的主键列表
        targetTablePK = String.join(",", sourceMetaInfo.getPrimaryKeys());
        tableGroup.setTargetTablePK(targetTablePK);
        logger.info("目标表 {} 未提供主键配置，从源表元数据获取：{}", 
            tableGroup.getTargetTable().getName(), targetTablePK);
    }
}
```

### 6. 修正 ADR 序号

- `0001-sqlserver-ct-bigtx-optimization.md` → `0004-sqlserver-ct-bigtx-optimization.md`

## 后果

### 正面影响

1. **主键顺序正确**: 从数据库 `KEY_SEQ` 获取，复合主键顺序正确
2. **符合 JDBC 规范**: 正确使用 `DatabaseMetaData.getPrimaryKeys()` 的 `KEY_SEQ` 字段
3. **全局受益**: 所有调用 `MetaInfo.getPrimaryKeys()` 的地方都获得正确的主键顺序
4. **向后兼容**: 已保存的 `targetTablePK` 会被保留

### 负面影响

1. **遗留问题**: 已受影响的旧数据需要手动修正
2. **SDK 修改**: 需要回归测试确保不影响其他功能

### 待办事项

1. 添加复合主键的单元测试
2. 前端应明确提示用户主键顺序的重要性
3. 考虑添加主键顺序校验工具

## 相关链接

- TableGroup.java: `/projects/github/dbsyncer/dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/TableGroup.java`
- TableGroupChecker.java: `/projects/github/dbsyncer/dbsyncer-biz/src/main/java/org/dbsyncer/biz/checker/impl/tablegroup/TableGroupChecker.java`
- AbstractDatabaseConnector.java: `/projects/github/dbsyncer/dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/AbstractDatabaseConnector.java`
- MetaInfo.java: `/projects/github/dbsyncer/dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/model/MetaInfo.java`

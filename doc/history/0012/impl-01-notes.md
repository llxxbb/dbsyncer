# ADR-0012 实施说明

## 元信息

| 字段 | 值 |
|------|-----|
| 作者 | 凌曦 |
| 日期 | 2026-05-28 |
| 关联设计 | [ADR-0012](../../adr/0012-消除-syncSourceTable-克隆-TableGroup即上下文.md) |

## 实现概述

已实施 ✅ (2026-05-28)

消除 `TableGroup` 中的 `syncSourceTable/syncTargetTable` 克隆对象，改为提供 `getSyncSourceFields()` / `getSyncTargetFields()` 按需组合字段列表。`Picker` 和 `initCommand()` 从读取克隆表改为读取按需字段列表。

**已修改文件：**
- `TableGroup.java` — 删除 `syncSourceTable/syncTargetTable` 字段、`buildSyncTables()`、`getSyncSourceTable()`、`getSyncTargetTable()` 方法，新增 `getSyncSourceFields()` / `getSyncTargetFields()`，更新 `initCommand()`
- `Picker.java` — 构造函数改为调用 `tableGroup.getSyncSourceFields()` / `getSyncTargetFields()`

## 变更清单

### 1. TableGroup.java — 替换克隆逻辑

**删除：**
- `syncSourceTable`、`syncTargetTable` 字段
- `buildSyncTables()` 方法
- `getSyncSourceTable()`、`getSyncTargetTable()` 方法
- `clear()` 中对克隆表的清理（无需清理，不再持有）

**新增：**

```java
/**
 * 按 fieldMapping 顺序获取同步用的源字段列表。
 * 直接从 sourceTable 提取，不克隆。
 */
@JsonIgnore
public List<Field> getSyncSourceFields() {
    Table sourceTable = this.getSourceTable();
    List<FieldMapping> fieldMapping = this.getFieldMapping();
    List<Field> result = new ArrayList<>(fieldMapping.size());
    for (FieldMapping m : fieldMapping) {
        if (StringUtil.isNotBlank(m.getSourceName())) {
            Field sField = sourceTable.findColumnByName(m.getSourceName());
            if (sField != null) {
                result.add(sField);
            }
        }
    }
    // 处理转换配置中的自定义字段（不重复添加）
    Set<String> added = result.stream().map(Field::nameIgnoreCase).collect(Collectors.toSet());
    List<Convert> convert = this.getConvert();
    if (!CollectionUtils.isEmpty(convert)) {
        for (Convert c : convert) {
            Field fm = c.getFieldMetadata();
            if (fm != null && !added.contains(fm.nameIgnoreCase())) {
                result.add(fm);
            }
        }
    }
    return result;
}

/**
 * 按 fieldMapping 顺序获取同步用的目标字段列表。
 * targetTable 无元数据时（如 Kafka），从源字段克隆，保留 pk 等属性。
 */
@JsonIgnore
public List<Field> getSyncTargetFields() {
    Table sourceTable = this.getSourceTable();
    Table targetTable = this.getTargetTable();
    List<FieldMapping> fieldMapping = this.getFieldMapping();
    Map<String, Field> sourceFieldCache = new HashMap<>();
    List<Field> result = new ArrayList<>(fieldMapping.size());
    for (FieldMapping m : fieldMapping) {
        if (StringUtil.isNotBlank(m.getTargetName())) {
            Field tField = targetTable.findColumnByName(m.getTargetName());
            if (tField != null) {
                result.add(tField);
            } else {
                // 目标表无元数据（如 Kafka），从源字段克隆
                if (StringUtil.isNotBlank(m.getSourceName())) {
                    Field sField = sourceFieldCache.computeIfAbsent(m.getSourceName(),
                            k -> sourceTable.findColumnByName(k));
                    if (sField != null) {
                        tField = new Field(m.getTargetName(), sField.getTypeName(), sField.getType(), sField.isPk(),
                                sField.getColumnSize(), sField.getRatio(), sField.getSrid());
                        result.add(tField);
                    }
                }
            }
        }
    }
    // 处理转换配置中的自定义字段
    Set<String> added = result.stream().map(Field::nameIgnoreCase).collect(Collectors.toSet());
    List<Convert> convert = this.getConvert();
    if (!CollectionUtils.isEmpty(convert)) {
        for (Convert c : convert) {
            Field fm = c.getFieldMetadata();
            if (fm != null && !added.contains(fm.nameIgnoreCase())) {
                result.add(fm);
            }
        }
    }
    return result;
}
```

### 2. initCommand() — 构建临时 Table 传给 CommandConfig

`CommandConfig` 构造函数需要 `Table` 对象，下游 `SqlTemplate`、`AbstractDatabaseConnector` 等通过 `commandConfig.getTable().getColumn()` 取字段列表。

**改法**：用 `getSyncSourceFields()` / `getSyncTargetFields()` 构建轻量 Table 对象（仅设 name + column），传入 `CommandConfig`。不缓存，每次 `initCommand()` 执行时构建。

```java
// 替换原有 buildSyncTables() + syncSourceTable/syncTargetTable 引用
List<Field> sourceFields = getSyncSourceFields();
List<Field> targetFields = getSyncTargetFields();

// 构建轻量 Table 对象，仅包含同步需要的字段
Table sTable = sourceTable.clone().setColumn(new ArrayList<>(sourceFields));
Table tTable = targetTable.clone().setColumn(new ArrayList<>(targetFields));

final CommandConfig sourceConfig = new CommandConfig(sConnConfig.getConnectorType(), sTable, ...);
final CommandConfig targetConfig = new CommandConfig(tConnConfig.getConnectorType(), tTable, ...);
```

### 3. Picker.java — 构造函数改为读取字段列表

**替换：**
```java
// 旧
sourceFields.addAll(tableGroup.getSyncSourceTable().getColumn());
targetFields.addAll(tableGroup.getSyncTargetTable().getColumn());

// 新
sourceFields.addAll(tableGroup.getSyncSourceFields());
targetFields.addAll(tableGroup.getSyncTargetFields());
```

## 与设计的差异

无偏离。

## 后续优化建议

无。实施完成。

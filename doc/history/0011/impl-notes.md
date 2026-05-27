# 实施说明 — ADR-0011: 简化 FieldMapping 结构

> **目标**：将 `FieldMapping` 从存储完整 `Field` 对象改为只存字段名字符串
> **验收要求**：每个改动点标注文件、行号、旧代码、新代码（伪代码），逐一验证

---

## 改动总览

| # | 文件 | 改动类型 | 风险 |
|---|------|----------|------|
| 1 | `FieldMapping.java` | 核心结构变更 | 🔴 高 |
| 2 | `TableGroup.initCommand()` | 字段查找逻辑 | 🔴 高 |
| 3 | `Picker` 构造器 | 字段查找逻辑 | 🔴 高 |
| 4 | `TableGroup.containsField()` | 字符串比较 | 🟢 低 |
| 5 | `DDLParserImpl.updateFieldMapping()` | 字段刷新 | 🟡 中 |
| 6 | `DDLParserImpl.renameFieldMapping()` | RENAME 映射 | 🟡 中 |
| 7 | `DDLParserImpl.removeFieldMappings()` | DROP 映射 | 🟡 中 |
| 8 | `DDLParserImpl.appendFieldMappings()` | ADD 映射 | 🟢 低 |
| 9 | `InsertSql.java` | 列名替换 | 🟡 中 |
| 10 | `UpdateSql.java` | WHERE 列名替换 | 🟡 中 |
| 11 | `DeleteSql.java` | WHERE 列名替换 | 🟡 中 |
| 12 | `PickerUtil.appendFieldMapping()` | 过滤/转换字段追加 | 🟡 中 |
| 13 | `FieldComparisonUtil.compareMappingWithTarget()` | 差异对比 | 🟡 中 |
| 14 | `TableGroupServiceImpl.removeFieldMappingByTargetName()` | 移除映射 | 🟢 低 |
| 15 | `TableGroupChecker.setFieldMapping()` | 构建映射（存字符串） | 🟢 低 |
| 16 | `TableGroupChecker.buildFieldMappingFromSourceTable()` | 构建映射（存字符串） | 🟢 低 |
| 17 | 集成测试断言 | 适配新 API | 🟡 中 |

---

## 改动点 #1 — FieldMapping.java 核心结构

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/FieldMapping.java`

### 旧结构

```java
public class FieldMapping {
    private Field source;
    private Field target;

    public FieldMapping() {}
    public FieldMapping(Field source, Field target) {
        this.source = source;
        this.target = target;
    }
    public Field getSource() { return source; }
    public void setSource(Field source) { this.source = source; }
    public Field getTarget() { return target; }
    public void setTarget(Field target) { this.target = target; }
}
```

### 新结构（兼容旧 JSON 反序列化）

```java
public class FieldMapping {

    // ========== 新结构：只存字段名 ==========
    private String sourceName;
    private String targetName;

    public FieldMapping() {}

    /**
     * 新构造函数：直接存字段名
     */
    public FieldMapping(String sourceName, String targetName) {
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    // ========== 兼容 getter/setter ==========

    /**
     * 获取源字段名（直接返回字符串）
     */
    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    /**
     * 兼容方法：获取源字段名（等价于 getSourceName）
     * 用于序列化时输出 "source" key 保持前端 JSON 格式不变
     */
    @JsonProperty("source")
    public String getSource() {
        return sourceName;
    }

    @JsonProperty("source")
    public void setSource(String name) {
        this.sourceName = name;
    }

    @JsonProperty("target")
    public String getTarget() {
        return targetName;
    }

    @JsonProperty("target")
    public void setTarget(String name) {
        this.targetName = name;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 从 Field 对象构建（内部调用）
     */
    public static FieldMapping fromFields(Field source, Field target) {
        return new FieldMapping(
            source != null ? source.getName() : null,
            target != null ? target.getName() : null
        );
    }

    /**
     * 旧 JSON 反序列化兼容：{"source":{"name":"id",...},"target":{"name":"id",...}}
     * Jackson 遇到对象会调用此 setter，从中提取 name
     */
    @JsonSetter("source")
    public void setSourceObject(Field field) {
        // 如果传入的是 Field 对象（旧格式），提取 name
        if (field != null) {
            this.sourceName = field.getName();
        }
    }

    @JsonSetter("target")
    public void setTargetObject(Field field) {
        if (field != null) {
            this.targetName = field.getName();
        }
    }
}
```

### ⚠️ Jackson 兼容关键点

- `@JsonProperty("source")` 标注 `String getSource()` — 序列化输出 `{"source":"id"}`
- `@JsonSetter("source")` 标注 `void setSourceObject(Field)` — 反序列化旧格式时捕获
- Jackson 遇到字符串会调 `setSource(String)`，遇到对象会调 `setSourceObject(Field)`

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 1.1 | 序列化新 `FieldMapping("id","id")` | `{"source":"id","target":"id"}` |
| 1.2 | 反序列化 `{"source":"id","target":"id"}` | `sourceName="id"`, `targetName="id"` |
| 1.3 | 反序列化旧格式 `{"source":{"name":"id",...},"target":{"name":"id",...}}` | `sourceName="id"`, `targetName="id"` |

---

## 改动点 #2 — TableGroup.initCommand() 字段查找

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/TableGroup.java`
**行号**: ~L330-L338

### 旧代码

```java
// 同步字段主要参考源库
fieldMapping.forEach(m -> {
    if (null != m.getSource()) {
        sTable.getColumn().add(m.getSource());       // ❌ 直接取 Field 对象
    }
    if (null != m.getTarget()) {
        tTable.getColumn().add(m.getTarget());       // ❌ 直接取 Field 对象
    }
});
```

### 新代码

```java
// 从 Table 查找字段元数据（消除冗余，保证一致性）
fieldMapping.forEach(m -> {
    if (StringUtil.isNotBlank(m.getSourceName())) {
        Field sField = sourceTable.findColumnByName(m.getSourceName());
        if (sField != null) {
            sTable.getColumn().add(sField);
        }
    }
    if (StringUtil.isNotBlank(m.getTargetName())) {
        Field tField = targetTable.findColumnByName(m.getTargetName());
        if (tField != null) {
            tTable.getColumn().add(tField);
        }
    }
});
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 2.1 | 正常映射任务 initCommand | sTable/tTable 的 column 正确填充 |
| 2.2 | sourceName 在 sourceTable 中不存在 | 跳过（null 安全） |
| 2.3 | targetName 在 targetTable 中不存在 | 跳过（null 安全） |
| 2.4 | DDL 变更后调用 initCommand | 获取最新 Field 元数据 |

---

## 改动点 #3 — Picker 构造器

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Picker.java`
**行号**: ~L31-L36

### 旧代码

```java
public Picker(TableGroup tableGroup) {
    if (!CollectionUtils.isEmpty(tableGroup.getFieldMapping())) {
        tableGroup.getFieldMapping().forEach(m -> {
            sourceFields.add(m.getSource());       // ❌ 直接取 Field
            targetFields.add(m.getTarget());       // ❌ 直接取 Field
        });
    }
    // ...
}
```

### 新代码

```java
public Picker(TableGroup tableGroup) {
    Table sourceTable = tableGroup.getSourceTable();
    Table targetTable = tableGroup.getTargetTable();

    if (!CollectionUtils.isEmpty(tableGroup.getFieldMapping())) {
        tableGroup.getFieldMapping().forEach(m -> {
            // 从 Table 查找字段元数据
            if (StringUtil.isNotBlank(m.getSourceName()) && sourceTable != null) {
                Field sf = sourceTable.findColumnByName(m.getSourceName());
                if (sf != null) sourceFields.add(sf);
            }
            if (StringUtil.isNotBlank(m.getTargetName()) && targetTable != null) {
                Field tf = targetTable.findColumnByName(m.getTargetName());
                if (tf != null) targetFields.add(tf);
            }
        });
    }
    // ... rest unchanged
}
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 3.1 | Picker 构造后 getSourceFields() | 返回正确字段列表（含 typeName/type 等） |
| 3.2 | pickTargetData() 数据映射 | 数据正确从源字段映射到目标字段 |
| 3.3 | sourceName 找不到 | 跳过，不抛异常 |

---

## 改动点 #4 — TableGroup.containsField()

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/TableGroup.java`
**行号**: ~L225-L237

### 旧代码

```java
public boolean containsField(String name) {
    if (fieldMapping == null || fieldMapping.isEmpty()) {
        return false;
    }
    for (FieldMapping fm : fieldMapping) {
        if (fm.getSource() != null && fm.getSource().matchesName(name)) {  // ❌
            return true;
        }
        if (fm.getTarget() != null && fm.getTarget().matchesName(name)) {  // ❌
            return true;
        }
    }
    return false;
}
```

### 新代码

```java
public boolean containsField(String name) {
    if (fieldMapping == null || fieldMapping.isEmpty()) {
        return false;
    }
    for (FieldMapping fm : fieldMapping) {
        if (StringUtil.equalsIgnoreCase(fm.getSourceName(), name)) {
            return true;
        }
        if (StringUtil.equalsIgnoreCase(fm.getTargetName(), name)) {
            return true;
        }
    }
    return false;
}
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 4.1 | containsField("id") | 正确返回 |
| 4.2 | containsField("ID") 大小写 | 不区分大小写匹配 |

---

## 改动点 #5 — DDLParserImpl.updateFieldMapping()

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/ddl/impl/DDLParserImpl.java`
**行号**: ~L261-L290

### 旧代码

```java
private void updateFieldMapping(TableGroup tableGroup, List<String> modifiedFieldNames) {
    Map<String, Field> sourceFiledMap = ...;
    Map<String, Field> targetFiledMap = ...;
    for (FieldMapping fieldMapping : tableGroup.getFieldMapping()) {
        Field source = fieldMapping.getSource();       // ❌
        Field target = fieldMapping.getTarget();       // ❌
        if (source != null) {
            String modifiedName = source.getName();
            if (!modifiedFieldNames.contains(modifiedName)) continue;
            sourceFiledMap.computeIfPresent(source.nameIgnoreCase(), (k, field) -> {
                fieldMapping.setSource(field);         // ❌ 设 Field 对象
                return field;
            });
            if (target != null && StringUtil.equalsIgnoreCase(modifiedName, target.getName())) {
                targetFiledMap.computeIfPresent(target.nameIgnoreCase(), (k, field) -> {
                    fieldMapping.setTarget(field);     // ❌ 设 Field 对象
                    return field;
                });
            }
        }
    }
}
```

### 新代码

```java
private void updateFieldMapping(TableGroup tableGroup, List<String> modifiedFieldNames) {
    for (FieldMapping fieldMapping : tableGroup.getFieldMapping()) {
        String srcName = fieldMapping.getSourceName();
        if (StringUtil.isBlank(srcName)) continue;

        // 不区分大小写匹配
        String matchedModifiedName = modifiedFieldNames.stream()
            .filter(n -> StringUtil.equalsIgnoreCase(n, srcName))
            .findFirst().orElse(null);
        if (matchedModifiedName == null) continue;

        // 更新 sourceName 为新的字段名（如果字段名变了）
        // 如果字段只是属性变更（如 type/nullable），sourceName 不变
        // 但 Field 元数据已通过 Table.column 自动更新（initCommand 时重新查找）
        Field newSource = tableGroup.getSourceTable().findColumnByName(srcName);
        if (newSource != null && !StringUtil.equals(srcName, newSource.getName())) {
            fieldMapping.setSourceName(newSource.getName());
        }

        // 同步更新 targetName（同名场景）
        String tgtName = fieldMapping.getTargetName();
        if (StringUtil.isNotBlank(tgtName) && StringUtil.equalsIgnoreCase(srcName, tgtName)) {
            Field newTarget = tableGroup.getTargetTable().findColumnByName(tgtName);
            if (newTarget != null && !StringUtil.equals(tgtName, newTarget.getName())) {
                fieldMapping.setTargetName(newTarget.getName());
            }
        }
    }
}
```

> **关键变更说明**：旧版本通过 `setSource(field)` 刷新 Field 对象中的 typeName/type 等元数据。新版本中，元数据不再存储在 FieldMapping 中，而是通过 `initCommand()` 和 `Picker` 构造器中的 `findColumnByName` 从 Table 获取。因此 `updateFieldMapping` 只需处理字段名变化（RENAME），不需要刷新 Field 属性。

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 5.1 | ALTER TABLE MODIFY COLUMN（类型变更） | 同步后 Picker 获取最新 typeName |
| 5.2 | ALTER TABLE MODIFY COLUMN（nullable 变更） | 同步后 Picker 获取最新 nullable |

---

## 改动点 #6 — DDLParserImpl.renameFieldMapping()

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/ddl/impl/DDLParserImpl.java`
**行号**: ~L302-L345

### 旧代码

```java
private void renameFieldMapping(TableGroup tableGroup, Map<String, String> changedFieldNames) {
    Iterator<FieldMapping> iterator = tableGroup.getFieldMapping().iterator();
    while (iterator.hasNext()) {
        FieldMapping fieldMapping = iterator.next();
        Field source = fieldMapping.getSource();       // ❌
        Field target = fieldMapping.getTarget();       // ❌

        for (Map.Entry<String, String> entry : changedFieldNames.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            if (source != null && source.matchesName(oldName)) {
                Field newSourceField = tableGroup.getSourceTable().findColumnByName(newName);
                if (newSourceField == null) {
                    iterator.remove();
                    break;
                }
                fieldMapping.setSource(newSourceField);       // ❌ 设 Field 对象

                if (target != null && target.matchesName(oldName)) {
                    Field newTargetField = tableGroup.getTargetTable().findColumnByName(newName);
                    if (newTargetField != null) {
                        fieldMapping.setTarget(newTargetField);  // ❌ 设 Field 对象
                    } else {
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }
}
```

### 新代码

```java
private void renameFieldMapping(TableGroup tableGroup, Map<String, String> changedFieldNames) {
    Iterator<FieldMapping> iterator = tableGroup.getFieldMapping().iterator();
    while (iterator.hasNext()) {
        FieldMapping fieldMapping = iterator.next();
        String srcName = fieldMapping.getSourceName();
        String tgtName = fieldMapping.getTargetName();

        for (Map.Entry<String, String> entry : changedFieldNames.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            if (StringUtil.equalsIgnoreCase(srcName, oldName)) {
                // 验证新字段在源表中存在
                Field newSourceField = tableGroup.getSourceTable().findColumnByName(newName);
                if (newSourceField == null) {
                    logger.warn("源表中未找到新字段 {}，移除字段映射", newName);
                    iterator.remove();
                    break;
                }
                fieldMapping.setSourceName(newName);

                // 同名字段同步更新
                if (StringUtil.equalsIgnoreCase(tgtName, oldName)) {
                    Field newTargetField = tableGroup.getTargetTable().findColumnByName(newName);
                    if (newTargetField != null) {
                        fieldMapping.setTargetName(newName);
                    } else {
                        logger.warn("目标表中未找到新字段 {}，移除字段映射", newName);
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }
}
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 6.1 | ALTER TABLE RENAME COLUMN old_col TO new_col | fieldMapping.sourceName 更新为 new_col |
| 6.2 | 新字段不存在于源表 | 移除该映射 |
| 6.3 | 新字段不存在于目标表（同名场景） | 移除该映射 |

---

## 改动点 #7 — DDLParserImpl.removeFieldMappings()

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/ddl/impl/DDLParserImpl.java`
**行号**: ~L350-L370

### 旧代码

```java
private void removeFieldMappings(TableGroup tableGroup, List<String> droppedFieldNames) {
    Iterator<FieldMapping> iterator = tableGroup.getFieldMapping().iterator();
    while (iterator.hasNext()) {
        FieldMapping fieldMapping = iterator.next();
        Field source = fieldMapping.getSource();       // ❌
        Field target = fieldMapping.getTarget();       // ❌
        for (String droppedFieldName : droppedFieldNames) {
            if ((source != null && source.matchesName(droppedFieldName))
                    || (target != null && target.matchesName(droppedFieldName))) {
                iterator.remove();
                break;
            }
        }
    }
}
```

### 新代码

```java
private void removeFieldMappings(TableGroup tableGroup, List<String> droppedFieldNames) {
    Iterator<FieldMapping> iterator = tableGroup.getFieldMapping().iterator();
    while (iterator.hasNext()) {
        FieldMapping fieldMapping = iterator.next();
        String srcName = fieldMapping.getSourceName();
        String tgtName = fieldMapping.getTargetName();
        for (String droppedFieldName : droppedFieldNames) {
            if (StringUtil.equalsIgnoreCase(srcName, droppedFieldName)
                    || StringUtil.equalsIgnoreCase(tgtName, droppedFieldName)) {
                iterator.remove();
                break;
            }
        }
    }
}
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 7.1 | ALTER TABLE DROP COLUMN col_x | 移除所有 sourceName="col_x" 或 targetName="col_x" 的映射 |
| 7.2 | DROP 后 remaining fieldMapping | 剩余映射正确 |

---

## 改动点 #8 — DDLParserImpl.appendFieldMappings()

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/ddl/impl/DDLParserImpl.java`
**行号**: ~L293-L300（方法末尾追加新字段）

### 旧代码

```java
private void appendFieldMappings(TableGroup tableGroup, List<String> addedFieldNames) {
    // 对每个新增字段，从 Table 取 Field，构建 FieldMapping 加入
    for (String addedFieldName : addedFieldNames) {
        Field sourceField = sourceFiledMap.get(addedFieldName.toLowerCase());
        Field targetField = targetFiledMap.get(addedFieldName.toLowerCase());
        if (sourceField != null) {
            tableGroup.getFieldMapping().add(new FieldMapping(sourceField, targetField));  // ❌
        }
    }
}
```

### 新代码

```java
private void appendFieldMappings(TableGroup tableGroup, List<String> addedFieldNames) {
    for (String addedFieldName : addedFieldNames) {
        Field sourceField = tableGroup.getSourceTable().findColumnByName(addedFieldName);
        Field targetField = tableGroup.getTargetTable().findColumnByName(addedFieldName);
        if (sourceField != null) {
            tableGroup.getFieldMapping().add(FieldMapping.fromFields(sourceField, targetField));
        }
    }
}
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 8.1 | ALTER TABLE ADD COLUMN new_col | fieldMapping 自动追加新映射 |

---

## 改动点 #9 — InsertSql.java 列名替换

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/sql/impl/InsertSql.java`
**行号**: ~L48-L56

### 旧代码

```java
fieldMappingList.stream()
    .filter(x -> x.getSource().getName()              // ❌
            .equals(column.getColumnName().replaceAll("\"", "")))
    .findFirst().ifPresent(
        fieldMapping -> column.setColumnName(fieldMapping.getTarget().getName()));  // ❌
```

### 新代码

```java
String colName = column.getColumnName().replaceAll("\"", "");
fieldMappingList.stream()
    .filter(x -> StringUtil.equalsIgnoreCase(x.getSourceName(), colName))
    .findFirst().ifPresent(
        fieldMapping -> column.setColumnName(fieldMapping.getTargetName()));
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 9.1 | INSERT 语句生成 | 源字段名正确替换为目标字段名 |
| 9.2 | 大小写敏感字段 | 正确匹配（不区分大小写） |

---

## 改动点 #10 — UpdateSql.java WHERE 列名替换

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/sql/impl/UpdateSql.java`
**行号**: ~L55-L58, L80-L83（两处 findColumn 方法）

### 旧代码

```java
fieldMappingList.stream()
    .filter(x -> x.getSource().getName()              // ❌
            .equals(column.getColumnName().replaceAll("\"", "")))
    .findFirst().ifPresent(
        fieldMapping -> column.setColumnName(fieldMapping.getTarget().getName()));  // ❌
```

### 新代码（同 InsertSql 改动）

```java
String colName = column.getColumnName().replaceAll("\"", "");
fieldMappingList.stream()
    .filter(x -> StringUtil.equalsIgnoreCase(x.getSourceName(), colName))
    .findFirst().ifPresent(
        fieldMapping -> column.setColumnName(fieldMapping.getTargetName()));
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 10.1 | UPDATE SET 语句生成 | 源字段名正确替换 |
| 10.2 | UPDATE WHERE 语句生成 | WHERE 条件中的字段名正确替换 |

---

## 改动点 #11 — DeleteSql.java WHERE 列名替换

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/sql/impl/DeleteSql.java`
**行号**: ~L68-L74

### 旧代码（同 InsertSql）

```java
fieldMappingList.stream()
    .filter(x -> x.getSource().getName()
            .equals(column.getColumnName().replaceAll("\"", "")))
    .findFirst().ifPresent(
        fieldMapping -> column.setColumnName(fieldMapping.getTarget().getName()));
```

### 新代码（同 InsertSql 改动）

```java
String colName = column.getColumnName().replaceAll("\"", "");
fieldMappingList.stream()
    .filter(x -> StringUtil.equalsIgnoreCase(x.getSourceName(), colName))
    .findFirst().ifPresent(
        fieldMapping -> column.setColumnName(fieldMapping.getTargetName()));
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 11.1 | DELETE WHERE 语句生成 | WHERE 条件字段名正确替换 |

---

## 改动点 #12 — PickerUtil.appendFieldMapping()

**文件**: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/util/PickerUtil.java`
**行号**: ~L78-L110

### 旧代码

```java
private static void appendFieldMapping(Mapping mapping, TableGroup group) {
    final List<FieldMapping> fieldMapping = group.getFieldMapping();

    // 转换配置检查
    Set<String> targetFieldNames = fieldMapping.stream()
            .map(FieldMapping::getTarget)            // ❌
            .filter(f -> f != null)
            .map(Field::nameIgnoreCase)              // ❌
            .filter(StringUtil::isNotBlank)
            .collect(Collectors.toSet());
    convert.forEach(c -> {
        String fieldName = c.getName();
        Field targetField = group.getTargetTable().findColumnByName(fieldName);
        if (targetField != null && !targetFieldNames.contains(fieldName.toLowerCase())) {
            fieldMapping.add(new FieldMapping(null, targetField));  // ❌
        }
    });
}

private static void addFieldMapping(List<FieldMapping> fieldMapping, String name, Table table, boolean checkSource) {
    for (FieldMapping m : fieldMapping) {
        Field f = checkSource ? m.getSource() : m.getTarget();  // ❌
        if (f.matchesName(name)) { exist = true; break; }
    }
    if (!exist) {
        Field field = table.findColumnByName(name);
        if (field != null) {
            FieldMapping fm = checkSource ? new FieldMapping(field, null) : new FieldMapping(null, field);  // ❌
            fieldMapping.add(fm);
        }
    }
}
```

### 新代码

```java
private static void appendFieldMapping(Mapping mapping, TableGroup group) {
    final List<FieldMapping> fieldMapping = group.getFieldMapping();

    // 转换配置检查 — 用 targetName 替代
    Set<String> targetFieldNames = fieldMapping.stream()
            .map(FieldMapping::getTargetName)
            .filter(StringUtil::isNotBlank)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    convert.forEach(c -> {
        String fieldName = c.getName();
        Field targetField = group.getTargetTable().findColumnByName(fieldName);
        if (targetField != null && !targetFieldNames.contains(fieldName.toLowerCase())) {
            fieldMapping.add(new FieldMapping(null, targetField.getName()));
        }
    });
}

private static void addFieldMapping(List<FieldMapping> fieldMapping, String name, Table table, boolean checkSource) {
    for (FieldMapping m : fieldMapping) {
        String existingName = checkSource ? m.getSourceName() : m.getTargetName();
        if (StringUtil.equalsIgnoreCase(existingName, name)) { exist = true; break; }
    }
    if (!exist) {
        Field field = table.findColumnByName(name);
        if (field != null) {
            FieldMapping fm = checkSource
                ? new FieldMapping(field.getName(), null)
                : new FieldMapping(null, field.getName());
            fieldMapping.add(fm);
        }
    }
}
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 12.1 | 增量事件字段追加 | eventFieldName 正确追加到 fieldMapping |
| 12.2 | 过滤条件字段追加 | filter 中的字段正确追加 |
| 12.3 | 转换配置无源字段 | 只有 targetName 的映射正确追加 |

---

## 改动点 #13 — FieldComparisonUtil.compareMappingWithTarget()

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/util/FieldComparisonUtil.java`
**行号**: ~L233-L260

### 旧代码

```java
public static List<FieldDiffItem> compareMappingWithTarget(List<FieldMapping> fieldMappings, List<Field> targetFields) {
    for (FieldMapping fieldMapping : fieldMappings) {
        if (fieldMapping == null || fieldMapping.getTarget() == null || fieldMapping.getTarget().getName() == null) {
            continue;
        }
        String targetFieldName = fieldMapping.getTarget().getName();   // ❌

        boolean existsInTarget = targetFields.stream()
            .anyMatch(tf -> tf != null && tf.matchesName(targetFieldName));

        if (!existsInTarget) {
            FieldDiffItem item = new FieldDiffItem();
            item.setFieldName(targetFieldName);
            item.setTargetType(fieldMapping.getTarget().getTypeName());    // ❌ 需要 Field
            item.setTargetLength(fieldMapping.getTarget().getColumnSize()); // ❌ 需要 Field
            // ...
        }
    }
}
```

### 新代码

```java
public static List<FieldDiffItem> compareMappingWithTarget(List<FieldMapping> fieldMappings, List<Field> targetFields) {
    // 构建 targetFields Map 便于查找
    Map<String, Field> targetFieldMap = targetFields.stream()
        .collect(Collectors.toMap(Field::nameIgnoreCase, f -> f, (a, b) -> a));

    for (FieldMapping fieldMapping : fieldMappings) {
        String targetName = fieldMapping.getTargetName();
        if (StringUtil.isBlank(targetName)) continue;

        Field actualField = targetFieldMap.get(targetName.toLowerCase());

        if (actualField == null) {
            // MAPPING_ONLY: mapping 配置了但目标表不存在
            FieldDiffItem item = new FieldDiffItem();
            item.setFieldName(targetName);
            item.setTargetType(null);        // 目标表无此字段，无类型信息
            item.setTargetLength(null);      // 目标表无此字段，无长度信息
            item.setDiffType("MAPPING_ONLY");
            item.setDescription("mapping 已配置但目标表不存在");
            mappingOnlyFields.add(item);
        }
    }
}
```

> **注意**：targetType 和 targetLength 在旧代码中来自 FieldMapping 中缓存的 Field（可能已过时且不可靠）。新版本设为 null，因为 MAPPING_ONLY 的含义就是"目标表没有此字段"，所以类型/长度信息本来就没有意义。

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 13.1 | MAPPING_ONLY 差异检测 | 正确识别 mapping 中有但目标表无的字段 |
| 13.2 | 前端显示 MAPPING_ONLY | targetType/targetLength 显示为"不存在" |

---

## 改动点 #14 — TableGroupServiceImpl.removeFieldMappingByTargetName()

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/TableGroupServiceImpl.java`
**行号**: ~L1231-L1244

### 旧代码

```java
private void removeFieldMappingByTargetName(TableGroup tableGroup, String targetFieldName) {
    java.util.Iterator<FieldMapping> iterator = fieldMappings.iterator();
    while (iterator.hasNext()) {
        FieldMapping fieldMapping = iterator.next();
        if (fieldMapping != null && fieldMapping.getTarget() != null          // ❌
                && fieldMapping.getTarget().getName() != null                  // ❌
                && fieldMapping.getTarget().matchesName(targetFieldName)) {    // ❌
            iterator.remove();
        }
    }
}
```

### 新代码

```java
private void removeFieldMappingByTargetName(TableGroup tableGroup, String targetFieldName) {
    java.util.Iterator<FieldMapping> iterator = fieldMappings.iterator();
    while (iterator.hasNext()) {
        FieldMapping fieldMapping = iterator.next();
        if (StringUtil.equalsIgnoreCase(fieldMapping.getTargetName(), targetFieldName)) {
            iterator.remove();
        }
    }
}
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 14.1 | 移除 MAPPING_ONLY 差异字段 | 正确移除 targetName 匹配的映射 |

---

## 改动点 #15 — TableGroupChecker.setFieldMapping() 存字符串

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/checker/impl/tablegroup/TableGroupChecker.java`
**行号**: ~L230-L280

### 旧代码

```java
public void setFieldMapping(TableGroup tableGroup, String json) {
    List<Map<String, Object>> mappings = JsonUtil.parseList(json);
    // ...
    for (...) {
        String sourceName = row.get("source") != null ? row.get("source").toString() : null;
        String targetName = row.get("target") != null ? row.get("target").toString() : null;
        Field s = tableGroup.getSourceTable().findColumnByName(sourceName);
        Field t = tableGroup.getTargetTable().findColumnByName(targetName);
        // ... 类型转换构建 t ...
        list.add(new FieldMapping(s, t));       // ❌ 存 Field 对象
    }
    tableGroup.setFieldMapping(list);
}
```

### 新代码

```java
public void setFieldMapping(TableGroup tableGroup, String json) {
    List<Map<String, Object>> mappings = JsonUtil.parseList(json);
    if (null == mappings) {
        throw new BizException("映射关系不能为空");
    }

    List<FieldMapping> list = new ArrayList<>();
    for (Map<String, Object> row : mappings) {
        String sourceName = row.get("source") != null ? row.get("source").toString() : null;
        String targetName = row.get("target") != null ? row.get("target").toString() : null;

        if (StringUtil.isBlank(sourceName) && StringUtil.isBlank(targetName)) {
            continue;
        }

        // 获取 target Field 元数据（类型转换逻辑保留，但只用于验证，不再存入 FieldMapping）
        Field sourceField = tableGroup.getSourceTable().findColumnByName(sourceName);
        Field targetField = null;
        if (StringUtil.isNotBlank(targetName)) {
            targetField = tableGroup.getTargetTable().findColumnByName(targetName);
            if (targetField == null && sourceField != null) {
                // target 字段不存在于目标表，需要从源字段类型转换构建
                Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
                ConnectorConfig scc = getConnectorConfig(mapping.getSourceConnectorId());
                ConnectorConfig tcc = getConnectorConfig(mapping.getTargetConnectorId());
                ConnectorService<?, ?> sourceConnector = connectorFactory.getConnectorService(scc.getConnectorType());
                ConnectorService<?, ?> targetConnector = connectorFactory.getConnectorService(tcc.getConnectorType());
                SchemaResolver sourceSchemaResolver = sourceConnector.getSchemaResolver();
                SchemaResolver targetSchemaResolver = targetConnector.getSchemaResolver();
                if (sourceSchemaResolver != null) {
                    Field standardField = sourceSchemaResolver.toStandardType(sourceField);
                    if (targetSchemaResolver != null) {
                        targetField = targetSchemaResolver.fromStandardType(standardField);
                        targetField.setName(targetName);
                    } else {
                        targetField = standardField;
                        targetField.setName(targetName);
                    }
                }
            }
            if (targetField != null) {
                targetField.setPk(Boolean.TRUE.equals(row.get("pk")));
            }
        }

        list.add(FieldMapping.fromFields(sourceField, targetField));
    }
    tableGroup.setFieldMapping(list);
}
```

> **关键变更**：类型转换逻辑保留（用于验证 target 字段是否存在），但 `FieldMapping` 只存字段名。`findColumnByName` 查找 target 字段，如果不存在（如自定义字段），通过 SchemaResolver 转换构建。

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 15.1 | 前端提交 `{"source":"id","target":"id"}` | 正确解析为 FieldMapping |
| 15.2 | target 字段不存在（自定义字段） | 通过 SchemaResolver 转换构建 |
| 15.3 | 主键标记 pk=true | 正确设置 targetField.pk |

---

## 改动点 #16 — TableGroupChecker.buildFieldMappingFromSourceTable() 存字符串

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/checker/impl/tablegroup/TableGroupChecker.java`
**行号**: ~L312-L370

### 旧代码

```java
public void buildFieldMappingFromSourceTable(TableGroup tableGroup) {
    List<Field> sCol = tableGroup.getSourceTable().getColumn();
    // ...
    for (Field sourceField : sCol) {
        Field targetField = null;
        if (isSameType) {
            targetField = sourceField;
        } else if (sourceSchemaResolver != null && targetSchemaResolver != null) {
            Field standardField = sourceSchemaResolver.toStandardType(sourceField);
            targetField = targetSchemaResolver.fromStandardType(standardField);
            targetField.setPk(sourceField.isPk());
        } else {
            targetField = sourceField;
        }
        targetColumns.add(targetField);
        fieldMappingList.add(new FieldMapping(sourceField, targetField));   // ❌ 存 Field
    }
    tableGroup.getTargetTable().setColumn(targetColumns);
    tableGroup.setFieldMapping(fieldMappingList);
}
```

### 新代码

```java
public void buildFieldMappingFromSourceTable(TableGroup tableGroup) {
    List<Field> sCol = tableGroup.getSourceTable().getColumn();
    if (CollectionUtils.isEmpty(sCol)) {
        tableGroup.setFieldMapping(new ArrayList<>());
        return;
    }

    Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
    ConnectorConfig sourceConnectorConfig = getConnectorConfig(mapping.getSourceConnectorId());
    ConnectorConfig targetConnectorConfig = getConnectorConfig(mapping.getTargetConnectorId());
    String sourceConnectorType = sourceConnectorConfig.getConnectorType();
    String targetConnectorType = targetConnectorConfig.getConnectorType();
    boolean isSameType = sourceConnectorType != null && sourceConnectorType.equals(targetConnectorType);

    ConnectorService<?, ?> sourceConnectorService = connectorFactory.getConnectorService(sourceConnectorType);
    ConnectorService<?, ?> targetConnectorService = connectorFactory.getConnectorService(targetConnectorType);
    SchemaResolver sourceSchemaResolver = sourceConnectorService.getSchemaResolver();
    SchemaResolver targetSchemaResolver = targetConnectorService.getSchemaResolver();

    ArrayList<Field> targetColumns = new ArrayList<>();
    List<FieldMapping> fieldMappingList = new ArrayList<>();
    for (Field sourceField : sCol) {
        Field targetField = null;

        if (isSameType) {
            targetField = sourceField;
        } else if (sourceSchemaResolver != null && targetSchemaResolver != null) {
            Field standardField = sourceSchemaResolver.toStandardType(sourceField);
            targetField = targetSchemaResolver.fromStandardType(standardField);
            targetField.setPk(sourceField.isPk());
        } else {
            targetField = sourceField;
        }

        targetColumns.add(targetField);
        fieldMappingList.add(FieldMapping.fromFields(sourceField, targetField));  // ✅ 只存字段名
    }
    tableGroup.getTargetTable().setColumn(targetColumns);
    tableGroup.setFieldMapping(fieldMappingList);
}
```

> **关键变更**：类型转换逻辑完全保留（targetColumns 需要完整 Field 对象），只有 `FieldMapping` 存储简化为 `fromFields()` 提取字段名。

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 16.1 | 新建表映射（自动构建） | fieldMapping 包含所有源字段 |
| 16.2 | 同类型数据库 | targetField = sourceField（不转换） |
| 16.3 | 异类型数据库 | targetField 通过 SchemaResolver 转换 |

---

## 改动点 #17 — 集成测试断言适配

**文件**:
- `dbsyncer-web/src/test/java/org/dbsyncer/web/integration/DDLMysqlIntegrationTest.java` (多处)
- `dbsyncer-web/src/test/java/org/dbsyncer/web/integration/BaseDDLIntegrationTest.java`
- `dbsyncer-parser/src/test/java/org/dbsyncer/parser/integration/CustomFieldIntegrationTest.java`

### 旧断言

```java
.anyMatch(fm -> fm.getSource() != null && "age".equals(fm.getSource().getName()) &&
        fm.getTarget() != null && "age".equals(fm.getTarget().getName()));
```

### 新断言

```java
.anyMatch(fm -> "age".equalsIgnoreCase(fm.getSourceName()) &&
        "age".equalsIgnoreCase(fm.getTargetName()));
```

### 验收标准

| # | 测试 | 预期 |
|---|------|------|
| 17.1 | 所有集成测试编译通过 | 无编译错误 |
| 17.2 | DDL 同步集成测试 | 通过 |
| 17.3 | DML 同步集成测试 | 通过 |
| 17.4 | 自定义字段集成测试 | 通过 |

---

## 兼容性保障 — JSON 格式

### 前端 → 后端（写入时）

前端 `editTableGroup.js` 提交的格式不变：

```javascript
fieldMapping: [{"source": "id", "target": "id"}, ...]
```

后端 `TableGroupChecker.setFieldMapping()` 解析后存为：

```json
{"sourceName": "id", "targetName": "id"}
```

### 后端 → 前端（读取时）

`FieldMapping` 序列化输出（`@JsonProperty("source")`）：

```json
{"source": "id", "target": "id"}
```

前端无需改动 ✅

### 旧持久化数据兼容

已序列化的 JSON 格式（旧）：

```json
{"source":{"name":"id","typeName":"INT","type":4,...},"target":{"name":"id",...}}
```

通过 `@JsonSetter("source") void setSourceObject(Field field)` 自动提取 name 字段 ✅

---

## 验收总清单

### 编译检查

```
# 全量编译
mvn clean compile -DskipTests
```

| 模块 | 状态 |
|------|------|
| dbsyncer-common | ☐ |
| dbsyncer-sdk | ☐ |
| dbsyncer-connector | ☐ |
| dbsyncer-storage | ☐ |
| dbsyncer-parser | ☐ |
| dbsyncer-manager | ☐ |
| dbsyncer-plugin | ☐ |
| dbsyncer-biz | ☐ |
| dbsyncer-web | ☐ |

### 单元测试

```
mvn test -pl dbsyncer-parser,dbsyncer-biz
```

| 测试类 | 状态 |
|--------|------|
| Picker 相关 | ☐ |
| DDLParserImpl 相关 | ☐ |
| TableGroupChecker 相关 | ☐ |
| FieldComparisonUtil 相关 | ☐ |

### 集成测试

```
mvn test -pl dbsyncer-web -Dtest=*IntegrationTest
```

| 测试类 | 状态 |
|--------|------|
| DDLMysqlIntegrationTest | ☐ |
| DMLMysqlIntegrationTest | ☐ |
| DDLSqlServerCTIntegrationTest | ☐ |
| CustomFieldIntegrationTest | ☐ |
| MySQLToSqlServerDMLIntegrationTest | ☐ |
| SqlServerCTBigTransactionIntegrationTest | ☐ |

### 兼容性测试

| 场景 | 状态 |
|------|------|
| 加载旧格式 JSON 配置 | ☐ |
| 编辑旧配置后保存 | ☐ |
| 新配置序列化输出 | ☐ |
| 前端读取展示正常 | ☐ |

---

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-05-27 | v1.0 | 初始版本，17 个改动点详细标注 | 凌曦 |
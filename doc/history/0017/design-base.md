# ADR-0017 补充：primaryKeys 数据流断裂修复

## 状态：Accepted

**日期**：2026-06-26  
**决策者**：用户  
**参与者**：AI  
**关联 ADR**：[0017-MetaInfo继承Table统一主键传递链](../adr/0017-MetaInfo继承Table统一主键传递链.md)

---

## 背景

在 ADR-0017 实施过程中发现 primaryKeys 数据流存在断裂：
1. `CommandConfig.primaryKeys` 字段从未被使用
2. `TableGroup.initCommand()` 中的设置代码是冗余的
3. `ParserComponentImpl` 错误地从 `sourceTable.getColumn().filter(isPk())` 获取主键
4. `MetaBufferActuator` 中的 `IncrementPluginContext` 未设置 primaryKeys
5. `AbstractDatabaseConnector` 中有 pluginExtInfo 兼容回退逻辑

---

## 决策

### 1. 移除 CommandConfig.primaryKeys 字段
**原因**：实际从未被连接器读取，连接器通过 PluginContext 获取。

**文件**：`CommandConfig.java`
- 移除 `primaryKeys` 字段
- 移除 `getPrimaryKeys()` / `setPrimaryKeys()` 方法

### 2. 移除 TableGroup 中的冗余代码
**原因**：设置到 CommandConfig 但从未被读取。

**文件**：`TableGroup.java`
- 移除 `sourceConfig.setPrimaryKeys(primaryKeys)` 调用

### 3. ParserComponentImpl 修正
**原因**：从 sourceTable.getColumn().filter(isPk()) 获取主键顺序不正确。

**文件**：`ParserComponentImpl.java`
```java
// 修正前
List<String> primaryKeys = sources.stream()
    .filter(f -> f.isPk())
    .map(f -> f.getName())
    .collect(Collectors.toList());

// 修正后
List<String> primaryKeys = group.getLabelTablePrimaryKeys();
context.setPrimaryKeys(primaryKeys);
```

### 4. MetaBufferActuator 修正
**原因**：IncrementPluginContext 未设置 primaryKeys。

**文件**：`MetaBufferActuator.java`
```java
// 两处 IncrementPluginContext 创建都添加
context.setPrimaryKeys(tableGroup.getLabelTablePrimaryKeys());
```

### 5. AbstractDatabaseConnector 清理
**原因**：pluginExtInfo 兼容回退逻辑应该移除。

**文件**：`AbstractDatabaseConnector.java`
```java
// 修正前：有兼容回退逻辑
List<String> primaryKeys = context.getPrimaryKeys();
if (primaryKeys == null || primaryKeys.isEmpty()) {
    // 兼容：尝试从 pluginExtInfo 获取
    // ...
}

// 修正后：直接抛出异常
List<String> primaryKeys = context.getPrimaryKeys();
if (primaryKeys == null || primaryKeys.isEmpty()) {
    throw new SdkException("Primary keys cannot be empty for UPDATE operation");
}
```

---

## 最终数据流

```
配置层：TableGroup.targetTablePK (String)
    ↓
解析层：TableGroup.getLabelTablePrimaryKeys() -> List<String>
    ↓
传递层：
  全量：ParserComponentImpl → FullPluginContext.setPrimaryKeys()
  增量：MetaBufferActuator → IncrementPluginContext.setPrimaryKeys()
    ↓
使用层：AbstractDatabaseConnector.update/delete/upsert → context.getPrimaryKeys()
```

---

## 影响

1. **编译**：通过
2. **测试**：通过（除了环境依赖的测试）
3. **代码**：移除冗余字段，数据流清晰
4. **验证**：primaryKeys 现在通过 PluginContext 从 TableGroup 传递到连接器

---

## 状态：✅ 完成

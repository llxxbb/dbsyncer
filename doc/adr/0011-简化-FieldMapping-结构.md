# ADR-0011: 简化 FieldMapping 结构

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Proposed |
| 决策者 | @老李 |
| 参与者 | 凌曦 |
| 创建日期 | 2026-05-27 |
| 最后更新 | 2026-05-27 |
| 关联文档 | [过程文档](../history/0011/) |

## 背景

`FieldMapping` 当前持有两个完整的 `Field` 对象（`source` 和 `target`），每个 `Field` 包含 ~15 个属性（name, typeName, type, pk, labelName, columnSize, ratio, srid, isSizeFixed, nullable, comment, autoincrement, charset, enumValues）。

**问题：**
1. **重复存储** — 同样的字段元数据已经存在于 `sourceTable.getColumn()` 和 `targetTable.getColumn()` 中，序列化 JSON 时又完整存储了一份
2. **数据不一致风险** — 运行时 `Table.column` 被刷新后（如 DDL 变更、refreshFields），FieldMapping 中缓存的 Field 对象可能过期，导致类型/主键等信息与表定义不一致

**现状证据：**
- `Picker.exchange()` 只用字段名做 `source.get(sField.getName())` → `target.put(tFieldName, v)`，不依赖 Field 的其他属性
- `TableGroupChecker.setFieldMapping()` 从前端接收的 JSON 本就是 `{"source":"name","target":"name"}` 格式，Field 对象是后端查表补出来的
- `TableGroup.initCommand()` 重新从 FieldMapping 提取 Field 构建 sTable/tTable 的 column，如果 Field 过期则构建出错

## 决策

**将 `FieldMapping` 的存储结构从 `Field` 对象简化为字段名字符串。**

### 新结构

```java
public class FieldMapping {
    private String sourceName;   // 源字段名
    private String targetName;   // 目标字段名
}
```

### 核心原则

- **字段名是唯一标识** — FieldMapping 只存"谁映射到谁"的语义关系
- **元数据从 Table 获取** — 运行时通过 `sourceTable.findColumnByName(sourceName)` 获取完整 Field 信息
- **Table.column 是唯一的元数据源** — 消除重复存储，保证一致性

### 兼容策略

1. **JSON 反序列化兼容** — 使用 Jackson `@JsonCreator` 自定义反序列化：
   - 旧格式 `{"source":{"name":"id",...},"target":{"name":"id",...}}` → 提取 name 填充 sourceName/targetName
   - 新格式 `{"sourceName":"id","targetName":"id"}` → 直接读取
2. **序列化新格式** — `@JsonProperty` 序列化为 `source`/`target` 字符串，前端 JSON 格式不变
3. **无需数据迁移** — 已有配置在下次读取时自动转换为新格式

### 影响面

| 模块 | 变更内容 |
|------|----------|
| `FieldMapping` | 移除 `Field source/target`，新增 `String sourceName/targetName` |
| `Picker` | 构造器改为从 Table 查找 Field，`exchange()` 逻辑不变 |
| `TableGroupChecker` | `setFieldMapping()` 直接存字符串，`buildFieldMappingFromSourceTable()` 同理 |
| `TableGroup` | `containsField()`, `initCommand()` 适配新结构 |
| `FieldComparisonUtil` | 比较逻辑适配 |
| `TableGroupServiceImpl` | `removeFieldMappingByTargetName()` 等适配 |

## 影响

- **存储缩减** — 每条映射关系从 ~500 字节 JSON 降至 ~40 字节（减少约 90%）
- **消除不一致** — 字段元数据以 Table.column 为唯一源，刷新表字段后自动生效
- **代码简化** — Picker 构造器通过 `findColumnByName` 查找，逻辑更清晰
- **兼容已有任务** — 旧配置自动适配，无需手动迁移

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-05-27 | v1.0 | 初始版本 | 凌曦 |

# ADR-0008: 字段名/表名大小写不敏感的业务对象封装

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Proposed |
| 决策者 | 老李 |
| 参与者 | 凌曦 |
| 创建日期 | 2026-05-13 |
| 最后更新 | 2026-05-13 |
| 关联文档 | [过程文档](../history/0008/) |

## 背景

大小写分析发现了 168 处大小写相关代码，其中 6 处 P0 级风险和 5 处 P1 级风险。核心问题是：字段名、表名、主键在匹配/查找时，有的地方区分大小写，有的不区分，导致当源数据库（如 MySQL on Linux）与目标数据库（如 PostgreSQL）之间存在大小写差异时，出现 DDL 映射更新失败、主键标记丢失、自定义字段重复添加等问题。

**业务事实**：数据库标识符的大小写是基础设施细节，不是业务概念。用户配置"字段 A 映射到字段 B"时，不关心底层存的是 `siteId`、`siteID` 还是 `SITEID`——对业务来说，这就是同一个字段。

## 决策

在现有业务对象上增加大小写不敏感的比较方法，统一放在业务对象层，而不是技术层：

### 1. Field 对象

```java
// Field.java - 增加方法
public String nameKey() {
    return getName() == null ? null : getName().toLowerCase();
}
```

### 2. Table 对象

```java
// Table.java - 增加方法
public String nameKey() {
    return getName() == null ? null : getName().toLowerCase();
}
```

### 3. TableGroup 对象（主键操作封装）

```java
// TableGroup.java - 增加主键操作方法
public void addPrimaryKeyIfAbsent(String name) {
    // 内部处理去重（大小写不敏感）
}
public boolean containsPrimaryKey(String name) {
    // 内部处理大小写不敏感判断
}
public boolean primaryKeyChangedSince(List<String> oldPKs) {
    // 内部处理大小写不敏感比较
}
```

### 使用原则

| 场景 | 使用 |
|------|------|
| 匹配/查找 | `nameKey()` 或业务方法 |
| 展示/DDL 生成 | `getName()`（保持原始大小写） |

## 影响

- **技术影响**：`Field`、`Table`、`TableGroup` 各增加少量方法。外围散落的 `equalsIgnoreCase` / `toLowerCase` / `indexOf` 逐步替换为统一调用。不引入新类、新层、新依赖。
- **团队影响**：无。现有开发模式不变，只是匹配时调用 `nameKey()` 而非直接 `equals()`。
- **时间影响**：分阶段实施（P0 → P1），每阶段 1-2 小时。

## 替代方案

| 方案 | 优点 | 缺点 | 不选原因 |
|------|------|------|----------|
| `CaseInsensitiveNameUtil` 工具类 | 实现简单 | 散落各处，业务语义不清晰，后续维护成本高 | 用户明确拒绝增加技术复杂度 |
| 逐点修改 `equalsIgnoreCase` | 改动最小 | 逻辑分散，遗漏风险高，无法形成闭环 | 不是根本解决，无法简化外围逻辑 |
| 引入 `TreeSet(CASE_INSENSITIVE_ORDER)` | 标准库支持 | 侵入性强，需要改所有集合类型 | 过度设计，增加理解成本 |

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-05-13 | v1.0 | 初始版本 | 凌曦 |

## 过程文档索引

- [讨论记录](../history/0008/design-base.md)
- [大小写分析报告](/docs/大小写处理分析报告.md)

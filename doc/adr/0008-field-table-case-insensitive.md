# ADR-0008: 字段名/表名大小写不敏感的业务对象封装

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Proposed |
| 决策者 | 老李 |
| 创建日期 | 2026-05-13 |
| 最后更新 | 2026-05-13 |
| 关联文档 | [过程文档](../history/0008/) |

## 背景

大小写分析发现 168 处大小写相关代码，6 处 P0 级风险。字段名/表名/主键匹配时有的区分大小写、有的不区分，导致 DDL 映射更新失败、主键标记丢失、自定义字段重复添加等问题。

**业务事实**：数据库标识符的大小写是基础设施细节，不是业务概念。用户配置"字段 A 映射到字段 B"时，`siteId`、`siteID`、`SITEID` 对业务来说就是同一个字段。

## 决策

现有业务对象增加大小写不敏感方法，统一在业务对象层处理：

### 1. Field

```java
public String nameIgnoreCase() {
    return getName() == null ? null : getName().toLowerCase();
}
```

### 2. Table

```java
public String nameIgnoreCase() {
    return getName() == null ? null : getName().toLowerCase();
}
```

### 3. TableGroup（主键封装）

```java
public void addPrimaryKeyIfAbsent(String name);
public boolean containsPrimaryKey(String name);
public boolean primaryKeyChangedSince(List<String> oldPKs);
```

### 使用原则

| 场景 | 方法 |
|------|------|
| 匹配/查找 | `nameIgnoreCase()` 或 TableGroup 业务方法 |
| 展示/DDL 生成 | `getName()`（保持原始大小写） |

## 实施要求

| 阶段 | 范围 | 交付物 |
|------|------|--------|
| P0 | `Field.nameIgnoreCase()` + `Table.nameIgnoreCase()` + 6 处高风险修复 | 代码 + 测试验证 |
| P1 | `TableGroup` 主键封装 + 5 处中风险修复 | 代码 + 测试验证 |

**不选方案**：工具类（增加技术复杂度）、逐点修改（无法闭环）。

## 影响

- `Field`、`Table`、`TableGroup` 各增加少量方法
- 外围散落的 `equalsIgnoreCase` / `toLowerCase` / `indexOf` 逐步替换为统一调用
- 不引入新类、新层、新依赖

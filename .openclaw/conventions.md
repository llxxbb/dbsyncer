# dbsyncer 项目约定

## 大小写比较规范

**适用阶段**：需求分析、架构设计、代码实现、测试用例设计（全生命周期）

**规则**：涉及数据库对象标识符（字段名、表名、主键名）的比较/匹配操作，**必须使用封装方法**，禁止自行实现。

### 禁止做法

```java
// ❌ 直接使用 .toLowerCase() / .toUpperCase()
if (fieldName.toLowerCase().equals(targetName.toLowerCase()))

// ❌ 直接使用 .equalsIgnoreCase() 原始方法
if (fieldName.equalsIgnoreCase(targetName))
```

### 必须使用的封装方法

| 场景 | 必须使用的方法 | 说明 |
|------|--------------|------|
| Field 对象比较字段名 | `Field.matchesName(String)` | ADR-0008 引入，空值安全，Locale 无关 |
| 普通字符串比较 | `StringUtil.equalsIgnoreCase(CharSequence, CharSequence)` | Apache Commons 封装，空值安全 |
| 主键字符串比较 | `StringUtil.equalsIgnoreCase()` | `targetTablePK` 等字符串字段 |
| 构建 Map 索引 | `buildFieldMap()` | FieldComparisonUtil 提供，统一小写 Key |

### 为什么

1. **空值安全**：`Field.matchesName()` 内部判空，避免 NPE
2. **Locale 安全**：某些语言环境下 `I.toLowerCase()` 不等于 `"i"`
3. **策略统一**：一处变更，全局生效，避免散落在多处难以维护
4. **可审查性**：代码搜索 `matchesName` 即可定位所有比较逻辑

### 适用范围

- 字段名比较（字段映射、差异检测、同步规则）
- 表名比较（表组配置、元数据查询）
- 主键名比较（主键变更检测、排序规则）
- 任何与数据库元数据相关的标识符匹配

---

## 最后更新
2026-05-14 | ADR-0008/0009 相关规范落地
